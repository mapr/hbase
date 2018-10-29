/**
 * Copyright 2011 The Apache Software Foundation
 *
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
package org.apache.hadoop.hbase.client.mapr;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.ClusterStatus;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.MasterNotRunningException;
import org.apache.hadoop.hbase.NamespaceDescriptor;
import org.apache.hadoop.hbase.ProcedureInfo;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableExistsException;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.TableNotFoundException;
import org.apache.hadoop.hbase.UnknownRegionException;
import org.apache.hadoop.hbase.ZooKeeperConnectionException;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.ipc.CoprocessorRpcChannel;
import org.apache.hadoop.hbase.protobuf.generated.AdminProtos.GetRegionInfoResponse.CompactionState;
import org.apache.hadoop.hbase.protobuf.generated.HBaseProtos.SnapshotDescription;
import org.apache.hadoop.hbase.protobuf.generated.MasterProtos.SnapshotResponse;
import org.apache.hadoop.hbase.quotas.QuotaFilter;
import org.apache.hadoop.hbase.quotas.QuotaRetriever;
import org.apache.hadoop.hbase.quotas.QuotaSettings;
import org.apache.hadoop.hbase.regionserver.wal.FailedLogCloseException;
import org.apache.hadoop.hbase.security.User;
import org.apache.hadoop.hbase.snapshot.HBaseSnapshotException;
import org.apache.hadoop.hbase.snapshot.RestoreSnapshotException;
import org.apache.hadoop.hbase.snapshot.SnapshotCreationException;
import org.apache.hadoop.hbase.snapshot.UnknownSnapshotException;
import org.apache.hadoop.hbase.util.Pair;

/**
 * Provides an interface to manage HBase database table metadata + general
 * administrative functions.  Use HBaseAdmin to create, drop, list, enable and
 * disable tables. Use it also to add and drop table column families.
 *
 * <p>See {@link HTable} to add, update, and delete data from an individual table.
 * <p>Currently HBaseAdmin instances are not expected to be long-lived.  For
 * example, an HBaseAdmin instance will not ride over a Master restart.
 */
public abstract class AbstractHBaseAdmin implements Closeable {
  private final static Log LOG = LogFactory.getLog(AbstractHBaseAdmin.class);

  /**
   * Sets the User for the Admin instance.
   */
  public void setUser(User user) {
    LOG.debug("setUser() called with MapR Table without impersonation support.");
  }

  public boolean abortProcedure(
      final long procId,
      final boolean mayInterruptIfRunning) throws IOException {
    throw new UnsupportedOperationException("abortProcedure is not supported for MapR.");
  }

  public Future<Boolean> abortProcedureAsync(
      final long procId,
      final boolean mayInterruptIfRunning) throws IOException {
    throw new UnsupportedOperationException("abortProcedureAsync is not supported for MapR.");
  }

  public ProcedureInfo[] listProcedures() throws IOException {
    throw new UnsupportedOperationException("listProcedures is not supported for MapR.");
  }

  /** @return - true if the master server is running. Throws an exception
   *  otherwise.
   * @throws ZooKeeperConnectionException
   * @throws MasterNotRunningException
   * @deprecated this has been deprecated without a replacement
   */
  @Deprecated
  public boolean isMasterRunning() {
    LOG.warn("isMasterRunning() called for a MapR Table, returning true.");
    return true;
  }
  /**
   * @param tableName Table to check.
   * @return True if table exists already.
   * @throws IOException
   */
  public abstract boolean tableExists(final String tableName)
      throws IOException;

  /**
   * List all the userspace tables.  In other words, scan the META table.
   *
   * If we wanted this to be really fast, we could implement a special
   * catalog table that just contains table names and their descriptors.
   * Right now, it only exists as part of the META table's region info.
   *
   * @return - returns an array of HTableDescriptors
   * @throws IOException if a remote or network exception occurs
   */
  public abstract HTableDescriptor[] listTables() throws IOException;

  public abstract TableName[] listTableNames() throws IOException;

  /**
   * List all the userspace tables matching the given regular expression.
   *
   * @param regex The regular expression to match against
   * @return - returns an array of HTableDescriptors
   * @throws IOException if a remote or network exception occurs
   * @see #listTables(java.util.regex.Pattern)
   */
  public abstract HTableDescriptor[] listTables(String regex) throws IOException;

