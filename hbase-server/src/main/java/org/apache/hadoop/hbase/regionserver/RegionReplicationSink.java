/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.regionserver;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.client.AsyncClusterConnection;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.RegionReplicaUtil;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.ipc.ServerCall;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.FutureUtils;
import org.apache.hadoop.hbase.wal.WAL;
import org.apache.hadoop.hbase.wal.WALEdit;
import org.apache.hadoop.hbase.wal.WALKeyImpl;
import org.apache.yetus.audience.InterfaceAudience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hbase.thirdparty.com.google.common.base.Preconditions;

import org.apache.hadoop.hbase.shaded.protobuf.generated.WALProtos.FlushDescriptor;

/**
 * The class for replicating WAL edits to secondary replicas, one instance per region.
 */
@InterfaceAudience.Private
public class RegionReplicationSink {

  private static final Logger LOG = LoggerFactory.getLogger(RegionReplicationSink.class);

  public static final String MAX_PENDING_SIZE = "hbase.region.read-replica.sink.max-pending-size";

  public static final long MAX_PENDING_SIZE_DEFAULT = 10L * 1024 * 1024;

  public static final String RETRIES_NUMBER = "hbase.region.read-replica.sink.retries.number";

  public static final int RETRIES_NUMBER_DEFAULT = 3;

  public static final String RPC_TIMEOUT_MS = "hbase.region.read-replica.sink.rpc.timeout.ms";

  public static final long RPC_TIMEOUT_MS_DEFAULT = 200;

  public static final String OPERATION_TIMEOUT_MS =
    "hbase.region.read-replica.sink.operation.timeout.ms";

  public static final long OPERATION_TIMEOUT_MS_DEFAULT = 1000;

  private static final class SinkEntry {

    final WALKeyImpl key;

    final WALEdit edit;

    final ServerCall<?> rpcCall;

    SinkEntry(WALKeyImpl key, WALEdit edit, ServerCall<?> rpcCall) {
      this.key = key;
      this.edit = edit;
      this.rpcCall = rpcCall;
      if (rpcCall != null) {
        // increase the reference count to avoid the rpc framework free the memory before we
        // actually sending them out.
        rpcCall.retainByWAL();
      }
    }

    /**
     * Should be called regardless of the result of the replicating operation. Unless you still want
     * to reuse this entry, otherwise you must call this method to release the possible off heap
     * memories.
     */
    void replicated() {
      if (rpcCall != null) {
        rpcCall.releaseByWAL();
      }
    }
  }

  private final RegionInfo primary;

  private final TableDescriptor tableDesc;

  private final Runnable flushRequester;

  private final AsyncClusterConnection conn;

  // used to track the replicas which we failed to replicate edits to them
  // will be cleared after we get a flush edit.
  private final Set<Integer> failedReplicas = new HashSet<>();

  private final Queue<SinkEntry> entries = new ArrayDeque<>();

  private final int retries;

  private final long rpcTimeoutNs;

  private final long operationTimeoutNs;

  private boolean sending;

  private boolean stopping;

  private boolean stopped;

  RegionReplicationSink(Configuration conf, RegionInfo primary, TableDescriptor td,
    Runnable flushRequester, AsyncClusterConnection conn) {
    Preconditions.checkArgument(RegionReplicaUtil.isDefaultReplica(primary), "%s is not primary",
      primary);
    Preconditions.checkArgument(td.getRegionReplication() > 1,
      "region replication should be greater than 1 but got %s", td.getRegionReplication());
    this.primary = primary;
    this.tableDesc = td;
    this.flushRequester = flushRequester;
    this.conn = conn;
    this.retries = conf.getInt(RETRIES_NUMBER, RETRIES_NUMBER_DEFAULT);
    this.rpcTimeoutNs =
      TimeUnit.MILLISECONDS.toNanos(conf.getLong(RPC_TIMEOUT_MS, RPC_TIMEOUT_MS_DEFAULT));
    this.operationTimeoutNs = TimeUnit.MILLISECONDS
      .toNanos(conf.getLong(OPERATION_TIMEOUT_MS, OPERATION_TIMEOUT_MS_DEFAULT));
  }

  private void onComplete(List<SinkEntry> sent,
    Map<Integer, MutableObject<Throwable>> replica2Error) {
    sent.forEach(SinkEntry::replicated);
    Set<Integer> failed = new HashSet<>();
    for (Map.Entry<Integer, MutableObject<Throwable>> entry : replica2Error.entrySet()) {
      Integer replicaId = entry.getKey();
      Throwable error = entry.getValue().getValue();
      if (error != null) {
        LOG.warn("Failed to replicate to secondary replica {} for {}, stop replicating" +
          " for a while and trigger a flush", replicaId, primary, error);
        failed.add(replicaId);
      }
    }
    synchronized (entries) {
      if (!failed.isEmpty()) {
        failedReplicas.addAll(failed);
        flushRequester.run();
      }
      sending = false;
      if (stopping) {
        stopped = true;
        entries.notifyAll();
        return;
      }
      if (!entries.isEmpty()) {
        send();
      }
    }
  }

