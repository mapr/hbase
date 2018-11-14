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
package org.apache.hadoop.hbase.client.mapr;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.classification.InterfaceAudience;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HRegionLocation;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.MasterNotRunningException;
import org.apache.hadoop.hbase.RegionLocations;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.ZooKeeperConnectionException;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.AsyncProcess;
import org.apache.hadoop.hbase.client.BufferedMutator;
import org.apache.hadoop.hbase.client.BufferedMutatorParams;
import org.apache.hadoop.hbase.client.ClusterConnection;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.MasterKeepAliveConnection;
import org.apache.hadoop.hbase.client.NonceGenerator;
import org.apache.hadoop.hbase.client.RegionLocator;
import org.apache.hadoop.hbase.client.Row;
import org.apache.hadoop.hbase.client.RpcRetryingCallerFactory;
import org.apache.hadoop.hbase.client.ServerStatisticTracker;
import org.apache.hadoop.hbase.client.backoff.ClientBackoffPolicy;
import org.apache.hadoop.hbase.client.coprocessor.Batch.Callback;
import org.apache.hadoop.hbase.protobuf.generated.AdminProtos.AdminService;
import org.apache.hadoop.hbase.protobuf.generated.ClientProtos.ClientService;
import org.apache.hadoop.hbase.protobuf.generated.MasterProtos.MasterService;
import org.apache.hadoop.hbase.security.User;
import org.apache.hadoop.hbase.security.UserProvider;

/**
 */
@InterfaceAudience.Private
public abstract class AbstractMapRClusterConnection implements ClusterConnection {
  private final static Log LOG = LogFactory.getLog(AbstractMapRClusterConnection.class);

  private static final GenericHFactory<AbstractHTable> tableFactory_ =
            new GenericHFactory<AbstractHTable>();
  private static final GenericHFactory<AbstractMapRClusterConnection> maprConnFactory_ =
          new GenericHFactory<AbstractMapRClusterConnection>();

  public abstract void abort(String why, Throwable e);

  public abstract boolean isAborted();

  public abstract void close() throws IOException;

  public abstract Configuration getConfiguration();

  public abstract BaseTableMappingRules getTableMappingRule();

  public static AbstractHTable createAbstractMapRTable(Configuration conf, TableName tableName) {
    return tableFactory_.getImplementorInstance(
            conf.get("htable.impl.mapr", "com.mapr.fs.hbase.HTableImpl11"),
            new Object[] {conf, tableName.getNameAsString().getBytes(), null, null, null},
            new Class[] {Configuration.class, byte[].class, BufferedMutator.class,
                BufferedMutator.ExceptionListener.class, ExecutorService.class});
  }

  public static AbstractHTable createAbstractMapRTable(Configuration conf, TableName tableName,
                                                       BufferedMutator bm,
                                                       BufferedMutator.ExceptionListener listener,
                                                       ExecutorService pool) {
    return tableFactory_.getImplementorInstance(
            conf.get("htable.impl.mapr", "com.mapr.fs.hbase.HTableImpl11"),
            new Object[] {conf, tableName.getNameAsString().getBytes(), bm, listener, pool},
            new Class[] {Configuration.class, byte[].class, BufferedMutator.class,
                BufferedMutator.ExceptionListener.class, ExecutorService.class});
  }

  public static Connection createMapRClusterConnection(final Configuration conf, final boolean managed,
          final User user, BaseTableMappingRules tableMappingRule) {
      return maprConnFactory_.getImplementorInstance(
              conf.get("maprclusterconnection.impl.mapr", "com.mapr.fs.hbase.MapRClusterConnectionImpl"),
              new Object[] {conf, managed, user, tableMappingRule},
              new Class[] {Configuration.class, boolean.class, User.class, BaseTableMappingRules.class});
  }

  public User getUser() {
    UserProvider provider = UserProvider.instantiate(getConfiguration());
    User user = null;
    try {
      user = provider.getCurrent();
    } catch (IOException e) {
      LOG.error("Failed to get the current user. Set it to Null.");
    }
    return user;
  }

  public abstract HTableInterface getTable(String tableName) throws IOException;

  public abstract HTableInterface getTable(byte[] tableName) throws IOException;

  public abstract HTableInterface getTable(TableName tableName) throws IOException;

  public abstract HTableInterface getTable(String tableName, ExecutorService pool) throws IOException;

  public abstract HTableInterface getTable(byte[] tableName, ExecutorService pool) throws IOException;

  public abstract HTableInterface getTable(TableName tableName, ExecutorService pool) throws IOException;

  public abstract BufferedMutator getBufferedMutator(BufferedMutatorParams params);

  public abstract BufferedMutator getBufferedMutator(TableName tableName) throws IOException;

  public abstract ExecutorService getBMPool() throws IOException;

  public abstract RegionLocator getRegionLocator(TableName tableName) throws IOException;

  public abstract AbstractHBaseAdmin createAbstractHBaseAdmin() throws IOException;

  public abstract Admin getAdmin() throws IOException;