  /**
   * List all of the names of userspace tables.
   * @return the list of table names
   * @throws IOException if a remote or network exception occurs
   */
  public String[] getTableNames() throws IOException {
    return getTableNames((String) null);
  }

  /**
   * List all of the names of userspace tables matching the given pattern
   * @param pattern The compiled regular expression to match against
   * @return the list of table names
   * @throws IOException if a remote or network exception occurs
   */
  public String[] getTableNames(Pattern pattern) throws IOException {
    return getTableNames(pattern.pattern());
  }

  /**
   * List all of the names of userspace tables matching the given regex
   * @param regex The regular expression to match against
   * @return the list of table names
   * @throws IOException if a remote or network exception occurs
   */
  public String[] getTableNames(String regex) throws IOException {
    ArrayList<String> tables = new ArrayList<String>();
    for (HTableDescriptor desc : listTables(regex)) {
      tables.add(desc.getNameAsString());
    }
    return tables.toArray(new String[tables.size()]);
  }

  /**
   * Method for getting the tableDescriptor
   * @param tableName as a byte []
   * @return the tableDescriptor
   * @throws TableNotFoundException
   * @throws IOException if a remote or network exception occurs
   */
  public abstract HTableDescriptor getTableDescriptor(final String tableName)
      throws TableNotFoundException, IOException;

  /**
   * Creates a new table with an initial set of empty regions defined by the
   * specified split keys.  The total number of regions created will be the
   * number of split keys plus one. Synchronous operation.
   * Note : Avoid passing empty split key.
   *
   * @param desc table descriptor for table
   * @param splitKeys array of split keys for the initial regions of the table
   *
   * @throws IllegalArgumentException if the table name is reserved, if the split keys
   * are repeated and if the split key has empty byte array.
   * @throws MasterNotRunningException if master is not running
   * @throws TableExistsException if table already exists (If concurrent
   * threads, the table may have been created between test-for-existence
   * and attempt-at-creation).
   * @throws IOException
   */
  public abstract void createTable(final HTableDescriptor desc, byte [][] splitKeys)
      throws IOException;

  /**
   * Deletes a table.
   * Synchronous operation.
   *
   * @param tableName name of table to delete
   * @throws IOException if a remote or network exception occurs
   */
  public abstract void deleteTable(final String tableName) throws IOException;

  /**
   * Deletes tables matching the passed in pattern and wait on completion.
   *
   * Warning: Use this method carefully, there is no prompting and the effect is
   * immediate. Consider using {@link #listTables(java.lang.String)} and
   * {@link #deleteTable(byte[])}
   *
   * @param regex The regular expression to match table names against
   * @return Table descriptors for tables that couldn't be deleted
   * @throws IOException
   * @see #deleteTables(java.util.regex.Pattern)
   * @see #deleteTable(java.lang.String)
   */
  public abstract HTableDescriptor[] deleteTables(String regex) throws IOException;

  /**
   * Enable a table.  May timeout.  Use {@link #enableTableAsync(byte[])}
   * and {@link #isTableEnabled(byte[])} instead.
   * The table has to be in disabled state for it to be enabled.
   * @param tableName name of the table
   * @throws IOException if a remote or network exception occurs
   * There could be couple types of IOException
   * TableNotFoundException means the table doesn't exist.
   * TableNotDisabledException means the table isn't in disabled state.
   * @see #isTableEnabled(byte[])
   * @see #disableTable(byte[])
   * @see #enableTableAsync(byte[])
   */
  public abstract void enableTable(final String tableName) throws IOException;

  /**
   * Enable tables matching the passed in pattern and wait on completion.
   *
   * Warning: Use this method carefully, there is no prompting and the effect is
   * immediate. Consider using {@link #listTables(java.lang.String)} and
   * {@link #enableTable(byte[])}
   *
   * @param regex The regular expression to match table names against
   * @throws IOException
   * @see #enableTables(java.util.regex.Pattern)
   * @see #enableTable(java.lang.String)
   */
  public abstract HTableDescriptor[] enableTables(String regex) throws IOException;

  /**
   * Disable table and wait on completion.  May timeout eventually.  Use
   * {@link #disableTableAsync(byte[])} and {@link #isTableDisabled(String)}
   * instead.
   * The table has to be in enabled state for it to be disabled.
   * @param tableName
   * @throws IOException
   * There could be couple types of IOException
   * TableNotFoundException means the table doesn't exist.
   * TableNotEnabledException means the table isn't in enabled state.
   */
  public abstract void disableTable(final String tableName)
      throws IOException;

