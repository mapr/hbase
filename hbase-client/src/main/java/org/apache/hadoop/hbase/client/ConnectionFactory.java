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
import java.lang.reflect.Constructor;
import java.util.concurrent.ExecutorService;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.classification.InterfaceAudience;
import org.apache.hadoop.hbase.classification.InterfaceStability;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.mapr.BaseTableMappingRules;
import org.apache.hadoop.hbase.client.mapr.GenericHFactory;
import org.apache.hadoop.hbase.client.mapr.BaseTableMappingRules.ClusterType;
import org.apache.hadoop.hbase.client.mapr.AbstractHTable;
import org.apache.hadoop.hbase.client.mapr.AbstractMapRClusterConnection;
import org.apache.hadoop.hbase.client.mapr.TableMappingRulesFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.security.User;
import org.apache.hadoop.hbase.security.UserProvider;


/**
 * A non-instantiable class that manages creation of {@link Connection}s.
 * Managing the lifecycle of the {@link Connection}s to the cluster is the responsibility of
 * the caller.
 * From a {@link Connection}, {@link Table} implementations are retrieved
 * with {@link Connection#getTable(TableName)}. Example:
 * <pre>
 * Connection connection = ConnectionFactory.createConnection(config);
 * Table table = connection.getTable(TableName.valueOf("table1"));
 * try {
 *   // Use the table as needed, for a single operation and a single thread
 * } finally {
 *   table.close();
 *   connection.close();
 * }
 * </pre>
 *
 * Similarly, {@link Connection} also returns {@link Admin} and {@link RegionLocator}
 * implementations.
 *
 * This class replaces {@link HConnectionManager}, which is now deprecated.
 * @see Connection
 * @since 0.99.0
 */
@InterfaceAudience.Public
@InterfaceStability.Evolving
public class ConnectionFactory {
  private static final Log LOG = LogFactory.getLog(ConnectionFactory.class);

  public static final String DEFAULT_DB = "mapr.hbase.default.db";

  public static final String MAPR_ENGINE = "mapr";
  public static final String MAPR_ENGINE2 = "maprdb";
  public static final String HBASE_ENGINE = "hbase";

  private static final GenericHFactory<AbstractMapRClusterConnection> maprConnFactory_ =
      new GenericHFactory<AbstractMapRClusterConnection>();

   /** No public c.tors */
  protected ConnectionFactory() {
  }

  /**
   * Create a new Connection instance using default HBaseConfiguration. Connection
   * encapsulates all housekeeping for a connection to the cluster. All tables and interfaces
   * created from returned connection share zookeeper connection, meta cache, and connections
   * to region servers and masters.
   * <br>
   * The caller is responsible for calling {@link Connection#close()} on the returned
   * connection instance.
   *
   * Typical usage:
   * <pre>
   * Connection connection = ConnectionFactory.createConnection();
   * Table table = connection.getTable(TableName.valueOf("mytable"));
   * try {
   *   table.get(...);
   *   ...
   * } finally {
   *   table.close();
   *   connection.close();
   * }
   * </pre>
   *
   * @return Connection object for <code>conf</code>
   */
  public static Connection createConnection() throws IOException {
    return createConnection(HBaseConfiguration.create(), null, null);
  }

  /**
   * Create a new Connection instance using the passed <code>conf</code> instance. Connection
   * encapsulates all housekeeping for a connection to the cluster. All tables and interfaces
   * created from returned connection share zookeeper connection, meta cache, and connections
   * to region servers and masters.
   * <br>
   * The caller is responsible for calling {@link Connection#close()} on the returned
   * connection instance.
   *
   * Typical usage:
   * <pre>
   * Connection connection = ConnectionFactory.createConnection(conf);
   * Table table = connection.getTable(TableName.valueOf("mytable"));
   * try {
   *   table.get(...);
   *   ...
   * } finally {
   *   table.close();
   *   connection.close();
   * }
   * </pre>
   *
   * @param conf configuration
   * @return Connection object for <code>conf</code>
   */
  public static Connection createConnection(Configuration conf) throws IOException {
    return createConnection(conf, null, null);
  }

  /**
   * Create a new Connection instance using the passed <code>conf</code> instance. Connection
   * encapsulates all housekeeping for a connection to the cluster. All tables and interfaces
   * created from returned connection share zookeeper connection, meta cache, and connections
   * to region servers and masters.
   * <br>
   * The caller is responsible for calling {@link Connection#close()} on the returned
   * connection instance.
   *
   * Typical usage:
   * <pre>
   * Connection connection = ConnectionFactory.createConnection(conf);
   * Table table = connection.getTable(TableName.valueOf("mytable"));
   * try {
   *   table.get(...);
   *   ...
   * } finally {
   *   table.close();
   *   connection.close();
   * }
   * </pre>
   *
   * @param conf configuration
   * @param pool the thread pool to use for batch operations
   * @return Connection object for <code>conf</code>
   */
  public static Connection createConnection(Configuration conf, ExecutorService pool)
      throws IOException {
    return createConnection(conf, pool, null);
  }

  /**
   * Create a new Connection instance using the passed <code>conf</code> instance. Connection
   * encapsulates all housekeeping for a connection to the cluster. All tables and interfaces
   * created from returned connection share zookeeper connection, meta cache, and connections
   * to region servers and masters.
   * <br>
   * The caller is responsible for calling {@link Connection#close()} on the returned
   * connection instance.
   *
   * Typical usage:
   * <pre>
   * Connection connection = ConnectionFactory.createConnection(conf);
   * Table table = connection.getTable(TableName.valueOf("table1"));
   * try {
   *   table.get(...);
   *   ...
   * } finally {
   *   table.close();
   *   connection.close();
   * }
   * </pre>
   *
   * @param conf configuration
   * @param user the user the connection is for
   * @return Connection object for <code>conf</code>
   */
  public static Connection createConnection(Configuration conf, User user)
  throws IOException {
    return createConnection(conf, null, user);
  }

