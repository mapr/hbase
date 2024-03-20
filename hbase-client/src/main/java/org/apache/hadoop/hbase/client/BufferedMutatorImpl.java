/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.client;

import com.google.common.annotations.VisibleForTesting;
import java.io.Closeable;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.classification.InterfaceAudience;
import org.apache.hadoop.hbase.classification.InterfaceStability;
import org.apache.hadoop.hbase.client.mapr.AbstractHTable;
import org.apache.hadoop.hbase.client.mapr.AbstractMapRClusterConnection;
import org.apache.hadoop.hbase.client.mapr.BaseTableMappingRules;
import org.apache.hadoop.hbase.client.mapr.TableMappingRulesFactory;
import org.apache.hadoop.hbase.ipc.RpcControllerFactory;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <p>
 * Used to communicate with a single HBase table similar to {@link HTable}
 * but meant for batched, potentially asynchronous puts. Obtain an instance from
 * a {@link Connection} and call {@link #close()} afterwards.
 * </p>
 *
 * <p>
 * While this can be used accross threads, great care should be used when doing so.
 * Errors are global to the buffered mutator and the Exceptions can be thrown on any
 * thread that causes the flush for requests.
 * </p>
 *
 * @see ConnectionFactory
 * @see Connection
 * @since 1.0.0
 */
@InterfaceAudience.Private
@InterfaceStability.Evolving
public class BufferedMutatorImpl implements BufferedMutator {

  private static final Log LOG = LogFactory.getLog(BufferedMutatorImpl.class);

  private final ExceptionListener listener;

  protected ClusterConnection connection; // non-final so can be overridden in test
  private final TableName tableName;

  // Band-aid with a cached maprTable handle, since connection.getTable will create
  // a new instance of the table every time. We should implement a mapr version BufferedMutator
  // in the com.mapr.fs.hbase, so that we can cache that maprBufferedMutator here.
  private AbstractHTable maprTable_ = null;

  private volatile Configuration conf;
  @VisibleForTesting
  final ConcurrentLinkedQueue<Mutation> writeAsyncBuffer = new ConcurrentLinkedQueue<Mutation>();
  @VisibleForTesting
  AtomicLong currentWriteBufferSize = new AtomicLong(0);

  /**
   * Count the size of {@link BufferedMutatorImpl#writeAsyncBuffer}.
   * The {@link ConcurrentLinkedQueue#size()} is NOT a constant-time operation.
   */
  @VisibleForTesting
  AtomicInteger undealtMutationCount = new AtomicInteger(0);
  private long writeBufferSize;
  private final int maxKeyValueSize;
  private boolean closed = false;
  private final boolean cleanupPoolOnClose_;
  private final ExecutorService pool;
  private int writeRpcTimeout; // needed to pass in through AsyncProcess constructor
  private int operationTimeout;

  @VisibleForTesting
  protected AsyncProcess ap = null; // non-final so can be overridden in test

  public BufferedMutatorImpl(ClusterConnection conn, RpcRetryingCallerFactory rpcCallerFactory,
                             RpcControllerFactory rpcFactory, BufferedMutatorParams params)
  {
    this(conn, rpcCallerFactory, rpcFactory, params, true /* cleanupPoolOnClose */);
  }

  public BufferedMutatorImpl(ClusterConnection conn, RpcRetryingCallerFactory rpcCallerFactory,
      RpcControllerFactory rpcFactory, BufferedMutatorParams params, boolean cleanupPoolOnClose) {
    if (conn == null || conn.isClosed()) {
      throw new IllegalArgumentException("Connection is null or closed.");
    }

    this.tableName = params.getTableName();
    if (tableName == null) {
      LOG.warn("BufferedMutator is constructed with tableName as null");
    }
    this.connection = conn;
    this.conf = connection.getConfiguration();
    if (this.conf == null) {
      LOG.warn("BufferedMutator is constructed with conf as null");
    }

    this.cleanupPoolOnClose_ = cleanupPoolOnClose;
    this.pool = params.getPool();
    this.listener = params.getListener();
    if (this.listener != null && this.pool == null) {
      throw new IllegalArgumentException("BufferedMutator for " + this.tableName + " has a listener, but does not have a pool");
    }

    ConnectionConfiguration connConf = new ConnectionConfiguration(conf);
    this.writeBufferSize = params.getWriteBufferSize() != BufferedMutatorParams.UNSET ?
        params.getWriteBufferSize() : connConf.getWriteBufferSize();
    this.maxKeyValueSize = params.getMaxKeyValueSize() != BufferedMutatorParams.UNSET ?
        params.getMaxKeyValueSize() : connConf.getMaxKeyValueSize();

    if (!BaseTableMappingRules.isInHBaseService()) {
      if (connection instanceof AbstractMapRClusterConnection) {
        maprTable_ = AbstractMapRClusterConnection.createAbstractMapRTable(
                connection.getConfiguration(), tableName, this, this.listener, this.pool);
        if (maprTable_ == null) {
          throw new IllegalArgumentException("Could not find table " + this.tableName + " through MapRClusterConnection.");
        }
      } else if (connection instanceof org.apache.hadoop.hbase.client.ConnectionManager.HConnectionImplementation) {
        BaseTableMappingRules tableMappingRule = null;
        try {
          tableMappingRule = TableMappingRulesFactory.create(connection.getConfiguration());
        } catch (IOException e) {
          throw new IllegalArgumentException("Could not get tableMappingRule for table " + this.tableName + " through HConnection. Reason:"
                  + e.getStackTrace());
        }
        if ((tableMappingRule != null) && tableMappingRule.isMapRTable(tableName)) {
          maprTable_ = HTable.createMapRTable(connection.getConfiguration(), tableName, this, this.listener, this.pool);
        }
        //maprTable_ can be null in this case.
      } else {
        LOG.warn("Unknown connection type!");
      }

    }

    this.writeRpcTimeout = connConf.getWriteRpcTimeout();
    this.operationTimeout = connConf.getOperationTimeout();

    if (maprTable_ == null) {
      // puts need to track errors globally due to how the APIs currently work.
      ap = new AsyncProcess(connection, conf, pool, rpcCallerFactory, true, rpcFactory, writeRpcTimeout);
    } else {
      maprTable_.setAutoFlush(false);
    }
  }

  @Override
  public TableName getName() {
    return tableName;
  }

  @Override
  public Configuration getConfiguration() {
    return conf;
  }

  @Override
  public void mutate(Mutation m) throws InterruptedIOException,
      RetriesExhaustedWithDetailsException {
    mutate(Arrays.asList(m));
  }

  @Override
  public void mutate(List<? extends Mutation> ms) throws InterruptedIOException,
      RetriesExhaustedWithDetailsException {

    if (closed) {
      throw new IllegalStateException("Cannot put when the BufferedMutator is closed.");
    }

    //TODO: add maprTable_.mutate() to com.mapr.fs.hbase
    if (isMapRTable()) {
      // Do not delete one by one, keep the mutations in their corresponding lists
      List<Put> puts = new ArrayList<>();
      List<Delete> deletes = new ArrayList<>();
      for (Mutation m : ms) {
        if (m instanceof Put) {
          puts.add((Put) m);
        }
        if (m instanceof Delete) {
          deletes.add((Delete) m);
        }
      }

      // Lists are ready, mutate as batch
      try {
        if (!puts.isEmpty()) {
          maprTable_.put(puts);
        }
        if (!deletes.isEmpty()) {
          maprTable_.delete(deletes);
        }
      } catch (IOException e) {
        throw new InterruptedIOException("Cannot mutate with this mapr table. Reason: " + e);
      }
      return;
    }

    long toAddSize = 0;
    int toAddCount = 0;
    for (Mutation m : ms) {
      if (m instanceof Put) {
        validatePut((Put) m);
      }
      toAddSize += m.heapSize();
      ++toAddCount;
    }

    // This behavior is highly non-intuitive... it does not protect us against
    // 94-incompatible behavior, which is a timing issue because hasError, the below code
    // and setter of hasError are not synchronized. Perhaps it should be removed.
    if (ap.hasError()) {
      currentWriteBufferSize.addAndGet(toAddSize);
      writeAsyncBuffer.addAll(ms);
      undealtMutationCount.addAndGet(toAddCount);
      backgroundFlushCommits(true);
    } else {
      currentWriteBufferSize.addAndGet(toAddSize);
      writeAsyncBuffer.addAll(ms);
      undealtMutationCount.addAndGet(toAddCount);
    }

    // Now try and queue what needs to be queued.
    while (undealtMutationCount.get() != 0
        && currentWriteBufferSize.get() > writeBufferSize) {
      backgroundFlushCommits(false);
    }
  }

  // validate for well-formedness
  public void validatePut(final Put put) throws IllegalArgumentException {
    HTable.validatePut(put, maxKeyValueSize);
  }

  @Override
  public synchronized void close() throws IOException {
    try {
      if (this.closed) {
        return;
      }
      // As we can have an operation in progress even if the buffer is empty, we call
      // backgroundFlushCommits at least one time.
      backgroundFlushCommits(true);
      if (isMapRTable()) {
        maprTable_.close();
        maprTable_ = null;
      }

      if (cleanupPoolOnClose_) {
        this.pool.shutdown();
        boolean terminated;
        int loopCnt = 0;
        do {
          // wait until the pool has terminated
          terminated = this.pool.awaitTermination(60, TimeUnit.SECONDS);
          loopCnt += 1;
          if (loopCnt >= 10) {
            LOG.warn("close() failed to terminate pool after 10 minutes. Abandoning pool.");
            break;
          }
        } while (!terminated);
      }
    } catch (InterruptedException e) {
      LOG.warn("waitForTermination interrupted");

    } finally {
      this.closed = true;
    }
  }

  @Override
  public synchronized void flush() throws InterruptedIOException,
      RetriesExhaustedWithDetailsException {
    // As we can have an operation in progress even if the buffer is empty, we call
    // backgroundFlushCommits at least one time.
    backgroundFlushCommits(true);
  }

  /**
   * Send the operations in the buffer to the servers. Does not wait for the server's answer. If
   * the is an error (max retried reach from a previous flush or bad operation), it tries to send
   * all operations in the buffer and sends an exception.
   *
   * @param synchronous - if true, sends all the writes and wait for all of them to finish before
   *        returning.
   */
  private void backgroundFlushCommits(boolean synchronous) throws
      InterruptedIOException,
      RetriesExhaustedWithDetailsException {
    if (isMapRTable()) {
      maprTable_.flushCommits();
      return;
    }
    if (!synchronous && writeAsyncBuffer.isEmpty()) {
      return;
    }

    if (!synchronous) {
      try (QueueRowAccess taker = createQueueRowAccess()){
        ap.submit(tableName, taker, true, null, false);
        if (ap.hasError()) {
          LOG.debug(tableName + ": One or more of the operations have failed -"
              + " waiting for all operation in progress to finish (successfully or not)");
        }
      }
    }
    if (synchronous || ap.hasError()) {
      while (true) {
        try (QueueRowAccess taker = createQueueRowAccess()){
          if (taker.isEmpty()) {
            break;
          }
          ap.submit(tableName, taker, true, null, false);
        }
      }

      RetriesExhaustedWithDetailsException error =
          ap.waitForAllPreviousOpsAndReset(null, tableName.getNameAsString());
      if (error != null) {
        if (listener == null) {
          throw error;
        } else {
          this.listener.onException(error, this);
        }
      }
    }
  }

  /**
   * This is used for legacy purposes in {@link HTable#setWriteBufferSize(long)} only. This ought
   * not be called for production uses.
   * @deprecated Going away when we drop public support for {@link HTableInterface}.
   */
  @Deprecated
  public void setWriteBufferSize(long writeBufferSize) throws RetriesExhaustedWithDetailsException,
      InterruptedIOException {
    if (isMapRTable()) {
      try {
        maprTable_.setWriteBufferSize(writeBufferSize);
      } catch (IOException e) {
        throw new InterruptedIOException("Cannot set write buffer size for this mapr table. Reason:"+e);
      }
      return;
    }
    this.writeBufferSize = writeBufferSize;
    if (currentWriteBufferSize.get() > writeBufferSize) {
      flush();
    }
  }

  private boolean isMapRTable() {
    return maprTable_ != null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long getWriteBufferSize() {
    if (isMapRTable()) {
      return maprTable_.getWriteBufferSize();
    }
    return this.writeBufferSize;
  }

  public void setRpcTimeout(int writeRpcTimeout) {
    this.writeRpcTimeout = writeRpcTimeout;
    if (!isMapRTable()) {
      this.ap.setRpcTimeout(writeRpcTimeout);
    }
  }

  public void setOperationTimeout(int operationTimeout) {
    this.operationTimeout = operationTimeout;
    if (!isMapRTable()) {
      this.ap.setOperationTimeout(operationTimeout);
    }
  }

  /**
   * This is used for legacy purposes in {@link HTable#getWriteBuffer()} only. This should not beÓ
   * called from production uses.
   * @deprecated Going away when we drop public support for {@link HTableInterface}.
Ó   */
  @Deprecated
  public List<Row> getWriteBuffer() {
    if (isMapRTable()) {
      return null;
    }
    return Arrays.asList(writeAsyncBuffer.toArray(new Row[0]));
  }

  @VisibleForTesting
  QueueRowAccess createQueueRowAccess() {
    return new QueueRowAccess();
  }

  @VisibleForTesting
  class QueueRowAccess implements RowAccess<Row>, Closeable {
    private int remainder = undealtMutationCount.getAndSet(0);
    private Mutation last = null;

    @Override
    public Iterator<Row> iterator() {
      return new Iterator<Row>() {
        private int countDown = remainder;
        @Override
        public boolean hasNext() {
          return countDown > 0;
        }
        @Override
        public Row next() {
          restoreLastMutation();
          if (!hasNext()) {
            throw new NoSuchElementException();
          }
          last = writeAsyncBuffer.poll();
          if (last == null) {
            throw new NoSuchElementException();
          }
          currentWriteBufferSize.addAndGet(-last.heapSize());
          --countDown;
          return last;
        }
        @Override
        public void remove() {
          if (last == null) {
            throw new IllegalStateException();
          }
          --remainder;
          last = null;
        }
      };
    }

    private void restoreLastMutation() {
      // restore the last mutation since it isn't submitted
      if (last != null) {
        writeAsyncBuffer.add(last);
        currentWriteBufferSize.addAndGet(last.heapSize());
        last = null;
      }
    }

    @Override
    public int size() {
      return remainder;
    }

    @Override
    public boolean isEmpty() {
      return remainder <= 0;
    }
    @Override
    public void close() {
      restoreLastMutation();
      if (remainder > 0) {
        undealtMutationCount.addAndGet(remainder);
        remainder = 0;
      }
    }
  }
}