  /**
   * Disable tables matching the passed in pattern and wait on completion.
   *
   * Warning: Use this method carefully, there is no prompting and the effect is
   * immediate. Consider using {@link #listTables(java.lang.String)} and
   * {@link #disableTable(byte[])}
   *
   * @param regex The regular expression to match table names against
   * @return Table descriptors for tables that couldn't be disabled
   * @throws IOException
   * @see #disableTables(java.util.regex.Pattern)
   * @see #disableTable(java.lang.String)
   */
  public abstract HTableDescriptor[] disableTables(String regex) throws IOException;

  /**
   * @param tableName name of table to check
   * @return true if table is on-line
   * @throws IOException if a remote or network exception occurs
   */
  public abstract boolean isTableEnabled(String tableName) throws IOException;

  /**
   * @param tableName name of table to check
   * @return true if table is off-line
   * @throws IOException if a remote or network exception occurs
   */
  public abstract boolean isTableDisabled(final String tableName) throws IOException;

  /**
   * @param tableName name of table to check
   * @return true if all regions of the table are available
   * @throws IOException if a remote or network exception occurs
   */
  public abstract boolean isTableAvailable(String tableName) throws IOException;

  /**
   * Use this api to check if the table has been created with the specified number of
   * splitkeys which was used while creating the given table.
   * Note : If this api is used after a table's region gets splitted, the api may return
   * false.
   * @param tableName
   *          name of table to check
   * @param splitKeys
   *          keys to check if the table has been created with all split keys
   * @throws IOException
   *           if a remote or network excpetion occurs
   */
  public abstract boolean isTableAvailable(String tableName,
                                           byte[][] splitKeys) throws IOException;

  /**
   * <b>MapR Notes: </b>For MapR tables, both values will always be 0.<p>
   *
   * Get the status of alter command - indicates how many regions have received
   * the updated schema Asynchronous operation.
   *
   * @param tableName TableName instance
   * @return Pair indicating the number of regions updated Pair.getFirst() is the
   *         regions that are yet to be updated Pair.getSecond() is the total number
   *         of regions of the table
   * @throws IOException
   *           if a remote or network exception occurs
   */
  public Pair<Integer, Integer> getAlterStatus(TableName tableName)
  throws IOException {
    // FIXME Revisit if we need to return tablet count
    LOG.warn("getAlterStatus() called for a MapR Table, return Pair(0,0).");
    return new Pair<Integer, Integer>(0, 0);
  }
  /**
   * Add a column to an existing table.
   * Asynchronous operation.
   *
   * @param tableName name of the table to add column to
   * @param column column descriptor of column to be added
   * @throws IOException if a remote or network exception occurs
   */
  public abstract void addColumn(final String tableName, HColumnDescriptor column)
      throws IOException;

  /**
   * Delete a column from a table.
   * Asynchronous operation.
   *
   * @param tableName name of table
   * @param columnName name of column to be deleted
   * @throws IOException if a remote or network exception occurs
   */
  public abstract void deleteColumn(final String tableName, final String columnName)
      throws IOException;

  /**
   * Modify an existing column family on a table.
   * Asynchronous operation.
   *
   * @param tableName name of table
   * @param descriptor new column descriptor to use
   * @throws IOException if a remote or network exception occurs
   */
  public abstract void modifyColumn(final String tableName, HColumnDescriptor descriptor)
      throws IOException;

  /**
   * Modify an existing table, more IRB friendly version.
   * Asynchronous operation.  This means that it may be a while before your
   * schema change is updated across all of the table.
   *
   * @param tableName name of table.
   * @param htd modified description of the table
   * @throws IOException if a remote or network exception occurs
   */
  public abstract void modifyTable(final String tableName, HTableDescriptor htd)
      throws IOException;

  /**
   * get the regions of a given table.
   *
   * @param tableName the name of the table
   * @return Ordered list of {@link HRegionInfo}.
   * @throws IOException
   */
  public abstract List<HRegionInfo> getTableRegions(final byte[] tableName)
      throws IOException;

  /** {@inheritDoc} */
  public abstract void close() throws IOException;