  @Override
  public boolean isMasterRunning() throws MasterNotRunningException,
      ZooKeeperConnectionException {
    LOG.warn("isMasterRunning() called for a MapR Connection, returning true.");
    return true;
  }

  public abstract boolean isTableEnabled(TableName tableName) throws IOException;

  public abstract boolean isTableEnabled(byte[] tableName) throws IOException;

  public abstract boolean isTableDisabled(TableName tableName) throws IOException;

  public abstract boolean isTableDisabled(byte[] tableName) throws IOException;

  public abstract boolean isTableAvailable(TableName tableName) throws IOException;

  public abstract boolean isTableAvailable(byte[] tableName) throws IOException;

  public abstract boolean isTableAvailable(TableName tableName, byte[][] splitKeys)
      throws IOException;

  public abstract boolean isTableAvailable(byte[] tableName, byte[][] splitKeys)
      throws IOException;

  public abstract HTableDescriptor[] listTables() throws IOException;

  public abstract String[] getTableNames() throws IOException;

  public abstract TableName[] listTableNames() throws IOException;

  public abstract HTableDescriptor getHTableDescriptor(TableName tableName)
      throws IOException;

  public abstract HTableDescriptor getHTableDescriptor(byte[] tableName)
      throws IOException;

  public HRegionLocation locateRegion(TableName tableName, byte[] row)
      throws IOException {
      throw new UnsupportedOperationException(
              "locateRegion is not supported for MapR.");
  }

  public HRegionLocation locateRegion(byte[] tableName, byte[] row)
      throws IOException {
      throw new UnsupportedOperationException(
              "locateRegion is not supported for MapR.");
  }

  public RegionLocations locateRegion(TableName tableName, byte[] row, boolean useCache,
      boolean retry) throws IOException {
      throw new UnsupportedOperationException(
              "locateRegion is not supported for MapR.");
  }

  @Override
  public void clearRegionCache() {
      throw new UnsupportedOperationException(
              "clearRegionCache is not supported for MapR.");
  }

  @Override
  public void clearRegionCache(TableName tableName) {
      throw new UnsupportedOperationException(
              "clearRegionCache is not supported for MapR.");
  }

  @Override
  public void clearRegionCache(byte[] tableName) {
      throw new UnsupportedOperationException(
              "clearRegionCache is not supported for MapR.");
  }

  @Override
  public void deleteCachedRegionLocation(HRegionLocation location) {
      throw new UnsupportedOperationException(
              "deleteCachedRegionLocation is not supported for MapR.");
  }

  @Override
  public HRegionLocation relocateRegion(TableName tableName, byte[] row)
      throws IOException {
      throw new UnsupportedOperationException(
              "relocateRegion is not supported for MapR.");
  }

  @Override
  public HRegionLocation relocateRegion(byte[] tableName, byte[] row)
      throws IOException {
      throw new UnsupportedOperationException(
              "relocateRegion is not supported for MapR.");
  }

  @Override
  public void updateCachedLocations(TableName tableName, byte[] rowkey,
      Object exception, HRegionLocation source) {
      throw new UnsupportedOperationException(
              "updateCachedLocations is not supported for MapR.");
  }

  @Override
  public void updateCachedLocations(TableName tableName, byte[] regionName, byte[] rowkey,
      Object exception, ServerName source) {
      throw new UnsupportedOperationException(
              "updateCachedLocations is not supported for MapR.");
  }

  @Override
  public void updateCachedLocations(byte[] tableName, byte[] rowkey,
      Object exception, HRegionLocation source) {
      throw new UnsupportedOperationException(
              "updateCachedLocations is not supported for MapR.");
  }

  @Override
  public HRegionLocation locateRegion(byte[] regionName) throws IOException {
      throw new UnsupportedOperationException(
              "locateRegion is not supported for MapR.");
  }

  @Override
  public List<HRegionLocation> locateRegions(TableName tableName)
      throws IOException {
      throw new UnsupportedOperationException(
              "locateRegions is not supported for MapR.");
  }

  @Override
  public List<HRegionLocation> locateRegions(byte[] tableName)
      throws IOException {
      throw new UnsupportedOperationException(
              "locateRegions is not supported for MapR.");
  }

  @Override
  public List<HRegionLocation> locateRegions(TableName tableName,
      boolean useCache, boolean offlined) throws IOException {
      throw new UnsupportedOperationException(
              "locateRegions is not supported for MapR.");
  }

  @Override
  public List<HRegionLocation> locateRegions(byte[] tableName,
      boolean useCache, boolean offlined) throws IOException {
      throw new UnsupportedOperationException(
              "locateRegions is not supported for MapR.");
  }

  @Override
  public RegionLocations locateRegion(TableName tableName, byte[] row, boolean useCache,
      boolean retry, int replicaId) throws IOException {
      throw new UnsupportedOperationException(
              "locateRegion is not supported for MapR.");
  }

  @Override
  public RegionLocations relocateRegion(TableName tableName, byte[] row, int replicaId)
      throws IOException {
      throw new UnsupportedOperationException(
              "relocateRegion is not supported for MapR.");
  }

