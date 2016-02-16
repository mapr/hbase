/**
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
package org.apache.hadoop.hbase.client;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.classification.InterfaceAudience;
import org.apache.hadoop.hbase.classification.InterfaceStability;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.HRegionLocation;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.KeyValueUtil;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.TableNotFoundException;
import org.apache.hadoop.hbase.client.ConnectionManager.HConnectionImplementation;
import org.apache.hadoop.hbase.client.AsyncProcess.AsyncRequestFuture;
import org.apache.hadoop.hbase.client.coprocessor.Batch;
import org.apache.hadoop.hbase.client.coprocessor.Batch.Callback;
import org.apache.hadoop.hbase.client.mapr.AbstractHTable;
import org.apache.hadoop.hbase.client.mapr.BaseTableMappingRules;
import org.apache.hadoop.hbase.client.mapr.GenericHFactory;
import org.apache.hadoop.hbase.client.mapr.TableMappingRulesFactory;
import org.apache.hadoop.hbase.filter.BinaryComparator;
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp;
import org.apache.hadoop.hbase.ipc.CoprocessorRpcChannel;
import org.apache.hadoop.hbase.ipc.PayloadCarryingRpcController;
import org.apache.hadoop.hbase.ipc.RegionCoprocessorRpcChannel;
import org.apache.hadoop.hbase.ipc.RpcControllerFactory;
import org.apache.hadoop.hbase.protobuf.ProtobufUtil;
import org.apache.hadoop.hbase.protobuf.RequestConverter;
import org.apache.hadoop.hbase.protobuf.generated.ClientProtos;
import org.apache.hadoop.hbase.protobuf.generated.ClientProtos.MultiRequest;
import org.apache.hadoop.hbase.protobuf.generated.ClientProtos.MutateRequest;
import org.apache.hadoop.hbase.protobuf.generated.ClientProtos.MutateResponse;
import org.apache.hadoop.hbase.protobuf.generated.ClientProtos.RegionAction;
import org.apache.hadoop.hbase.protobuf.generated.HBaseProtos.CompareType;
import org.apache.hadoop.hbase.protobuf.generated.MasterProtos.GetTableDescriptorsRequest;
import org.apache.hadoop.hbase.protobuf.generated.MasterProtos.GetTableDescriptorsResponse;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.MapRUtil;
import org.apache.hadoop.hbase.util.Pair;
import org.apache.hadoop.hbase.util.ReflectionUtils;
import org.apache.hadoop.hbase.util.Threads;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Service;
import com.google.protobuf.ServiceException;

/**
 * An implementation of {@link Table}. Used to communicate with a single HBase table.
 * Lightweight. Get as needed and just close when done.
 * Instances of this class SHOULD NOT be constructed directly.
 * Obtain an instance via {@link Connection}. See {@link ConnectionFactory}
 * class comment for an example of how.
 *
 * <p>This class is NOT thread safe for reads nor writes.
 * In the case of writes (Put, Delete), the underlying write buffer can
 * be corrupted if multiple threads contend over a single HTable instance.
 * In the case of reads, some fields used by a Scan are shared among all threads.
 *
 * <p>HTable is no longer a client API. Use {@link Table} instead. It is marked
 * InterfaceAudience.Private indicating that this is an HBase-internal class as defined in
 * <a href="https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-common/InterfaceClassification.html">Hadoop
 * Interface Classification</a>
 * There are no guarantees for backwards source / binary compatibility and methods or class can
 * change or go away without deprecation.
 *
 * @see Table
 * @see Admin
 * @see Connection
 * @see ConnectionFactory
 */
@InterfaceAudience.Private
@InterfaceStability.Stable
public class HTable implements HTableInterface, RegionLocator {
  private static final GenericHFactory<AbstractHTable> tableFactory_ =
      new GenericHFactory<AbstractHTable>();
  private BaseTableMappingRules tableMappingRule_;
  private final AbstractHTable maprTable_;

  private static final Log LOG = LogFactory.getLog(HTable.class);
  protected ClusterConnection connection;
  private TableName tableName;
  private volatile Configuration configuration;
  private TableConfiguration tableConfiguration;
  protected BufferedMutatorImpl mutator;
  private boolean autoFlush = true;
  private boolean closed = false;
  protected int scannerCaching;
  protected long scannerMaxResultSize;
  private ExecutorService pool;  // For Multi & Scan
  private int operationTimeout;
  private boolean cleanupPoolOnClose; // shutdown the pool in close()
  private boolean cleanupConnectionOnClose; // close the connection in close()
  private Consistency defaultConsistency = Consistency.STRONG;
  private HRegionLocator locator;

  /** The Async process for batch */
  protected AsyncProcess multiAp;
  private RpcRetryingCallerFactory rpcCallerFactory;
  private RpcControllerFactory rpcControllerFactory;

  /**
   * Creates an object to access a HBase table.
   * @param conf Configuration object to use.
   * @param tableName Name of the table.
   * @throws IOException if a remote or network exception occurs
   * @deprecated Constructing HTable objects manually has been deprecated. Please use
   * {@link Connection} to instantiate a {@link Table} instead.
   */
  @Deprecated
  public HTable(Configuration conf, final String tableName)
  throws IOException {
    this(conf, TableName.valueOf(tableName));
  }

  /**
   * Creates an object to access a HBase table.
   * @param conf Configuration object to use.
   * @param tableName Name of the table.
   * @throws IOException if a remote or network exception occurs
   * @deprecated Constructing HTable objects manually has been deprecated. Please use
   * {@link Connection} to instantiate a {@link Table} instead.
   */
  @Deprecated
  public HTable(Configuration conf, final byte[] tableName)
  throws IOException {
    this(conf, TableName.valueOf(tableName));
  }

  /**
   * Creates an object to access a HBase table.
   * @param conf Configuration object to use.
   * @param tableName table name pojo
   * @throws IOException if a remote or network exception occurs
   * @deprecated Constructing HTable objects manually has been deprecated. Please use
   * {@link Connection} to instantiate a {@link Table} instead.
   */
  @Deprecated
  public HTable(Configuration conf, final TableName tableName)
  throws IOException {
    if ((maprTable_ = initIfMapRTable(conf, tableName)) != null) {
      return; // If it was a MapR table, our work is done
    }

    this.tableName = MapRUtil.adjustTableName(tableName);
    this.cleanupPoolOnClose = this.cleanupConnectionOnClose = true;
    if (conf == null) {
      this.connection = null;
      return;
    }
    this.connection = ConnectionManager.getConnectionInternal(conf);
    this.configuration = conf;

    this.pool = getDefaultExecutor(conf);
    this.finishSetup();
  }

  /**
   * Creates an object to access a HBase table.
   * @param tableName Name of the table.
   * @param connection HConnection to be used.
   * @throws IOException if a remote or network exception occurs
   * @deprecated Do not use.
   */
  @Deprecated
  public HTable(TableName tableName, Connection connection) throws IOException {
    // 'this.connection' must be set before initIfMapRTable() table is called to enable impersonation
    this.connection = (ClusterConnection)connection;
    if ((maprTable_ = initIfMapRTable(connection.getConfiguration(), tableName)) != null) {
      // If it was a MapR table, our work is done
      return;
    }

    this.tableName = MapRUtil.adjustTableName(tableName);
    this.cleanupPoolOnClose = true;
    this.cleanupConnectionOnClose = false;
    this.configuration = connection.getConfiguration();

    this.pool = getDefaultExecutor(this.configuration);
    this.finishSetup();
  }

  // Marked Private @since 1.0
  @InterfaceAudience.Private
  public static ThreadPoolExecutor getDefaultExecutor(Configuration conf) {
    int maxThreads = conf.getInt("hbase.htable.threads.max", Integer.MAX_VALUE);
    if (maxThreads == 0) {
      maxThreads = 1; // is there a better default?
    }
    long keepAliveTime = conf.getLong("hbase.htable.threads.keepalivetime", 60);

    // Using the "direct handoff" approach, new threads will only be created
    // if it is necessary and will grow unbounded. This could be bad but in HCM
    // we only create as many Runnables as there are region servers. It means
    // it also scales when new region servers are added.
    ThreadPoolExecutor pool = new ThreadPoolExecutor(1, maxThreads, keepAliveTime, TimeUnit.SECONDS,
        new SynchronousQueue<Runnable>(), Threads.newDaemonThreadFactory("htable"));
    pool.allowCoreThreadTimeOut(true);
    return pool;
  }

  /**
   * Creates an object to access a HBase table.
   * @param conf Configuration object to use.
   * @param tableName Name of the table.
   * @param pool ExecutorService to be used.
   * @throws IOException if a remote or network exception occurs
   * @deprecated Constructing HTable objects manually has been deprecated. Please use
   * {@link Connection} to instantiate a {@link Table} instead.
   */
  @Deprecated
  public HTable(Configuration conf, final byte[] tableName, final ExecutorService pool)
      throws IOException {
    this(conf, TableName.valueOf(tableName), pool);
  }