  public void closeRegion(byte[] regionname, String serverName) throws IOException {
    LOG.warn("closeRegion() called for a MapR Table, silently ignoring.");
  }

  /**
   * For expert-admins. Runs close on the regionserver. Closes a region based on
   * the encoded region name. The region server name is mandatory. If the
   * servername is provided then based on the online regions in the specified
   * regionserver the specified region will be closed. The master will not be
   * informed of the close. Note that the regionname is the encoded regionname.
   *
   * @param encodedRegionName
   *          The encoded region name; i.e. the hash that makes up the region
   *          name suffix: e.g. if regionname is
   *          <code>TestTable,0094429456,1289497600452.527db22f95c8a9e0116f0cc13c680396.</code>
   *          , then the encoded region name is:
   *          <code>527db22f95c8a9e0116f0cc13c680396</code>.
   * @param serverName
   *          The servername of the regionserver. A server name is made of host,
   *          port and startcode. This is mandatory. Here is an example:
   *          <code> host187.example.com,60020,1289493121758</code>
   * @return true if the region was closed, false if not.
   * @throws IOException
   *           if a remote or network exception occurs
   */
  public boolean closeRegionWithEncodedRegionName(final String encodedRegionName,
      final String serverName) throws IOException {
    throw new UnsupportedOperationException("closeRegionWithEncodedRegionName for MapR is unsupported.");
  }
  public void closeRegion(ServerName sn, HRegionInfo hri) throws IOException {
    LOG.warn("closeRegion() called for a MapR Table, silently ignoring.");
  }

  public List<HRegionInfo> getOnlineRegions(final ServerName sn) throws IOException {
      throw new UnsupportedOperationException("getOnlineRegions for MapR is unsupported.");
  }

  public void flush(byte[] tableNameOrRegionName) throws IOException {
    LOG.warn("flush() called for a MapR Table, silently ignoring.");
  }

  public void createNamespace(final NamespaceDescriptor descriptor) throws IOException {
    throw new UnsupportedOperationException("createNamespace for a MapR is unsupported.");
  }

  public void modifyNamespace(final NamespaceDescriptor descriptor) throws IOException {
    throw new UnsupportedOperationException("modifyNamespace for a MapR is unsupported.");
  }

  public void deleteNamespace(final String name) throws IOException {
    throw new UnsupportedOperationException("deleteNamespace for a MapR is unsupported.");
  }

  public NamespaceDescriptor getNamespaceDescriptor(final String name) throws IOException {
    throw new UnsupportedOperationException("getNamespaceDescriptor for a MapR is unsupported.");
  }

  public NamespaceDescriptor[] listNamespaceDescriptors() throws IOException {
    throw new UnsupportedOperationException("listNamespaceDescriptors for a MapR is unsupported.");
  }

  public HTableDescriptor[] listTableDescriptorsByNamespace(final String name) throws IOException {
    throw new UnsupportedOperationException("listTableDescriptorsByNamespace for a MapR is unsupported.");
  }

  public TableName[] listTableNamesByNamespace(final String name) throws IOException {
    throw new UnsupportedOperationException("listTableNamesByNamespace for a MapR is unsupported.");
  }

  public void compact(byte[] tableNameOrRegionName, byte[] columnFamily,
      boolean major) throws IOException {
    LOG.warn("compact() called for a MapR Table, silently ignoring.");
  }

  public void compact(ServerName sn, HRegionInfo hri, boolean major,
      byte[] family) throws IOException {
    LOG.warn("compact() called for a MapR Table, silently ignoring.");
  }

  public void move(byte[] encodedRegionName, byte[] destServerName)
      throws UnknownRegionException, MasterNotRunningException, ZooKeeperConnectionException {
    LOG.warn("move() called for a MapR Table, silently ignoring.");
  }

  public void offline(byte[] regionName) throws ZooKeeperConnectionException {
    LOG.warn("offline() called for a MapR Table, silently ignoring.");
  }

  public boolean enableCatalogJanitor(boolean enable)
    throws MasterNotRunningException {
    LOG.warn("enableCatalogJanitor for MapR, silently ignoring.");
    return false;
  }

  public int runCatalogScan()
    throws MasterNotRunningException {
    LOG.warn("runCatalogScan for MapR, silently ignoring.");
    return 0;
  }