  /**
   * Create a new Connection instance using the passed <code>conf</code> instance. Connection
   * encapsulates all housekeeping for a connection to the cluster. All tables and interfaces
   * created from returned connection share zookeeper connection, meta cache, and connections
   * to region servers and masters.
   * <br>
   * The caller is responsible for calling {@link Connection#close()} on the returned
   * connection instance.
   *
   * Typical usage:
   * <pre>
   * Connection connection = ConnectionFactory.createConnection(conf);
   * Table table = connection.getTable(TableName.valueOf("table1"));
   * try {
   *   table.get(...);
   *   ...
   * } finally {
   *   table.close();
   *   connection.close();
   * }
   * </pre>
   *
   * @param conf configuration
   * @param user the user the connection is for
   * @param pool the thread pool to use for batch operations
   * @return Connection object for <code>conf</code>
   */
  public static Connection createConnection(Configuration conf, ExecutorService pool, User user)
  throws IOException {
    if (user == null) {
      UserProvider provider = UserProvider.instantiate(conf);
      user = provider.getCurrent();
    }

    return createConnection(conf, false, pool, user);
  }

  // If a connection created from here, it must be newer than Hbase 1.0. Any connection created newer than
  // Hbase 1.0 must be either mapr or hbase, but not both.
  static Connection createConnection(final Configuration conf, final boolean managed,
      final ExecutorService pool, final User user)
  throws IOException {

    boolean connAtCtor = conf.getBoolean(HBaseAdmin.HBASE_ADMIN_CONNECT_AT_CONSTRUCTION, false);
    BaseTableMappingRules tableMappingRule = null;
    if (BaseTableMappingRules.isInHBaseService()) {
      tableMappingRule = BaseTableMappingRules.INSTANCE;
    } else {
      tableMappingRule = TableMappingRulesFactory.create(conf);

      String defaultDb = conf.get(DEFAULT_DB, org.apache.hadoop.hbase.client.mapr.TableMappingRulesFactory.UNSETDB);

      if (defaultDb.equalsIgnoreCase(MAPR_ENGINE) || defaultDb.equalsIgnoreCase(MAPR_ENGINE2)) {
        tableMappingRule.setClusterType(ClusterType.MAPR_ONLY);
      } else if (defaultDb.equalsIgnoreCase(HBASE_ENGINE)) {
        tableMappingRule.setClusterType(ClusterType.HBASE_ONLY);
      } else { //either not set, or something else which we do not understand

        if (!tableMappingRule.isMapRClientInstalled()) {
          LOG.info(DEFAULT_DB + " " + defaultDb + " is neither MapRDB or HBase, set HBASE_ONLY mode since mapr client is not installed.");
          // mapr client is not installed, use hbase cluster connection
          tableMappingRule.setClusterType(ClusterType.HBASE_ONLY);
          connAtCtor = true;
        } else {
          LOG.info(DEFAULT_DB + " " + defaultDb + " is neither MapRDB or HBase, set HBASE_MAPR mode since mapr client is installed.");
          // user does not tell which cluster to connect to, and mapr client is installed, will connect to mapr.
          tableMappingRule.setClusterType(ClusterType.HBASE_MAPR);
        }
      }
      LOG.info("ConnectionFactory receives " + DEFAULT_DB +"(" + defaultDb +"), set clusterType(" + tableMappingRule.getClusterType()
             + "), user(" + user.getName() + "), hbase_admin_connect_at_construction(" + connAtCtor + ")");
    }

    if (tableMappingRule.getClusterType() == ClusterType.MAPR_ONLY) {
      LOG.debug("ConnectionFactory creates a maprdb connection!");
      //set it to MAPR_ENGINE, so that tableMappingRule.isMapRDefault() will be true
      conf.set("db.engine.default", MAPR_ENGINE);
      return maprConnFactory_.getImplementorInstance(
              conf.get("maprclusterconnection.impl.mapr", "com.mapr.fs.hbase.MapRClusterConnectionImpl"),
              new Object[] {conf, managed, user, tableMappingRule},
              new Class[] {Configuration.class, boolean.class, User.class, BaseTableMappingRules.class});
    }

    //set it to HBASE_ENGINE, so that tableMappingRule.isMapRDefault() will be false
    LOG.debug("ConnectionFactory creates a hbase connection!");
    conf.setBoolean(HBaseAdmin.HBASE_ADMIN_CONNECT_AT_CONSTRUCTION, connAtCtor);
    conf.set("db.engine.default", HBASE_ENGINE);
    String className = conf.get(HConnection.HBASE_CLIENT_CONNECTION_IMPL,
      ConnectionManager.HConnectionImplementation.class.getName());
    Class<?> clazz = null;
    try {
      clazz = Class.forName(className);
    } catch (ClassNotFoundException e) {
      throw new IOException(e);
    }
    try {
      // Default HCM#HCI is not accessible; make it so before invoking.
      Constructor<?> constructor =
        clazz.getDeclaredConstructor(Configuration.class,
          boolean.class, ExecutorService.class, User.class);
      constructor.setAccessible(true);
      return (Connection) constructor.newInstance(conf, managed, pool, user);
    } catch (Exception e) {
      throw new IOException(e);
    }
  }
}