  private void send() {
    List<SinkEntry> toSend = new ArrayList<>();
    for (SinkEntry entry;;) {
      entry = entries.poll();
      if (entry == null) {
        break;
      }
      toSend.add(entry);
    }
    int toSendReplicaCount = tableDesc.getRegionReplication() - 1 - failedReplicas.size();
    if (toSendReplicaCount <= 0) {
      return;
    }
    sending = true;
    List<WAL.Entry> walEntries =
      toSend.stream().map(e -> new WAL.Entry(e.key, e.edit)).collect(Collectors.toList());
    AtomicInteger remaining = new AtomicInteger(toSendReplicaCount);
    Map<Integer, MutableObject<Throwable>> replica2Error = new HashMap<>();
    for (int replicaId = 1; replicaId < tableDesc.getRegionReplication(); replicaId++) {
      MutableObject<Throwable> error = new MutableObject<>();
      replica2Error.put(replicaId, error);
      RegionInfo replica = RegionReplicaUtil.getRegionInfoForReplica(primary, replicaId);
      FutureUtils.addListener(
        conn.replicate(replica, walEntries, retries, rpcTimeoutNs, operationTimeoutNs), (r, e) -> {
          error.setValue(e);
          if (remaining.decrementAndGet() == 0) {
            onComplete(toSend, replica2Error);
          }
        });
    }
  }

  private boolean flushAllStores(FlushDescriptor flushDesc) {
    Set<byte[]> storesFlushed =
      flushDesc.getStoreFlushesList().stream().map(sfd -> sfd.getFamilyName().toByteArray())
        .collect(Collectors.toCollection(() -> new TreeSet<>(Bytes.BYTES_COMPARATOR)));
    if (storesFlushed.size() != tableDesc.getColumnFamilyCount()) {
      return false;
    }
    return storesFlushed.containsAll(tableDesc.getColumnFamilyNames());
  }

  /**
   * Add this edit to replication queue.
   * <p/>
   * The {@code rpcCall} is for retaining the cells if the edit is built within an rpc call and the
   * rpc call has cell scanner, which is off heap.
   */
  public void add(WALKeyImpl key, WALEdit edit, ServerCall<?> rpcCall) {
    if (!tableDesc.hasRegionMemStoreReplication() && !edit.isMetaEdit()) {
      // only replicate meta edit if region memstore replication is not enabled
      return;
    }
    synchronized (entries) {
      if (stopping) {
        return;
      }
      if (edit.isMetaEdit()) {
        // check whether we flushed all stores, which means we could drop all the previous edits,
        // and also, recover from the previous failure of some replicas
        for (Cell metaCell : edit.getCells()) {
          if (CellUtil.matchingFamily(metaCell, WALEdit.METAFAMILY)) {
            FlushDescriptor flushDesc;
            try {
              flushDesc = WALEdit.getFlushDescriptor(metaCell);
            } catch (IOException e) {
              LOG.warn("Failed to parse FlushDescriptor from {}", metaCell);
              continue;
            }
            if (flushDesc != null && flushAllStores(flushDesc)) {
              LOG.debug("Got a flush all request, clear failed replicas {} and {} pending" +
                " replication entries", failedReplicas, entries.size());
              entries.clear();
              failedReplicas.clear();
            }
          }
        }
      }
      // TODO: limit the total cached entries here, and we should have a global limitation, not for
      // only this region.
      entries.add(new SinkEntry(key, edit, rpcCall));
      if (!sending) {
        send();
      }
    }
  }

  /**
   * Stop the replication sink.
   * <p/>
   * Usually this should only be called when you want to close a region.
   */
  void stop() {
    synchronized (entries) {
      stopping = true;
      if (!sending) {
        stopped = true;
        entries.notifyAll();
      }
    }
  }

  /**
   * Make sure that we have finished all the replicating requests.
   * <p/>
   * After returning, we can make sure there will be no new replicating requests to secondary
   * replicas.
   * <p/>
   * This is used to keep the replicating order the same with the WAL edit order when writing.
   */
  void waitUntilStopped() throws InterruptedException {
    synchronized (entries) {
      while (!stopped) {
        entries.wait();
      }
    }
  }
}