  public boolean isCatalogJanitorEnabled()
    throws MasterNotRunningException {
    LOG.warn("isCatalogJanitorEnabled for MapR, silently ignoring.");
    return false;
  }

  public void assign(byte[] regionName) throws IOException {
    LOG.warn("assign() called for a MapR Table, silently ignoring.");
  }

  public void unassign(byte[] regionName, boolean force) throws IOException {
    LOG.warn("unassign() called for a MapR Table, silently ignoring.");
  }

  public void mergeRegions(final byte[] encodedNameOfRegionA,
          final byte[] encodedNameOfRegionB, final boolean forcible)
          throws IOException {
    throw new UnsupportedOperationException("mergeRegions for a MapR is unsupported.");
  }

  public void split(byte[] tableNameOrRegionName, byte[] splitPoint) throws IOException {
    LOG.warn("split() called for a MapR Table, silently ignoring.");
  }

  /**
   * TODO: Move this to com.mapr.fs.HBaseAdminImpl
   */
  public void truncateTable(final TableName tableName, final boolean preserveSplits)
      throws IOException {
    byte[][] splitKeys = null;
    if (preserveSplits) {
      // fetch the split keys of existing table
      List<HRegionInfo> regions = getTableRegions(tableName.getQualifier());
      Collections.sort(regions);
      List<byte[]> splitKeyList = new ArrayList<byte[]>(regions.size());
      for (HRegionInfo region : regions) {
        if (region.getEndKey() != null && region.getEndKey().length != 0) {
          splitKeyList.add(region.getEndKey());
        }
      }
      splitKeys = splitKeyList.toArray(new byte[splitKeyList.size()][]);
    }

    // save the table descriptor before deleting
    String tablePath = tableName.getAliasAsString();
    HTableDescriptor htd = getTableDescriptor(tablePath);

    // now we can delete the table
    deleteTable(tablePath);

    //TODO -- re-enable the code below when cherry pick "MAPR-14741: Added tableuuid to CopyTable."
    // cleanup reserved properties from the descriptor
    //htd.remove(HTableDescriptor.MAPR_UUID_KEY);  
    htd.remove("DISABLED");

    createTable(htd, splitKeys);
  }

  public abstract TableName[] listTableNames(String regex) throws IOException;

  /**
   * Shuts down the cluster
   * @throws IOException if a remote or network exception occurs
   */
  public synchronized void shutdown() throws IOException {
    LOG.warn("shutdown() called for a MapR cluster, silently ignoring.");
  }

  /**
   * Shuts down the current master only.
   * Does not shutdown the cluster.
   * @see #shutdown()
   * @throws IOException if a remote or network exception occurs
   */
  public synchronized void stopMaster() throws IOException {
    LOG.warn("stopMaster() called for a MapR cluster, silently ignoring.");
  }

  /**
   * Stop the designated regionserver
   * @param hostnamePort Hostname and port delimited by a <code>:</code> as in
   * <code>example.org:1234</code>
   * @throws IOException if a remote or network exception occurs
   */
  public synchronized void stopRegionServer(final String hostnamePort)
  throws IOException {
    LOG.warn("stopRegionServer() called for a MapR cluster, silently ignoring.");
  }
  /**
   * @return cluster status
   * @throws IOException if a remote or network exception occurs
   */
  public ClusterStatus getClusterStatus() throws IOException {
    LOG.warn("getClusterStatus() called for a MapR cluster, return null.");
    return null;
  }

  /**
   * Roll the log writer. I.e. when using a file system based write ahead log,
   * start writing log messages to a new file.
   *
   * Note that when talking to a version 1.0+ HBase deployment, the rolling is asynchronous.
   * This method will return as soon as the roll is requested and the return value will
   * always be null. Additionally, the named region server may schedule store flushes at the
   * request of the wal handling the roll request.
   *
   * When talking to a 0.98 or older HBase deployment, the rolling is synchronous and the
   * return value may be either null or a list of encoded region names.
   *
   * @param serverName
   *          The servername of the regionserver. A server name is made of host,
   *          port and startcode. This is mandatory. Here is an example:
   *          <code> host187.example.com,60020,1289493121758</code>
   * @return a set of {@link HRegionInfo#getEncodedName()} that would allow the wal to
   *         clean up some underlying files. null if there's nothing to flush.
   * @throws IOException if a remote or network exception occurs
   * @throws FailedLogCloseException
   * @deprecated use {@link #rollWALWriter(ServerName)}
   */
  @Deprecated
  public synchronized byte[][] rollHLogWriter(String serverName)
      throws IOException, FailedLogCloseException {
    LOG.warn("rollHLogWriter() called for a MapR cluster, returning null.");
    return null;
  }