  /**
   * Creates an object to access a HBase table.
   * @param conf Configuration object to use.
   * @param tableName Name of the table.
   * @param pool ExecutorService to be used.
   * @throws IOException if a remote or network exception occurs
   * @deprecated Constructing HTable objects manually has been deprecated. Please use
   * {@link Connection} to instantiate a {@link Table} instead.
   */
  @Deprecated
  public HTable(Configuration conf, final TableName tableName, final ExecutorService pool)
      throws IOException {
    if ((maprTable_ = initIfMapRTable(conf, tableName)) != null) {
      // If it was a MapR table, our work is done
      return;
    }
    this.connection = ConnectionManager.getConnectionInternal(conf);
    this.configuration = conf;
    this.pool = pool;
    if (pool == null) {
      this.pool = getDefaultExecutor(conf);
      this.cleanupPoolOnClose = true;
    } else {
      this.cleanupPoolOnClose = false;
    }
    this.tableName = MapRUtil.adjustTableName(tableName);
    this.cleanupConnectionOnClose = true;
    this.finishSetup();
  }

  /**
   * Creates an object to access a HBase table.
   * @param tableName Name of the table.
   * @param connection HConnection to be used.
   * @param pool ExecutorService to be used.
   * @throws IOException if a remote or network exception occurs.
   * @deprecated Do not use, internal ctor.
   */
  @Deprecated
  public HTable(final byte[] tableName, final Connection connection,
      final ExecutorService pool) throws IOException {
    this(TableName.valueOf(tableName), connection, pool);
  }

  /** @deprecated Do not use, internal ctor. */
  @Deprecated
  public HTable(TableName tableName, final Connection connection,
      final ExecutorService pool) throws IOException {
    this(tableName, (ClusterConnection)connection, null, null, null, pool);
  }

  /**
   * Creates an object to access a HBase table.
   * Used by HBase internally.  DO NOT USE. See {@link ConnectionFactory} class comment for how to
   * get a {@link Table} instance (use {@link Table} instead of {@link HTable}).
   * @param tableName Name of the table.
   * @param connection HConnection to be used.
   * @param pool ExecutorService to be used.
   * @throws IOException if a remote or network exception occurs
   */
  @InterfaceAudience.Private
  public HTable(TableName tableName, final ClusterConnection connection,
      final TableConfiguration tableConfig,
      final RpcRetryingCallerFactory rpcCallerFactory,
      final RpcControllerFactory rpcControllerFactory,
      final ExecutorService pool) throws IOException {
    // 'this.connection' must be set before initIfMapRTable() table is called to enable impersonation
    this.connection = connection;
    if ((maprTable_ = initIfMapRTable(connection.getConfiguration(), tableName)) != null) {
      // If it was a MapR table, our work is done
      return;
    }

    if (connection == null || connection.isClosed()) {
      throw new IllegalArgumentException("Connection is null or closed.");
    }
    this.tableName = MapRUtil.adjustTableName(tableName);
    this.cleanupConnectionOnClose = false;
    this.configuration = connection.getConfiguration();
    this.tableConfiguration = tableConfig;
    this.pool = pool;
    if (pool == null) {
      this.pool = getDefaultExecutor(this.configuration);
      this.cleanupPoolOnClose = true;
    } else {
      this.cleanupPoolOnClose = false;
    }

    this.rpcCallerFactory = rpcCallerFactory;
    this.rpcControllerFactory = rpcControllerFactory;

    this.finishSetup();
  }

  /**
   * For internal testing. Uses Connection provided in {@code params}.
   * @throws IOException
   */
  @VisibleForTesting
  protected HTable(ClusterConnection conn, BufferedMutatorParams params) throws IOException {
    connection = conn;
    tableName = params.getTableName();
    if ((maprTable_ = initIfMapRTable(connection.getConfiguration(), tableName)) != null) {
      // If it was a MapR table, our work is done
      return;
     }
    tableConfiguration = new TableConfiguration(connection.getConfiguration());
    cleanupPoolOnClose = false;
    cleanupConnectionOnClose = false;
    // used from tests, don't trust the connection is real
    this.mutator = new BufferedMutatorImpl(conn, null, null, params);
  }

  /**
   * @return maxKeyValueSize from configuration.
   */
  public static int getMaxKeyValueSize(Configuration conf) {
    return conf.getInt("hbase.client.keyvalue.maxsize", -1);
  }

  /**
   * setup this HTable's parameter based on the passed configuration
   */
  private void finishSetup() throws IOException {
    if (tableConfiguration == null) {
      tableConfiguration = new TableConfiguration(configuration);
    }

    this.operationTimeout = tableName.isSystemTable() ?
        tableConfiguration.getMetaOperationTimeout() : tableConfiguration.getOperationTimeout();
    this.scannerCaching = tableConfiguration.getScannerCaching();
    this.scannerMaxResultSize = tableConfiguration.getScannerMaxResultSize();
    if (this.rpcCallerFactory == null) {
      this.rpcCallerFactory = connection.getNewRpcRetryingCallerFactory(configuration);
    }
    if (this.rpcControllerFactory == null) {
      this.rpcControllerFactory = RpcControllerFactory.instantiate(configuration);
    }

    // puts need to track errors globally due to how the APIs currently work.
    multiAp = this.connection.getAsyncProcess();

    this.closed = false;

    this.locator = new HRegionLocator(tableName, connection);
  }

  /**
   * Tests if the table identified by tableName should be considered
   * as a MapR table according to table mapping rules and if yes 
   * create a MapR table instance
   *
   * @param conf
   * @param tableName
   * @return true if this is a MapR table
   * @throws IOException
   */
  private AbstractHTable initIfMapRTable(final Configuration conf,
      final TableName tableName) throws IOException {
    tableMappingRule_ = TableMappingRulesFactory.create(conf);
    if (!BaseTableMappingRules.isInHBaseService()
        && tableMappingRule_.isMapRTable(tableName)) {
      try {
        configuration = conf;
        if (this.connection instanceof HConnectionImplementation) {
          return ((HConnectionImplementation)this.connection).getUser().getUGI().doAs(
              new PrivilegedAction<AbstractHTable>() {
                @Override
                public AbstractHTable run() {
                  return createMapRTable(conf, tableName);
                }
              });
        } else {
          return createMapRTable(conf, tableName);
        }
      } catch (Throwable e) {
        GenericHFactory.handleIOException(e);
      }
    }
    return null;
  }