  @Override
  public MasterService.BlockingInterface getMaster() throws IOException {
      throw new UnsupportedOperationException(
              "relocateRegion is not supported for MapR.");
  }

  @Override
  public AdminService.BlockingInterface getAdmin(
      ServerName serverName) throws IOException {
      throw new UnsupportedOperationException(
              "AdminService.BlockingInterface getAdmin is not supported for MapR.");
  }

  @Override
  public ClientService.BlockingInterface getClient(
      ServerName serverName) throws IOException {
      throw new UnsupportedOperationException(
              "ClientService.BlockingInterface getClient is not supported for MapR.");
  }

  @Override
  public AdminService.BlockingInterface getAdmin(
      ServerName serverName, boolean getMaster) throws IOException {
      throw new UnsupportedOperationException(
              "AdminService.BlockingInterface getAdmin is not supported for MapR.");
  }

  @Override
  public HRegionLocation getRegionLocation(TableName tableName, byte[] row,
      boolean reload) throws IOException {
      throw new UnsupportedOperationException(
              "getRegionLocation is not supported for MapR.");
  }

  @Override
  public HRegionLocation getRegionLocation(byte[] tableName, byte[] row,
      boolean reload) throws IOException {
      throw new UnsupportedOperationException(
              "getRegionLocation is not supported for MapR.");
  }

  @Override
  public void processBatch(List<? extends Row> actions, TableName tableName,
      ExecutorService pool, Object[] results) throws IOException,
      InterruptedException {
      throw new UnsupportedOperationException(
              "processBatch is not supported for MapR.");
  }

  @Override
  public void processBatch(List<? extends Row> actions, byte[] tableName,
      ExecutorService pool, Object[] results) throws IOException,
      InterruptedException {
    throw new UnsupportedOperationException(
              "processBatch is not supported for MapR.");
  }

  @Override
  public <R> void processBatchCallback(List<? extends Row> list,
      TableName tableName, ExecutorService pool, Object[] results,
      Callback<R> callback) throws IOException, InterruptedException {
    throw new UnsupportedOperationException(
              "processBatchCallback is not supported for MapR.");
  }

  @Override
  public <R> void processBatchCallback(List<? extends Row> list,
      byte[] tableName, ExecutorService pool, Object[] results,
      Callback<R> callback) throws IOException, InterruptedException {
    throw new UnsupportedOperationException(
              "processBatchCallback is not supported for MapR.");
  }

  @Override
  public void setRegionCachePrefetch(TableName tableName, boolean enable) {
    throw new UnsupportedOperationException(
              "setRegionCachePrefetch is not supported for MapR.");
  }

  @Override
  public void setRegionCachePrefetch(byte[] tableName, boolean enable) {
    throw new UnsupportedOperationException(
              "setRegionCachePrefetch is not supported for MapR.");
  }

  @Override
  public boolean getRegionCachePrefetch(TableName tableName) {
    throw new UnsupportedOperationException(
              "getRegionCachePrefetch is not supported for MapR.");
  }

  @Override
  public boolean getRegionCachePrefetch(byte[] tableName) {
    throw new UnsupportedOperationException(
              "getRegionCachePrefetch is not supported for MapR.");
  }

  @Override
  public int getCurrentNrHRS() throws IOException {
    throw new UnsupportedOperationException(
              "getCurrentNrHRS is not supported for MapR.");
  }

  public abstract HTableDescriptor[] getHTableDescriptorsByTableName(
      List<TableName> tableNames) throws IOException;

  public abstract HTableDescriptor[] getHTableDescriptors(List<String> tableNames)
      throws IOException;

  @Override
  public abstract boolean isClosed();

  @Override
  public void clearCaches(ServerName sn) {
    throw new UnsupportedOperationException(
            "clearCaches is not supported for MapR.");
    }

  @Override
  public MasterKeepAliveConnection getKeepAliveMasterService()
      throws MasterNotRunningException {
    throw new UnsupportedOperationException(
            "getKeepAliveMasterService is not supported for MapR.");
  }

  @Override
  public boolean isDeadServer(ServerName serverName) {
    throw new UnsupportedOperationException(
            "isDeadServer is not supported for MapR.");
  }

  @Override
  public NonceGenerator getNonceGenerator() {
    throw new UnsupportedOperationException(
            "getNonceGenerator is not supported for MapR.");
  }

  @Override
  public AsyncProcess getAsyncProcess() {
    throw new UnsupportedOperationException(
            "getAsyncProcess is not supported for MapR.");
  }

  @Override
  public RpcRetryingCallerFactory getNewRpcRetryingCallerFactory(Configuration conf) {
    throw new UnsupportedOperationException(
            "getNewRpcRetryingCallerFactory is not supported for MapR.");
  }
  
  @Override
  public abstract boolean isManaged();

  @Override
  public ServerStatisticTracker getStatisticsTracker() {
    throw new UnsupportedOperationException(
            "getStatisticsTracker is not supported for MapR.");
  }

  @Override
  public ClientBackoffPolicy getBackoffPolicy() {
    throw new UnsupportedOperationException(
            "getStatisticsTracker is not supported for MapR.");
  }

}