  public synchronized void rollWALWriter(ServerName serverName)
      throws IOException, FailedLogCloseException {
    LOG.warn("rollHLogWriter() called for a MapR cluster, silently ignoring.");
  }

  public String[] getMasterCoprocessors() {
    LOG.warn("getMasterCoprocessors() called for a MapR cluster, returning empty.");
    return new String[0];
  }

  public CompactionState getCompactionState(TableName tableName)
  throws IOException {
    LOG.warn("getCompactionState() called for a MapR cluster, returning CompactionState.NONE.");
    return CompactionState.NONE;
  }

  public CompactionState getCompactionStateForRegion(final byte[] regionName)
  throws IOException {
    LOG.warn("getCompactionStateForRegion() called for a MapR cluster, returning CompactionState.NONE.");
    return CompactionState.NONE;
  }

  /**
   * Create typed snapshot of the table.
   * <p>
   * Snapshots are considered unique based on <b>the name of the snapshot</b>. Attempts to take a
   * snapshot with the same name (even a different type or with different parameters) will fail with
   * a {@link SnapshotCreationException} indicating the duplicate naming.
   * <p>
   * Snapshot names follow the same naming constraints as tables in HBase. See
   * {@link org.apache.hadoop.hbase.TableName#isLegalFullyQualifiedTableName(byte[])}.
   * <p>
   * @param snapshotName name to give the snapshot on the filesystem. Must be unique from all other
   *          snapshots stored on the cluster
   * @param tableName name of the table to snapshot
   * @param type type of snapshot to take
   * @throws IOException we fail to reach the master
   * @throws SnapshotCreationException if snapshot creation failed
   * @throws IllegalArgumentException if the snapshot request is formatted incorrectly
   */
  public void snapshot(final String snapshotName,
          TableName tableName,
         SnapshotDescription.Type type) throws IOException, SnapshotCreationException,
IllegalArgumentException {
    throw new UnsupportedOperationException("snapshot for a MapR is unsupported.");
  }

  /**
   * Take a snapshot and wait for the server to complete that snapshot (blocking).
   * <p>
   * Only a single snapshot should be taken at a time for an instance of HBase, or results may be
   * undefined (you can tell multiple HBase clusters to snapshot at the same time, but only one at a
   * time for a single cluster).
   * <p>
   * Snapshots are considered unique based on <b>the name of the snapshot</b>. Attempts to take a
   * snapshot with the same name (even a different type or with different parameters) will fail with
   * a {@link SnapshotCreationException} indicating the duplicate naming.
   * <p>
   * Snapshot names follow the same naming constraints as tables in HBase. See
   * {@link org.apache.hadoop.hbase.TableName#isLegalFullyQualifiedTableName(byte[])}.
   * <p>
   * You should probably use {@link #snapshot(String, String)} or {@link #snapshot(byte[], byte[])}
   * unless you are sure about the type of snapshot that you want to take.
   * @param snapshot snapshot to take
   * @throws IOException or we lose contact with the master.
   * @throws SnapshotCreationException if snapshot failed to be taken
   * @throws IllegalArgumentException if the snapshot request is formatted incorrectly
   */
  public void snapshot(SnapshotDescription snapshot) throws IOException, SnapshotCreationException,
      IllegalArgumentException {
    throw new UnsupportedOperationException("snapshot for a MapR is unsupported.");
  }

  /**
   * Take a snapshot without waiting for the server to complete that snapshot (asynchronous)
   * <p>
   * Only a single snapshot should be taken at a time, or results may be undefined.
   * @param snapshot snapshot to take
   * @return response from the server indicating the max time to wait for the snapshot
   * @throws IOException if the snapshot did not succeed or we lose contact with the master.
   * @throws SnapshotCreationException if snapshot creation failed
   * @throws IllegalArgumentException if the snapshot request is formatted incorrectly
   */
  public SnapshotResponse takeSnapshotAsync(SnapshotDescription snapshot) throws IOException,
      SnapshotCreationException {
    throw new UnsupportedOperationException("takeSnapshotAsync for a MapR is unsupported.");
  }