  private AbstractHTable createMapRTable(Configuration conf,
      TableName tableName) {
    return tableFactory_.getImplementorInstance(
        configuration.get("htable.impl.mapr", "com.mapr.fs.hbase.HTableImpl"),
        new Object[] {conf, tableName.getQualifier()},
        new Class[] {Configuration.class, byte[].class});
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Configuration getConfiguration() {
    return configuration;
  }

  /**
   * <b>Return <code>true</code> for MapR Tables.</b><p>
   * Tells whether or not a table is enabled or not. This method creates a
   * new HBase configuration, so it might make your unit tests fail due to
   * incorrect ZK client port.
   * @param tableName Name of table to check.
   * @return {@code true} if table is online.
   * @throws IOException if a remote or network exception occurs
   * @deprecated use {@link HBaseAdmin#isTableEnabled(byte[])}
   */
  @Deprecated
  public static boolean isTableEnabled(String tableName) throws IOException {
    return isTableEnabled(TableName.valueOf(tableName));
  }

  /**
   * <b>Return <code>true</code> for MapR Tables.</b><p>
   * Tells whether or not a table is enabled or not. This method creates a
   * new HBase configuration, so it might make your unit tests fail due to
   * incorrect ZK client port.
   * @param tableName Name of table to check.
   * @return {@code true} if table is online.
   * @throws IOException if a remote or network exception occurs
   * @deprecated use {@link HBaseAdmin#isTableEnabled(byte[])}
   */
  @Deprecated
  public static boolean isTableEnabled(byte[] tableName) throws IOException {
    return isTableEnabled(TableName.valueOf(tableName));
  }

  /**
   * <b>Return <code>true</code> for MapR Tables.</b><p>
   * Tells whether or not a table is enabled or not. This method creates a
   * new HBase configuration, so it might make your unit tests fail due to
   * incorrect ZK client port.
   * @param tableName Name of table to check.
   * @return {@code true} if table is online.
   * @throws IOException if a remote or network exception occurs
   * @deprecated use {@link HBaseAdmin#isTableEnabled(byte[])}
   */
  @Deprecated
  public static boolean isTableEnabled(TableName tableName) throws IOException {
    return isTableEnabled(HBaseConfiguration.create(), tableName);
  }

  /**
   * <b>Return <code>true</code> for MapR Tables.</b><p>
   * Tells whether or not a table is enabled or not.
   * @param conf The Configuration object to use.
   * @param tableName Name of table to check.
   * @return {@code true} if table is online.
   * @throws IOException if a remote or network exception occurs
   * @deprecated use {@link HBaseAdmin#isTableEnabled(byte[])}
   */
  @Deprecated
  public static boolean isTableEnabled(Configuration conf, String tableName)
  throws IOException {
    return isTableEnabled(conf, TableName.valueOf(tableName));
  }

  /**
   * <b>Return <code>true</code> for MapR Tables.</b><p>
   * Tells whether or not a table is enabled or not.
   * @param conf The Configuration object to use.
   * @param tableName Name of table to check.
   * @return {@code true} if table is online.
   * @throws IOException if a remote or network exception occurs
   * @deprecated use {@link HBaseAdmin#isTableEnabled(byte[])}
   */
  @Deprecated
  public static boolean isTableEnabled(Configuration conf, byte[] tableName)
  throws IOException {
    return isTableEnabled(conf, TableName.valueOf(tableName));
  }

  /**
   * <b>Return <code>true</code> for MapR Tables.</b><p>
   * Tells whether or not a table is enabled or not.
   * @param conf The Configuration object to use.
   * @param tableName Name of table to check.
   * @return {@code true} if table is online.
   * @throws IOException if a remote or network exception occurs
   * @deprecated use {@link HBaseAdmin#isTableEnabled(org.apache.hadoop.hbase.TableName tableName)}
   */
  @Deprecated
  public static boolean isTableEnabled(Configuration conf,
      final TableName tableName) throws IOException {
    if (TableMappingRulesFactory.create(conf).isMapRTable(tableName)) {
      return true;
    }
    return HConnectionManager.execute(new HConnectable<Boolean>(conf) {
      @Override
      public Boolean connect(HConnection connection) throws IOException {
        return connection.isTableEnabled(tableName);
      }
    });
  }

  /**
   * Find region location hosting passed row using cached info
   * @param row Row to find.
   * @return The location of the given row.
   * @throws IOException if a remote or network exception occurs
   * @deprecated Use {@link RegionLocator#getRegionLocation(byte[])}
   */
  @Deprecated
  public HRegionLocation getRegionLocation(final String row)
  throws IOException {
    if (maprTable_ != null) {
      return maprTable_.getRegionLocation(row);
    }
    return getRegionLocation(Bytes.toBytes(row), false);
  }

  /**
   * @deprecated Use {@link RegionLocator#getRegionLocation(byte[])} instead.
   */
  @Override
  @Deprecated
  public HRegionLocation getRegionLocation(final byte [] row)
  throws IOException {
    if (maprTable_ != null) {
      return maprTable_.getRegionLocation(row);
    }
    return locator.getRegionLocation(row);
  }

  /**
   * @deprecated Use {@link RegionLocator#getRegionLocation(byte[], boolean)} instead.
   */
  @Override
  @Deprecated
  public HRegionLocation getRegionLocation(final byte [] row, boolean reload)
  throws IOException {
    if (maprTable_ != null) {
      return maprTable_.getRegionLocation(row, reload);
    }
    return locator.getRegionLocation(row, reload);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte [] getTableName() {
    if (maprTable_ != null) {
      return maprTable_.getTableName();
    }
    return this.tableName.getName();
  }

  @Override
  public TableName getName() {
    if (maprTable_ != null) {
      return TableName.valueOf(maprTable_.getTableName());
    }
    return tableName;
  }

  /**
   * <b>Return <code>null</code> for MapR Tables.</b><p>
   * <em>INTERNAL</em> Used by unit tests and tools to do low-level
   * manipulations.
   * @return An HConnection instance.
   * @deprecated This method will be changed from public to package protected.
   */
  // TODO(tsuna): Remove this.  Unit tests shouldn't require public helpers.
  @Deprecated
  @VisibleForTesting
  public HConnection getConnection() {
    return this.connection;
  }

  /**
   * <b>NO-OP for MapR Tables.</b><p>
   * Gets the number of rows that a scanner will fetch at once.
   * <p>
   * The default value comes from {@code hbase.client.scanner.caching}.
   * @deprecated Use {@link Scan#setCaching(int)} and {@link Scan#getCaching()}
   */
  @Deprecated
  public int getScannerCaching() {
    return scannerCaching;
  }

  /**
   * Kept in 0.96 for backward compatibility
   * @deprecated  since 0.96. This is an internal buffer that should not be read nor write.
   */
  @Deprecated
  public List<Row> getWriteBuffer() {
    if (maprTable_ != null) {
      return null;
    }
    return mutator == null ? null : mutator.getWriteBuffer();
  }

  /**
   * <b>NO-OP for MapR Tables.</b><p>
   * Sets the number of rows that a scanner will fetch at once.
   * <p>
   * This will override the value specified by
   * {@code hbase.client.scanner.caching}.
   * Increasing this value will reduce the amount of work needed each time
   * {@code next()} is called on a scanner, at the expense of memory use
   * (since more rows will need to be maintained in memory by the scanners).
   * @param scannerCaching the number of rows a scanner will fetch at once.
   * @deprecated Use {@link Scan#setCaching(int)}
   */
  @Deprecated
  public void setScannerCaching(int scannerCaching) {
    this.scannerCaching = scannerCaching;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public HTableDescriptor getTableDescriptor() throws IOException {
    if (maprTable_ != null) {
      return maprTable_.getTableDescriptor();
    }
    // TODO: This is the same as HBaseAdmin.getTableDescriptor(). Only keep one.
    if (tableName == null) return null;
    if (tableName.equals(TableName.META_TABLE_NAME)) {
      return HTableDescriptor.META_TABLEDESC;
    }
    HTableDescriptor htd = executeMasterCallable(
      new MasterCallable<HTableDescriptor>(getConnection()) {
      @Override
      public HTableDescriptor call(int callTimeout) throws ServiceException {
        GetTableDescriptorsResponse htds;
        GetTableDescriptorsRequest req =
            RequestConverter.buildGetTableDescriptorsRequest(tableName);
        htds = master.getTableDescriptors(null, req);

        if (!htds.getTableSchemaList().isEmpty()) {
          return HTableDescriptor.convert(htds.getTableSchemaList().get(0));
        }
        return null;
      }
    });
    if (htd != null) {
      return new UnmodifyableHTableDescriptor(htd);
    }
    throw new TableNotFoundException(tableName.getNameAsString());
  }

  private <V> V executeMasterCallable(MasterCallable<V> callable) throws IOException {
    RpcRetryingCaller<V> caller = rpcCallerFactory.newCaller();
    try {
      return caller.callWithRetries(callable, operationTimeout);
    } finally {
      callable.close();
    }
  }

  /**
   * @deprecated Use {@link RegionLocator#getStartEndKeys()} instead;
   */
  @Override
  @Deprecated
  public byte [][] getStartKeys() throws IOException {
    if (maprTable_ != null) {
      return maprTable_.getStartKeys();
    }
    return locator.getStartKeys();
  }

  /**
   * @deprecated Use {@link RegionLocator#getEndKeys()} instead;
   */
  @Override
  @Deprecated
  public byte[][] getEndKeys() throws IOException {
    if (maprTable_ != null) {
      return maprTable_.getEndKeys();
    }
    return locator.getEndKeys();
  }

  /**
   * @deprecated Use {@link RegionLocator#getStartEndKeys()} instead;
   */
  @Override
  @Deprecated
  public Pair<byte[][],byte[][]> getStartEndKeys() throws IOException {
    if (maprTable_ != null) {
      return maprTable_.getStartEndKeys();
    }
    return locator.getStartEndKeys();
  }

  /**
   * Gets all the regions and their address for this table.
   * <p>
   * This is mainly useful for the MapReduce integration.
   * @return A map of HRegionInfo with it's server address
   * @throws IOException if a remote or network exception occurs
   * @deprecated This is no longer a public API.  Use {@link #getAllRegionLocations()} instead.
   */
  @Deprecated
  public NavigableMap<HRegionInfo, ServerName> getRegionLocations() throws IOException {
    // TODO: Odd that this returns a Map of HRI to SN whereas getRegionLocator, singular, returns an HRegionLocation.
    return MetaScanner.allTableRegions(this.connection, getName());
  }

  /**
   * Gets all the regions and their address for this table.
   * <p>
   * This is mainly useful for the MapReduce integration.
   * @return A map of HRegionInfo with it's server address
   * @throws IOException if a remote or network exception occurs
   *
   * @deprecated Use {@link RegionLocator#getAllRegionLocations()} instead;
   */
  @Override
  @Deprecated
  public List<HRegionLocation> getAllRegionLocations() throws IOException {
  //TODO: SU add support of this function in HTableImpl.java in mapr-hbase 
  //  if (maprTable_ != null) {
  //    return maprTable_.getRegionLocations();
  //  }
    return locator.getAllRegionLocations();
  }

  /**
   * Get the corresponding regions for an arbitrary range of keys.
   * <p>
   * @param startKey Starting row in range, inclusive
   * @param endKey Ending row in range, exclusive
   * @return A list of HRegionLocations corresponding to the regions that
   * contain the specified range
   * @throws IOException if a remote or network exception occurs
   * @deprecated This is no longer a public API
   */
  @Deprecated
  public List<HRegionLocation> getRegionsInRange(final byte [] startKey,
    final byte [] endKey) throws IOException {
    return getRegionsInRange(startKey, endKey, false);
  }

  /**
   * Get the corresponding regions for an arbitrary range of keys.
   * <p>
   * @param startKey Starting row in range, inclusive
   * @param endKey Ending row in range, exclusive
   * @param reload true to reload information or false to use cached information
   * @return A list of HRegionLocations corresponding to the regions that
   * contain the specified range
   * @throws IOException if a remote or network exception occurs
   * @deprecated This is no longer a public API
   */
  @Deprecated
  public List<HRegionLocation> getRegionsInRange(final byte [] startKey,
      final byte [] endKey, final boolean reload) throws IOException {
    return getKeysAndRegionsInRange(startKey, endKey, false, reload).getSecond();
  }

  /**
   * Get the corresponding start keys and regions for an arbitrary range of
   * keys.
   * <p>
   * @param startKey Starting row in range, inclusive
   * @param endKey Ending row in range
   * @param includeEndKey true if endRow is inclusive, false if exclusive
   * @return A pair of list of start keys and list of HRegionLocations that
   *         contain the specified range
   * @throws IOException if a remote or network exception occurs
   * @deprecated This is no longer a public API
   */
  @Deprecated
  private Pair<List<byte[]>, List<HRegionLocation>> getKeysAndRegionsInRange(
      final byte[] startKey, final byte[] endKey, final boolean includeEndKey)
      throws IOException {
    return getKeysAndRegionsInRange(startKey, endKey, includeEndKey, false);
  }

  /**
   * (TODO : nagrawal)
   * Get the corresponding start keys and regions for an arbitrary range of
   * keys.
   * <p>
   * @param startKey Starting row in range, inclusive
   * @param endKey Ending row in range
   * @param includeEndKey true if endRow is inclusive, false if exclusive
   * @param reload true to reload information or false to use cached information
   * @return A pair of list of start keys and list of HRegionLocations that
   *         contain the specified range
   * @throws IOException if a remote or network exception occurs
   * @deprecated This is no longer a public API
   */
  @Deprecated
  private Pair<List<byte[]>, List<HRegionLocation>> getKeysAndRegionsInRange(
      final byte[] startKey, final byte[] endKey, final boolean includeEndKey,
      final boolean reload) throws IOException {
    final boolean endKeyIsEndOfTable = Bytes.equals(endKey,HConstants.EMPTY_END_ROW);
    if ((Bytes.compareTo(startKey, endKey) > 0) && !endKeyIsEndOfTable) {
      throw new IllegalArgumentException(
        "Invalid range: " + Bytes.toStringBinary(startKey) +
        " > " + Bytes.toStringBinary(endKey));
    }
    List<byte[]> keysInRange = new ArrayList<byte[]>();
    List<HRegionLocation> regionsInRange = new ArrayList<HRegionLocation>();
    byte[] currentKey = startKey;
    do {
      HRegionLocation regionLocation = getRegionLocation(currentKey, reload);
      keysInRange.add(currentKey);
      regionsInRange.add(regionLocation);
      currentKey = regionLocation.getRegionInfo().getEndKey();
    } while (!Bytes.equals(currentKey, HConstants.EMPTY_END_ROW)
        && (endKeyIsEndOfTable || Bytes.compareTo(currentKey, endKey) < 0
            || (includeEndKey && Bytes.compareTo(currentKey, endKey) == 0)));
    return new Pair<List<byte[]>, List<HRegionLocation>>(keysInRange,
        regionsInRange);
  }

  /**
   * {@inheritDoc}
   * @deprecated Use reversed scan instead.
   */
   @Override
   @Deprecated
   public Result getRowOrBefore(final byte[] row, final byte[] family)
       throws IOException {
     if (maprTable_ != null) {
       return maprTable_.getRowOrBefore(row, family);
     }
     RegionServerCallable<Result> callable = new RegionServerCallable<Result>(this.connection,
         tableName, row) {
       @Override
      public Result call(int callTimeout) throws IOException {
         PayloadCarryingRpcController controller = rpcControllerFactory.newController();
         controller.setPriority(tableName);
         controller.setCallTimeout(callTimeout);
         ClientProtos.GetRequest request = RequestConverter.buildGetRowOrBeforeRequest(
             getLocation().getRegionInfo().getRegionName(), row, family);
         try {
           ClientProtos.GetResponse response = getStub().get(controller, request);
           if (!response.hasResult()) return null;
           return ProtobufUtil.toResult(response.getResult());
         } catch (ServiceException se) {
           throw ProtobufUtil.getRemoteException(se);
         }
       }
     };
     return rpcCallerFactory.<Result>newCaller().callWithRetries(callable, this.operationTimeout);
   }

  /**
   * The underlying {@link HTable} must not be closed.
   * (TODO : nagrawal) handle reverse scan.
   * {@link HTableInterface#getScanner(Scan)} has other usage details.
   */
  @Override
  public ResultScanner getScanner(final Scan scan) throws IOException {
    if (maprTable_ != null) {
      return maprTable_.getScanner(scan);
    }
    if (scan.getBatch() > 0 && scan.isSmall()) {
      throw new IllegalArgumentException("Small scan should not be used with batching");
    }

    if (scan.getCaching() <= 0) {
      scan.setCaching(getScannerCaching());
    }
    if (scan.getMaxResultSize() <= 0) {
      scan.setMaxResultSize(scannerMaxResultSize);
    }

    if (scan.isReversed()) {
      if (scan.isSmall()) {
        return new ClientSmallReversedScanner(getConfiguration(), scan, getName(),
            this.connection, this.rpcCallerFactory, this.rpcControllerFactory,
            pool, tableConfiguration.getReplicaCallTimeoutMicroSecondScan());
      } else {
        return new ReversedClientScanner(getConfiguration(), scan, getName(),
            this.connection, this.rpcCallerFactory, this.rpcControllerFactory,
            pool, tableConfiguration.getReplicaCallTimeoutMicroSecondScan());
      }
    }

    if (scan.isSmall()) {
      return new ClientSmallScanner(getConfiguration(), scan, getName(),
          this.connection, this.rpcCallerFactory, this.rpcControllerFactory,
          pool, tableConfiguration.getReplicaCallTimeoutMicroSecondScan());
    } else {
      return new ClientScanner(getConfiguration(), scan, getName(), this.connection,
          this.rpcCallerFactory, this.rpcControllerFactory,
          pool, tableConfiguration.getReplicaCallTimeoutMicroSecondScan());
    }
  }

  /**
   * The underlying {@link HTable} must not be closed.
   * {@link HTableInterface#getScanner(byte[])} has other usage details.
   */
  @Override
  public ResultScanner getScanner(byte [] family) throws IOException {
    Scan scan = new Scan();
    scan.addFamily(family);
    return getScanner(scan);
  }

  /**
   * The underlying {@link HTable} must not be closed.
   * {@link HTableInterface#getScanner(byte[], byte[])} has other usage details.
   */
  @Override
  public ResultScanner getScanner(byte [] family, byte [] qualifier)
  throws IOException {
    Scan scan = new Scan();
    scan.addColumn(family, qualifier);
    return getScanner(scan);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Result get(final Get get) throws IOException {
    if (maprTable_ != null) {
      return maprTable_.get(get);
    }
    return get(get, get.isCheckExistenceOnly());
  }

  private Result get(Get get, final boolean checkExistenceOnly) throws IOException {
    // if we are changing settings to the get, clone it.
    if (get.isCheckExistenceOnly() != checkExistenceOnly || get.getConsistency() == null) {
      get = ReflectionUtils.newInstance(get.getClass(), get);
      get.setCheckExistenceOnly(checkExistenceOnly);
      if (get.getConsistency() == null){
        get.setConsistency(defaultConsistency);
      }
    }

    if (get.getConsistency() == Consistency.STRONG) {
      // Good old call.
      final Get getReq = get;
      RegionServerCallable<Result> callable = new RegionServerCallable<Result>(this.connection,
          getName(), get.getRow()) {
        @Override
        public Result call(int callTimeout) throws IOException {
          ClientProtos.GetRequest request =
            RequestConverter.buildGetRequest(getLocation().getRegionInfo().getRegionName(), getReq);
          PayloadCarryingRpcController controller = rpcControllerFactory.newController();
          controller.setPriority(tableName);
          controller.setCallTimeout(callTimeout);
          try {
            ClientProtos.GetResponse response = getStub().get(controller, request);
            if (response == null) return null;
            return ProtobufUtil.toResult(response.getResult());
          } catch (ServiceException se) {
            throw ProtobufUtil.getRemoteException(se);
          }
        }
      };
      return rpcCallerFactory.<Result>newCaller().callWithRetries(callable, this.operationTimeout);
    }

    // Call that takes into account the replica
    RpcRetryingCallerWithReadReplicas callable = new RpcRetryingCallerWithReadReplicas(
      rpcControllerFactory, tableName, this.connection, get, pool,
      tableConfiguration.getRetriesNumber(),
      operationTimeout,
      tableConfiguration.getPrimaryCallTimeoutMicroSecond());
    return callable.call();
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public Result[] get(List<Get> gets) throws IOException {
    if (maprTable_ != null) {
      return maprTable_.get(gets);
    }
    if (gets.size() == 1) {
      return new Result[]{get(gets.get(0))};
    }
    try {
      Object [] r1 = batch((List)gets);

      // translate.
      Result [] results = new Result[r1.length];
      int i=0;
      for (Object o : r1) {
        // batch ensures if there is a failure we get an exception instead
        results[i++] = (Result) o;
      }

      return results;
    } catch (InterruptedException e) {
      throw (InterruptedIOException)new InterruptedIOException().initCause(e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void batch(final List<? extends Row> actions, final Object[] results)
      throws InterruptedException, IOException {
    AsyncRequestFuture ars = multiAp.submitAll(pool, tableName, actions, null, results);
    ars.waitUntilDone();
    if (ars.hasError()) {
      throw ars.getErrors();
    }
  }

  /**
   * {@inheritDoc}
   * @deprecated If any exception is thrown by one of the actions, there is no way to
   * retrieve the partially executed results. Use {@link #batch(List, Object[])} instead.
   */
  @Deprecated
  @Override
  public Object[] batch(final List<? extends Row> actions)
     throws InterruptedException, IOException {
    Object[] results = new Object[actions.size()];
    batch(actions, results);
    return results;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <R> void batchCallback(
      final List<? extends Row> actions, final Object[] results, final Batch.Callback<R> callback)
      throws IOException, InterruptedException {
    if (maprTable_ != null) {
      //TODO(nagrawal): callback is ignored.
      maprTable_.batch(actions, results);
      return;
    }
    connection.processBatchCallback(actions, tableName, pool, results, callback);
  }

  /**
   * {@inheritDoc}
   * @deprecated If any exception is thrown by one of the actions, there is no way to
   * retrieve the partially executed results. Use
   * {@link #batchCallback(List, Object[], org.apache.hadoop.hbase.client.coprocessor.Batch.Callback)}
   * instead.
   */
  @Deprecated
  @Override
  public <R> Object[] batchCallback(
    final List<? extends Row> actions, final Batch.Callback<R> callback) throws IOException,
      InterruptedException {
    if (maprTable_ != null) {
      //TODO(nagrawal): callback is ignored.
      return maprTable_.batch(actions);
    }
    Object[] results = new Object[actions.size()];
    batchCallback(actions, results, callback);
    return results;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void delete(final Delete delete)
  throws IOException {
    if (maprTable_ != null) {
      maprTable_.delete(delete);
      return;
    }
    RegionServerCallable<Boolean> callable = new RegionServerCallable<Boolean>(connection,
        tableName, delete.getRow()) {
      @Override
      public Boolean call(int callTimeout) throws IOException {
        PayloadCarryingRpcController controller = rpcControllerFactory.newController();
        controller.setPriority(tableName);
        controller.setCallTimeout(callTimeout);

        try {
          MutateRequest request = RequestConverter.buildMutateRequest(
            getLocation().getRegionInfo().getRegionName(), delete);
          MutateResponse response = getStub().mutate(controller, request);
          return Boolean.valueOf(response.getProcessed());
        } catch (ServiceException se) {
          throw ProtobufUtil.getRemoteException(se);
        }
      }
    };
    rpcCallerFactory.<Boolean> newCaller().callWithRetries(callable, this.operationTimeout);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void delete(final List<Delete> deletes)
  throws IOException {
    if (maprTable_ != null) {
      maprTable_.delete(deletes);
      return;
    }
    Object[] results = new Object[deletes.size()];
    try {
      batch(deletes, results);
    } catch (InterruptedException e) {
      throw (InterruptedIOException)new InterruptedIOException().initCause(e);
    } finally {
      // mutate list so that it is empty for complete success, or contains only failed records
      // results are returned in the same order as the requests in list
      // walk the list backwards, so we can remove from list without impacting the indexes of earlier members
      for (int i = results.length - 1; i>=0; i--) {
        // if result is not null, it succeeded
        if (results[i] instanceof Result) {
          deletes.remove(i);
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   * @throws IOException
   */
  @Override
  public void put(final Put put) throws IOException {
    getBufferedMutator().mutate(put);
    if (maprTable_ != null) {
      maprTable_.put(put);
      return;
    }
    if (autoFlush) {
      flushCommits();
    }
  }

  /**
   * {@inheritDoc}
   * @throws IOException
   */
  @Override
  public void put(final List<Put> puts) throws IOException {
    getBufferedMutator().mutate(puts);
    if (maprTable_ != null) {
      maprTable_.put(puts);
      return;
    }
    if (autoFlush) {
      flushCommits();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void mutateRow(final RowMutations rm) throws IOException {
    if (maprTable_ != null) {
      maprTable_.mutateRow(rm);
      return;
    }
    RegionServerCallable<Void> callable =
        new RegionServerCallable<Void>(connection, getName(), rm.getRow()) {
      @Override
      public Void call(int callTimeout) throws IOException {
        PayloadCarryingRpcController controller = rpcControllerFactory.newController();
        controller.setPriority(tableName);
        controller.setCallTimeout(callTimeout);
        try {
          RegionAction.Builder regionMutationBuilder = RequestConverter.buildRegionAction(
            getLocation().getRegionInfo().getRegionName(), rm);
          regionMutationBuilder.setAtomic(true);
          MultiRequest request =
            MultiRequest.newBuilder().addRegionAction(regionMutationBuilder.build()).build();
          ClientProtos.MultiResponse response = getStub().multi(controller, request);
          ClientProtos.RegionActionResult res = response.getRegionActionResultList().get(0);
          if (res.hasException()) {
            Throwable ex = ProtobufUtil.toException(res.getException());
            if(ex instanceof IOException) {
              throw (IOException)ex;
            }
            throw new IOException("Failed to mutate row: "+Bytes.toStringBinary(rm.getRow()), ex);
          }
        } catch (ServiceException se) {
          throw ProtobufUtil.getRemoteException(se);
        }
        return null;
      }
    };
    rpcCallerFactory.<Void> newCaller().callWithRetries(callable, this.operationTimeout);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Result append(final Append append) throws IOException {
    if (append.numFamilies() == 0) {
      throw new IOException(
          "Invalid arguments to append, no columns specified");
    }
    if (maprTable_ != null) {
      return maprTable_.append(append);
    }

    NonceGenerator ng = this.connection.getNonceGenerator();
    final long nonceGroup = ng.getNonceGroup(), nonce = ng.newNonce();
    RegionServerCallable<Result> callable =
      new RegionServerCallable<Result>(this.connection, getName(), append.getRow()) {
        @Override
        public Result call(int callTimeout) throws IOException {
          PayloadCarryingRpcController controller = rpcControllerFactory.newController();
          controller.setPriority(getTableName());
          controller.setCallTimeout(callTimeout);
          try {
            MutateRequest request = RequestConverter.buildMutateRequest(
              getLocation().getRegionInfo().getRegionName(), append, nonceGroup, nonce);
            MutateResponse response = getStub().mutate(controller, request);
            if (!response.hasResult()) return null;
            return ProtobufUtil.toResult(response.getResult(), controller.cellScanner());
          } catch (ServiceException se) {
            throw ProtobufUtil.getRemoteException(se);
          }
        }
      };
    return rpcCallerFactory.<Result> newCaller().callWithRetries(callable, this.operationTimeout);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Result increment(final Increment increment) throws IOException {
    if (!increment.hasFamilies()) {
      throw new IOException(
          "Invalid arguments to increment, no columns specified");
    }
    if (maprTable_ != null) {
      return maprTable_.increment(increment);
    }
    NonceGenerator ng = this.connection.getNonceGenerator();
    final long nonceGroup = ng.getNonceGroup(), nonce = ng.newNonce();
    RegionServerCallable<Result> callable = new RegionServerCallable<Result>(this.connection,
        getName(), increment.getRow()) {
      @Override
      public Result call(int callTimeout) throws IOException {
        PayloadCarryingRpcController controller = rpcControllerFactory.newController();
        controller.setPriority(getTableName());
        controller.setCallTimeout(callTimeout);
        try {
          MutateRequest request = RequestConverter.buildMutateRequest(
            getLocation().getRegionInfo().getRegionName(), increment, nonceGroup, nonce);
          MutateResponse response = getStub().mutate(controller, request);
          return ProtobufUtil.toResult(response.getResult(), controller.cellScanner());
        } catch (ServiceException se) {
          throw ProtobufUtil.getRemoteException(se);
        }
      }
    };
    return rpcCallerFactory.<Result> newCaller().callWithRetries(callable, this.operationTimeout);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long incrementColumnValue(final byte [] row, final byte [] family,
      final byte [] qualifier, final long amount)
  throws IOException {
    return incrementColumnValue(row, family, qualifier, amount, Durability.SYNC_WAL);
  }

  /**
   * @deprecated As of release 0.96
   *             (<a href="https://issues.apache.org/jira/browse/HBASE-9508">HBASE-9508</a>).
   *             This will be removed in HBase 2.0.0.
   *             Use {@link #incrementColumnValue(byte[], byte[], byte[], long, Durability)}.
   */
  @Deprecated
  @Override
  public long incrementColumnValue(final byte [] row, final byte [] family,
      final byte [] qualifier, final long amount, final boolean writeToWAL)
  throws IOException {
    return incrementColumnValue(row, family, qualifier, amount,
      writeToWAL? Durability.SKIP_WAL: Durability.USE_DEFAULT);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long incrementColumnValue(final byte [] row, final byte [] family,
      final byte [] qualifier, final long amount, final Durability durability)
  throws IOException {
    NullPointerException npe = null;
    if (row == null) {
      npe = new NullPointerException("row is null");
    } else if (family == null) {
      npe = new NullPointerException("family is null");
    } else if (qualifier == null) {
      npe = new NullPointerException("qualifier is null");
    }
    if (npe != null) {
      throw new IOException(
          "Invalid arguments to incrementColumnValue", npe);
    }
    if (maprTable_ != null) {
      return maprTable_.incrementColumnValue(row, family, qualifier, amount,
        durability);
    }

    NonceGenerator ng = this.connection.getNonceGenerator();
    final long nonceGroup = ng.getNonceGroup(), nonce = ng.newNonce();
    RegionServerCallable<Long> callable =
      new RegionServerCallable<Long>(connection, getName(), row) {
        @Override
        public Long call(int callTimeout) throws IOException {
          PayloadCarryingRpcController controller = rpcControllerFactory.newController();
          controller.setPriority(getTableName());
          controller.setCallTimeout(callTimeout);
          try {
            MutateRequest request = RequestConverter.buildIncrementRequest(
              getLocation().getRegionInfo().getRegionName(), row, family,
              qualifier, amount, durability, nonceGroup, nonce);
            MutateResponse response = getStub().mutate(controller, request);
            Result result =
              ProtobufUtil.toResult(response.getResult(), controller.cellScanner());
            return Long.valueOf(Bytes.toLong(result.getValue(family, qualifier)));
          } catch (ServiceException se) {
            throw ProtobufUtil.getRemoteException(se);
          }
        }
      };
    return rpcCallerFactory.<Long> newCaller().callWithRetries(callable, this.operationTimeout);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean checkAndPut(final byte [] row,
      final byte [] family, final byte [] qualifier, final byte [] value,
      final Put put)
  throws IOException {
    if (maprTable_ != null) {
      return maprTable_.checkAndPut(row, family, qualifier, value, put);
    }
    RegionServerCallable<Boolean> callable =
      new RegionServerCallable<Boolean>(connection, getName(), row) {
        @Override
        public Boolean call(int callTimeout) throws IOException {
          PayloadCarryingRpcController controller = rpcControllerFactory.newController();
          controller.setPriority(tableName);
          controller.setCallTimeout(callTimeout);
          try {
            MutateRequest request = RequestConverter.buildMutateRequest(
                getLocation().getRegionInfo().getRegionName(), row, family, qualifier,
                new BinaryComparator(value), CompareType.EQUAL, put);
            MutateResponse response = getStub().mutate(controller, request);
            return Boolean.valueOf(response.getProcessed());
          } catch (ServiceException se) {
            throw ProtobufUtil.getRemoteException(se);
          }
        }
      };
    return rpcCallerFactory.<Boolean> newCaller().callWithRetries(callable, this.operationTimeout);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean checkAndPut(final byte [] row, final byte [] family,
      final byte [] qualifier, final CompareOp compareOp, final byte [] value,
      final Put put)
  throws IOException {
    RegionServerCallable<Boolean> callable =
      new RegionServerCallable<Boolean>(connection, getName(), row) {
        @Override
        public Boolean call(int callTimeout) throws IOException {
          PayloadCarryingRpcController controller = new PayloadCarryingRpcController();
          controller.setPriority(tableName);
          controller.setCallTimeout(callTimeout);
          try {
            CompareType compareType = CompareType.valueOf(compareOp.name());
            MutateRequest request = RequestConverter.buildMutateRequest(
              getLocation().getRegionInfo().getRegionName(), row, family, qualifier,
                new BinaryComparator(value), compareType, put);
            MutateResponse response = getStub().mutate(controller, request);
            return Boolean.valueOf(response.getProcessed());
          } catch (ServiceException se) {
            throw ProtobufUtil.getRemoteException(se);
          }
        }
      };
    return rpcCallerFactory.<Boolean> newCaller().callWithRetries(callable, this.operationTimeout);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean checkAndDelete(final byte [] row,
      final byte [] family, final byte [] qualifier, final byte [] value,
      final Delete delete)
  throws IOException {
    if (maprTable_ != null) {
      return maprTable_.checkAndDelete(row, family, qualifier, value, delete);
    }
    RegionServerCallable<Boolean> callable =
      new RegionServerCallable<Boolean>(connection, getName(), row) {
        @Override
        public Boolean call(int callTimeout) throws IOException {
          PayloadCarryingRpcController controller = rpcControllerFactory.newController();
          controller.setPriority(tableName);
          controller.setCallTimeout(callTimeout);
          try {
            MutateRequest request = RequestConverter.buildMutateRequest(
              getLocation().getRegionInfo().getRegionName(), row, family, qualifier,
                new BinaryComparator(value), CompareType.EQUAL, delete);
            MutateResponse response = getStub().mutate(controller, request);
            return Boolean.valueOf(response.getProcessed());
          } catch (ServiceException se) {
            throw ProtobufUtil.getRemoteException(se);
          }
        }
      };
    return rpcCallerFactory.<Boolean> newCaller().callWithRetries(callable, this.operationTimeout);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean checkAndDelete(final byte [] row, final byte [] family,
      final byte [] qualifier, final CompareOp compareOp, final byte [] value,
      final Delete delete)
  throws IOException {
    RegionServerCallable<Boolean> callable =
      new RegionServerCallable<Boolean>(connection, getName(), row) {
        @Override
        public Boolean call(int callTimeout) throws IOException {
          PayloadCarryingRpcController controller = rpcControllerFactory.newController();
          controller.setPriority(tableName);
          controller.setCallTimeout(callTimeout);
          try {
            CompareType compareType = CompareType.valueOf(compareOp.name());
            MutateRequest request = RequestConverter.buildMutateRequest(
              getLocation().getRegionInfo().getRegionName(), row, family, qualifier,
                new BinaryComparator(value), compareType, delete);
            MutateResponse response = getStub().mutate(controller, request);
            return Boolean.valueOf(response.getProcessed());
          } catch (ServiceException se) {
            throw ProtobufUtil.getRemoteException(se);
          }
        }
      };
    return rpcCallerFactory.<Boolean> newCaller().callWithRetries(callable, this.operationTimeout);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean checkAndMutate(final byte [] row, final byte [] family, final byte [] qualifier,
      final CompareOp compareOp, final byte [] value, final RowMutations rm)
  throws IOException {
    RegionServerCallable<Boolean> callable =
        new RegionServerCallable<Boolean>(connection, getName(), row) {
          @Override
          public Boolean call(int callTimeout) throws IOException {
            PayloadCarryingRpcController controller = rpcControllerFactory.newController();
            controller.setPriority(tableName);
            controller.setCallTimeout(callTimeout);
            try {
              CompareType compareType = CompareType.valueOf(compareOp.name());
              MultiRequest request = RequestConverter.buildMutateRequest(
                  getLocation().getRegionInfo().getRegionName(), row, family, qualifier,
                  new BinaryComparator(value), compareType, rm);
              ClientProtos.MultiResponse response = getStub().multi(controller, request);
              ClientProtos.RegionActionResult res = response.getRegionActionResultList().get(0);
              if (res.hasException()) {
                Throwable ex = ProtobufUtil.toException(res.getException());
                if(ex instanceof IOException) {
                  throw (IOException)ex;
                }
                throw new IOException("Failed to checkAndMutate row: "+
                    Bytes.toStringBinary(rm.getRow()), ex);
              }
              return Boolean.valueOf(response.getProcessed());
            } catch (ServiceException se) {
              throw ProtobufUtil.getRemoteException(se);
            }
          }
        };
    return rpcCallerFactory.<Boolean> newCaller().callWithRetries(callable, this.operationTimeout);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean exists(final Get get) throws IOException {
    if (maprTable_ != null) {
      return maprTable_.exists(get);
    }
    Result r = get(get, true);
    assert r.getExists() != null;
    return r.getExists();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean[] existsAll(final List<Get> gets) throws IOException {
    if (gets.isEmpty()) return new boolean[]{};
    if (gets.size() == 1) return new boolean[]{exists(gets.get(0))};

    //TODO: change maprTable_exists to return boolean[] 
    if (maprTable_ != null) {
      Boolean[] objectResults = maprTable_.exists(gets);
      boolean[] results = new boolean[objectResults.length];
      for (int i = 0; i < objectResults.length; ++i) {
          results[i] = objectResults[i].booleanValue();
        }
      return results;
    }

    ArrayList<Get> exists = new ArrayList<Get>(gets.size());
    for (Get g: gets){
      Get ge = new Get(g);
      ge.setCheckExistenceOnly(true);
      exists.add(ge);
    }

    Object[] r1;
    try {
      r1 = batch(exists);
    } catch (InterruptedException e) {
      throw (InterruptedIOException)new InterruptedIOException().initCause(e);
    }

    // translate.
    boolean[] results = new boolean[r1.length];
    int i = 0;
    for (Object o : r1) {
      // batch ensures if there is a failure we get an exception instead
      results[i++] = ((Result)o).getExists();
    }

    return results;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Deprecated
  public Boolean[] exists(final List<Get> gets) throws IOException {
    boolean[] results = existsAll(gets);
    Boolean[] objectResults = new Boolean[results.length];
    for (int i = 0; i < results.length; ++i) {
      objectResults[i] = results[i];
    }
    return objectResults;
  }

  /**
   * {@inheritDoc}
   * @throws IOException
   */
  @Override
  public void flushCommits() throws IOException {
    if (mutator == null) {
      // nothing to flush if there's no mutator; don't bother creating one.
      return;
    }
    getBufferedMutator().flush();
  }

  /**
   * Process a mixed batch of Get, Put and Delete actions. All actions for a
   * RegionServer are forwarded in one RPC call. Queries are executed in parallel.
   *
   * @param list The collection of actions.
   * @param results An empty array, same size as list. If an exception is thrown,
   * you can test here for partial results, and to determine which actions
   * processed successfully.
   * @throws IOException if there are problems talking to META. Per-item
   * exceptions are stored in the results array.
   */
  public <R> void processBatchCallback(
    final List<? extends Row> list, final Object[] results, final Batch.Callback<R> callback)
    throws IOException, InterruptedException {
    this.batchCallback(list, results, callback);
  }


  /**
   * Parameterized batch processing, allowing varying return types for different
   * {@link Row} implementations.
   */
  public void processBatch(final List<? extends Row> list, final Object[] results)
    throws IOException, InterruptedException {
    this.batch(list, results);
  }


  @Override
  public void close() throws IOException {
    if (maprTable_ != null) {
      maprTable_.close();
      return;
    }
    if (this.closed) {
      return;
    }
    flushCommits();
    if (cleanupPoolOnClose) {
      this.pool.shutdown();
      try {
        boolean terminated = false;
        do {
          // wait until the pool has terminated
          terminated = this.pool.awaitTermination(60, TimeUnit.SECONDS);
        } while (!terminated);
      } catch (InterruptedException e) {
        this.pool.shutdownNow();
        LOG.warn("waitForTermination interrupted");
      }
    }
    if (cleanupConnectionOnClose) {
      if (this.connection != null) {
        this.connection.close();
      }
    }
    this.closed = true;
  }

  // validate for well-formedness
  public void validatePut(final Put put) throws IllegalArgumentException {
    validatePut(put, tableConfiguration.getMaxKeyValueSize());
  }

  // validate for well-formedness
  public static void validatePut(Put put, int maxKeyValueSize) throws IllegalArgumentException {
    if (put.isEmpty()) {
      throw new IllegalArgumentException("No columns to insert");
    }
    if (maxKeyValueSize > 0) {
      for (List<Cell> list : put.getFamilyCellMap().values()) {
        for (Cell cell : list) {
          if (KeyValueUtil.length(cell) > maxKeyValueSize) {
            throw new IllegalArgumentException("KeyValue size too large");
          }
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAutoFlush() {
    if (maprTable_ != null) {
      return maprTable_.isAutoFlush();
    }
    return autoFlush;
  }

  /**
   * {@inheritDoc}
   */
  @Deprecated
  @Override
  public void setAutoFlush(boolean autoFlush) {
    this.autoFlush = autoFlush;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setAutoFlushTo(boolean autoFlush) {
    this.autoFlush = autoFlush;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setAutoFlush(boolean autoFlush, boolean clearBufferOnFail) {
    if (maprTable_ != null) {
      maprTable_.setAutoFlush(autoFlush, clearBufferOnFail);
      return;
    }
    this.autoFlush = autoFlush;
  }

  /**
   * <b>Return <code>0</code> for MapR Tables.</b><p>
   * Returns the maximum size in bytes of the write buffer for this HTable.
   * <p>
   * The default value comes from the configuration parameter
   * {@code hbase.client.write.buffer}.
   * @return The size of the write buffer in bytes.
   */
  @Override
  public long getWriteBufferSize() {
    if (maprTable_ != null) {
      return maprTable_.getWriteBufferSize();
    }
    if (mutator == null) {
      return tableConfiguration.getWriteBufferSize();
    } else {
      return mutator.getWriteBufferSize();
    }
  }

  /**
   * <b>NO-OP for MapR Tables.</b><p>
   * Sets the size of the buffer in bytes.
   * <p>
   * If the new size is less than the current amount of data in the
   * write buffer, the buffer gets flushed.
   * @param writeBufferSize The new write buffer size, in bytes.
   * @throws IOException if a remote or network exception occurs.
   */
  @Override
  public void setWriteBufferSize(long writeBufferSize) throws IOException {
    if (maprTable_ != null) {
      maprTable_.setWriteBufferSize(writeBufferSize);
      return;
    }
    getBufferedMutator();
    mutator.setWriteBufferSize(writeBufferSize);
  }

  /**
   * <b>Return <code>null</code> for MapR Tables.</b><p>
   * The pool is used for mutli requests for this HTable
   * @return the pool used for mutli
   */
  ExecutorService getPool() {
    if (maprTable_ != null) {
      return null;
    }
    return this.pool;
  }

  /**
   * <b>NO-OP for MapR Tables.</b><p>
   * Enable or disable region cache prefetch for the table. It will be
   * applied for the given table's all HTable instances who share the same
   * connection. By default, the cache prefetch is enabled.
   * @param tableName name of table to configure.
   * @param enable Set to true to enable region cache prefetch. Or set to
   * false to disable it.
   * @throws IOException
   * @deprecated does nothing since 0.99
   */
  @Deprecated
  public static void setRegionCachePrefetch(final byte[] tableName,
      final boolean enable)  throws IOException {
  }

  /**
   * @deprecated does nothing since 0.99
   */
  @Deprecated
  public static void setRegionCachePrefetch(
      final TableName tableName,
      final boolean enable) throws IOException {
    if (TableMappingRulesFactory.create(
      HBaseConfiguration.create()).isMapRTable(tableName)) {
      return;
    }
  }

  /**
   * <b>NO-OP for MapR Tables.</b><p>
   * Enable or disable region cache prefetch for the table. It will be
   * applied for the given table's all HTable instances who share the same
   * connection. By default, the cache prefetch is enabled.
   * @param conf The Configuration object to use.
   * @param tableName name of table to configure.
   * @param enable Set to true to enable region cache prefetch. Or set to
   * false to disable it.
   * @throws IOException
   * @deprecated does nothing since 0.99
   */
  @Deprecated
  public static void setRegionCachePrefetch(final Configuration conf,
      final byte[] tableName, final boolean enable) throws IOException {
  }

  /**
   * @deprecated does nothing since 0.99
   */
  @Deprecated
  public static void setRegionCachePrefetch(final Configuration conf,
      final TableName tableName,
      final boolean enable) throws IOException {
    if (TableMappingRulesFactory.create(
      HBaseConfiguration.create()).isMapRTable(tableName)) {
      return;
    }
  }

  /**
   * <b>Return <code>false</code> for MapR Tables.</b><p>
   * Check whether region cache prefetch is enabled or not for the table.
   * @param conf The Configuration object to use.
   * @param tableName name of table to check
   * @return true if table's region cache prefecth is enabled. Otherwise
   * it is disabled.
   * @throws IOException
   * @deprecated always return false since 0.99
   */
  @Deprecated
  public static boolean getRegionCachePrefetch(final Configuration conf,
      final byte[] tableName) throws IOException {
    return false;
  }

  /**
   * @deprecated always return false since 0.99
   */
  @Deprecated
  public static boolean getRegionCachePrefetch(final Configuration conf,
      final TableName tableName) throws IOException {
    return false;
  }

  /**
   * <b>Return <code>false</code> for MapR Tables.</b><p>
   * Check whether region cache prefetch is enabled or not for the table.
   * @param tableName name of table to check
   * @return true if table's region cache prefecth is enabled. Otherwise
   * it is disabled.
   * @throws IOException
   * @deprecated always return false since 0.99
   */
  @Deprecated
  public static boolean getRegionCachePrefetch(final byte[] tableName) throws IOException {
    return false;
  }

  /**
   * @deprecated always return false since 0.99
   */
  @Deprecated
  public static boolean getRegionCachePrefetch(
      final TableName tableName) throws IOException {
    return false;
  }

  /**
   * <b>NO-OP for MapR Tables</b><p>
   * Explicitly clears the region cache to fetch the latest value from META.
   * This is a power user function: avoid unless you know the ramifications.
   */
  public void clearRegionCache() {
    if (maprTable_ != null) {
      maprTable_.clearRegionCache();
      return;
    }
    this.connection.clearRegionCache();
  }

  /**
   * <b>NO-OP for MapR Tables, returns <code>null</code>.</b><p>
   * {@inheritDoc}
   */
  @Override
  public CoprocessorRpcChannel coprocessorService(byte[] row) {
    if (maprTable_ != null) {
      return null;
    }
    return new RegionCoprocessorRpcChannel(connection, tableName, row);
  }

  /**
   * <b>NO-OP for MapR Tables, returns <code>null</code>.</b><p>
   * {@inheritDoc}
   */
  @Override
  public <T extends Service, R> Map<byte[],R> coprocessorService(final Class<T> service,
      byte[] startKey, byte[] endKey, final Batch.Call<T,R> callable)
      throws ServiceException, Throwable {
    if (maprTable_ != null) {
      return null;
    }
    final Map<byte[],R> results =  Collections.synchronizedMap(
        new TreeMap<byte[], R>(Bytes.BYTES_COMPARATOR));
    coprocessorService(service, startKey, endKey, callable, new Batch.Callback<R>() {
      @Override
      public void update(byte[] region, byte[] row, R value) {
        if (region != null) {
          results.put(region, value);
        }
      }
    });
    return results;
  }

  /**
   * <b>NO-OP for MapR Tables</b><p>
   * {@inheritDoc}
   */
  @Override
  public <T extends Service, R> void coprocessorService(final Class<T> service,
      byte[] startKey, byte[] endKey, final Batch.Call<T,R> callable,
      final Batch.Callback<R> callback) throws ServiceException, Throwable {
    if (maprTable_ != null) {
      return;
    }

    // get regions covered by the row range
    List<byte[]> keys = getStartKeysInRange(startKey, endKey);

    Map<byte[],Future<R>> futures =
        new TreeMap<byte[],Future<R>>(Bytes.BYTES_COMPARATOR);
    for (final byte[] r : keys) {
      final RegionCoprocessorRpcChannel channel =
          new RegionCoprocessorRpcChannel(connection, tableName, r);
      Future<R> future = pool.submit(
          new Callable<R>() {
            @Override
            public R call() throws Exception {
              T instance = ProtobufUtil.newServiceStub(service, channel);
              R result = callable.call(instance);
              byte[] region = channel.getLastRegion();
              if (callback != null) {
                callback.update(region, r, result);
              }
              return result;
            }
          });
      futures.put(r, future);
    }
    for (Map.Entry<byte[],Future<R>> e : futures.entrySet()) {
      try {
        e.getValue().get();
      } catch (ExecutionException ee) {
        LOG.warn("Error calling coprocessor service " + service.getName() + " for row "
            + Bytes.toStringBinary(e.getKey()), ee);
        throw ee.getCause();
      } catch (InterruptedException ie) {
        throw new InterruptedIOException("Interrupted calling coprocessor service " + service.getName()
            + " for row " + Bytes.toStringBinary(e.getKey()))
            .initCause(ie);
      }
    }
  }

  private List<byte[]> getStartKeysInRange(byte[] start, byte[] end)
  throws IOException {
    if (start == null) {
      start = HConstants.EMPTY_START_ROW;
    }
    if (end == null) {
      end = HConstants.EMPTY_END_ROW;
    }
    return getKeysAndRegionsInRange(start, end, true).getFirst();
  }

  /**
   * <b>NO-OP for MapR Tables.</b><p>
   * @param operationTimeout
   */
  public void setOperationTimeout(int operationTimeout) {
    if (maprTable_ != null) {
      maprTable_.setOperationTimeout(operationTimeout);
      return;
    }
    this.operationTimeout = operationTimeout;
  }

  /**
   * <b>Returns <code>0</code> for MapR Tables.</b><p>
   */
  public int getOperationTimeout() {
    if (maprTable_ != null) {
      return maprTable_.getOperationTimeout();
    }
    return operationTimeout;
  }

  @Override
  public String toString() {
    return getName() + ";" + connection;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <R extends Message> Map<byte[], R> batchCoprocessorService(
      Descriptors.MethodDescriptor methodDescriptor, Message request,
      byte[] startKey, byte[] endKey, R responsePrototype) throws ServiceException, Throwable {
    final Map<byte[], R> results = Collections.synchronizedMap(new TreeMap<byte[], R>(
        Bytes.BYTES_COMPARATOR));
    batchCoprocessorService(methodDescriptor, request, startKey, endKey, responsePrototype,
        new Callback<R>() {

          @Override
          public void update(byte[] region, byte[] row, R result) {
            if (region != null) {
              results.put(region, result);
            }
          }
        });
    return results;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <R extends Message> void batchCoprocessorService(
      final Descriptors.MethodDescriptor methodDescriptor, final Message request,
      byte[] startKey, byte[] endKey, final R responsePrototype, final Callback<R> callback)
      throws ServiceException, Throwable {

    if (startKey == null) {
      startKey = HConstants.EMPTY_START_ROW;
    }
    if (endKey == null) {
      endKey = HConstants.EMPTY_END_ROW;
    }
    // get regions covered by the row range
    Pair<List<byte[]>, List<HRegionLocation>> keysAndRegions =
        getKeysAndRegionsInRange(startKey, endKey, true);
    List<byte[]> keys = keysAndRegions.getFirst();
    List<HRegionLocation> regions = keysAndRegions.getSecond();

    // check if we have any calls to make
    if (keys.isEmpty()) {
      LOG.info("No regions were selected by key range start=" + Bytes.toStringBinary(startKey) +
          ", end=" + Bytes.toStringBinary(endKey));
      return;
    }

    List<RegionCoprocessorServiceExec> execs = new ArrayList<RegionCoprocessorServiceExec>();
    final Map<byte[], RegionCoprocessorServiceExec> execsByRow =
        new TreeMap<byte[], RegionCoprocessorServiceExec>(Bytes.BYTES_COMPARATOR);
    for (int i = 0; i < keys.size(); i++) {
      final byte[] rowKey = keys.get(i);
      final byte[] region = regions.get(i).getRegionInfo().getRegionName();
      RegionCoprocessorServiceExec exec =
          new RegionCoprocessorServiceExec(region, rowKey, methodDescriptor, request);
      execs.add(exec);
      execsByRow.put(rowKey, exec);
    }

    // tracking for any possible deserialization errors on success callback
    // TODO: it would be better to be able to reuse AsyncProcess.BatchErrors here
    final List<Throwable> callbackErrorExceptions = new ArrayList<Throwable>();
    final List<Row> callbackErrorActions = new ArrayList<Row>();
    final List<String> callbackErrorServers = new ArrayList<String>();
    Object[] results = new Object[execs.size()];

    AsyncProcess asyncProcess =
        new AsyncProcess(connection, configuration, pool,
            RpcRetryingCallerFactory.instantiate(configuration, connection.getStatisticsTracker()),
            true, RpcControllerFactory.instantiate(configuration));

    AsyncRequestFuture future = asyncProcess.submitAll(tableName, execs,
        new Callback<ClientProtos.CoprocessorServiceResult>() {
          @Override
          public void update(byte[] region, byte[] row,
                              ClientProtos.CoprocessorServiceResult serviceResult) {
            if (LOG.isTraceEnabled()) {
              LOG.trace("Received result for endpoint " + methodDescriptor.getFullName() +
                  ": region=" + Bytes.toStringBinary(region) +
                  ", row=" + Bytes.toStringBinary(row) +
                  ", value=" + serviceResult.getValue().getValue());
            }
            try {
              callback.update(region, row,
                  (R) responsePrototype.newBuilderForType().mergeFrom(
                      serviceResult.getValue().getValue()).build());
            } catch (InvalidProtocolBufferException e) {
              LOG.error("Unexpected response type from endpoint " + methodDescriptor.getFullName(),
                  e);
              callbackErrorExceptions.add(e);
              callbackErrorActions.add(execsByRow.get(row));
              callbackErrorServers.add("null");
            }
          }
        }, results);

    future.waitUntilDone();

    if (future.hasError()) {
      throw future.getErrors();
    } else if (!callbackErrorExceptions.isEmpty()) {
      throw new RetriesExhaustedWithDetailsException(callbackErrorExceptions, callbackErrorActions,
          callbackErrorServers);
    }
  }

  public boolean isMapRTable() {
    if (maprTable_ != null) {
      return true;
    }
    return false;
  }

  public RegionLocator getRegionLocator() {
    return this.locator;
  }

  @VisibleForTesting
  BufferedMutator getBufferedMutator() throws IOException {
    if (mutator == null) {
      this.mutator = (BufferedMutatorImpl) connection.getBufferedMutator(
          new BufferedMutatorParams(tableName)
              .pool(pool)
              .writeBufferSize(tableConfiguration.getWriteBufferSize())
              .maxKeyValueSize(tableConfiguration.getMaxKeyValueSize())
      );
    }
    return mutator;
  }
}