  /**
   * Check the current state of the passed snapshot.
   * <p>
   * There are three possible states:
   * <ol>
   * <li>running - returns <tt>false</tt></li>
   * <li>finished - returns <tt>true</tt></li>
   * <li>finished with error - throws the exception that caused the snapshot to fail</li>
   * </ol>
   * <p>
   * The cluster only knows about the most recent snapshot. Therefore, if another snapshot has been
   * run/started since the snapshot your are checking, you will recieve an
   * {@link UnknownSnapshotException}.
   * @param snapshot description of the snapshot to check
   * @return <tt>true</tt> if the snapshot is completed, <tt>false</tt> if the snapshot is still
   *         running
   * @throws IOException if we have a network issue
   * @throws HBaseSnapshotException if the snapshot failed
   * @throws UnknownSnapshotException if the requested snapshot is unknown
   */
  public boolean isSnapshotFinished(final SnapshotDescription snapshot)
      throws IOException, HBaseSnapshotException, UnknownSnapshotException {
    throw new UnsupportedOperationException("isSnapshotFinished for a MapR is unsupported.");
  }

  /**
   * Create a new table by cloning the snapshot content.
   *
   * @param snapshotName name of the snapshot to be cloned
   * @param tableName name of the table where the snapshot will be restored
   * @throws IOException if a remote or network exception occurs
   * @throws TableExistsException if table to be created already exists
   * @throws RestoreSnapshotException if snapshot failed to be cloned
   * @throws IllegalArgumentException if the specified table has not a valid name
   */
  public void cloneSnapshot(final String snapshotName, final TableName tableName)
      throws IOException, TableExistsException, RestoreSnapshotException {
    throw new UnsupportedOperationException("cloneSnapshot for a MapR is unsupported.");
  }

  /**
   * Execute a distributed procedure on a cluster synchronously with return data
   *
   * @param signature A distributed procedure is uniquely identified
   * by its signature (default the root ZK node name of the procedure).
   * @param instance The instance name of the procedure. For some procedures, this parameter is
   * optional.
   * @param props Property/Value pairs of properties passing to the procedure
   * @return data returned after procedure execution. null if no return data.
   * @throws IOException
   */
  public byte[] execProcedureWithRet(String signature, String instance,
      Map<String, String> props) throws IOException {
    throw new UnsupportedOperationException("execProcedureWithRet for a MapR is unsupported.");
  }

  /**
   * Execute a distributed procedure on a cluster.
   *
   * @param signature A distributed procedure is uniquely identified
   * by its signature (default the root ZK node name of the procedure).
   * @param instance The instance name of the procedure. For some procedures, this parameter is
   * optional.
   * @param props Property/Value pairs of properties passing to the procedure
   * @throws IOException
   */
  public void execProcedure(String signature, String instance,
      Map<String, String> props) throws IOException {
    throw new UnsupportedOperationException("execProcedure for a MapR is unsupported.");
  }

  /**
   * Check the current state of the specified procedure.
   * <p>
   * There are three possible states:
   * <ol>
   * <li>running - returns <tt>false</tt></li>
   * <li>finished - returns <tt>true</tt></li>
   * <li>finished with error - throws the exception that caused the procedure to fail</li>
   * </ol>
   * <p>
   *
   * @param signature The signature that uniquely identifies a procedure
   * @param instance The instance name of the procedure
   * @param props Property/Value pairs of properties passing to the procedure
   * @return true if the specified procedure is finished successfully, false if it is still running
   * @throws IOException if the specified procedure finished with error
   */
  public boolean isProcedureFinished(String signature, String instance, Map<String, String> props)
      throws IOException {
    throw new UnsupportedOperationException("isProcedureFinished for a MapR is unsupported.");
  }

  /**
   * List completed snapshots.
   * @return a list of snapshot descriptors for completed snapshots
   * @throws IOException if a network error occurs
   */
  public List<SnapshotDescription> listSnapshots() throws IOException {
    throw new UnsupportedOperationException("listSnapshots for a MapR is unsupported.");
  }

  /**
   * List all the completed snapshots matching the given pattern.
   *
   * @param pattern The compiled regular expression to match against
   * @return - returns a List of SnapshotDescription
   * @throws IOException if a remote or network exception occurs
   */
  public List<SnapshotDescription> listSnapshots(Pattern pattern) throws IOException {
    throw new UnsupportedOperationException("listSnapshots for a MapR is unsupported.");
  }
  /**
   * Delete an existing snapshot.
   * @param snapshotName name of the snapshot
   * @throws IOException if a remote or network exception occurs
   */
  public void deleteSnapshot(final String snapshotName) throws IOException {
    throw new UnsupportedOperationException("deleteSnapshot for a MapR is unsupported.");
  }
  /**
   * Delete existing snapshots whose names match the pattern passed.
   * @param pattern pattern for names of the snapshot to match
   * @throws IOException if a remote or network exception occurs
   */
  public void deleteSnapshots(final Pattern pattern) throws IOException {
    throw new UnsupportedOperationException("deleteSnapshot for a MapR is unsupported.");
  }
  /**
   * Apply the new quota settings.
   * @param quota the quota settings
   * @throws IOException if a remote or network exception occurs
   */
  public void setQuota(final QuotaSettings quota) throws IOException {
    throw new UnsupportedOperationException("setQuota is not supported for MapR.");
  }
  /**
   * Return a Quota Scanner to list the quotas based on the filter.
   * @param filter the quota settings filter
   * @return the quota scanner
   * @throws IOException if a remote or network exception occurs
   */
  public QuotaRetriever getQuotaRetriever(final QuotaFilter filter) throws IOException {
    throw new UnsupportedOperationException("getQuotaRetriever is not supported for MapR.");
  }
  /**
   * Creates and returns a {@link com.google.protobuf.RpcChannel} instance
   * connected to the active master.
   *
   * <p>
   * The obtained {@link com.google.protobuf.RpcChannel} instance can be used to access a published
   * coprocessor {@link com.google.protobuf.Service} using standard protobuf service invocations:
   * </p>
   *
   * <div style="background-color: #cccccc; padding: 2px">
   * <blockquote><pre>
   * CoprocessorRpcChannel channel = myAdmin.coprocessorService();
   * MyService.BlockingInterface service = MyService.newBlockingStub(channel);
   * MyCallRequest request = MyCallRequest.newBuilder()
   *     ...
   *     .build();
   * MyCallResponse response = service.myCall(null, request);
   * </pre></blockquote></div>
   *
   * @return A MasterCoprocessorRpcChannel instance
   */
  public CoprocessorRpcChannel coprocessorService() {
    throw new UnsupportedOperationException("coprocessorService is not supported for MapR.");
  }
  /**
   * Creates and returns a {@link com.google.protobuf.RpcChannel} instance
   * connected to the passed region server.
   *
   * <p>
   * The obtained {@link com.google.protobuf.RpcChannel} instance can be used to access a published
   * coprocessor {@link com.google.protobuf.Service} using standard protobuf service invocations:
   * </p>
   *
   * <div style="background-color: #cccccc; padding: 2px">
   * <blockquote><pre>
   * CoprocessorRpcChannel channel = myAdmin.coprocessorService(serverName);
   * MyService.BlockingInterface service = MyService.newBlockingStub(channel);
   * MyCallRequest request = MyCallRequest.newBuilder()
   *     ...
   *     .build();
   * MyCallResponse response = service.myCall(null, request);
   * </pre></blockquote></div>
   *
   * @param sn the server name to which the endpoint call is made
   * @return A RegionServerCoprocessorRpcChannel instance
   */
  public CoprocessorRpcChannel coprocessorService(ServerName sn) {
    throw new UnsupportedOperationException("coprocessorService is not supported for MapR.");
  }
  public void updateConfiguration(ServerName server) throws IOException {
    LOG.warn("updateConfiguration() called for a MapR Table, silently ignoring.");
  }
  public void updateConfiguration() throws IOException {
    LOG.warn("updateConfiguration() called for a MapR Table, silently ignoring.");
  }

  public int getMasterInfoPort() throws IOException {
    LOG.warn("getMasterInfoPort() called for a MapR Table, return 0.");
    return 0;
  }
  public long getLastMajorCompactionTimestamp(final TableName tableName) throws IOException {
    LOG.warn("getMasterInfoPort() called for a MapR Table, return 0.");
    return 0;
  }
  public long getLastMajorCompactionTimestampForRegion(final byte[] regionName) throws IOException {
    LOG.warn("getMasterInfoPort() called for a MapR Table, return 0.");
    return 0;
  }
}
