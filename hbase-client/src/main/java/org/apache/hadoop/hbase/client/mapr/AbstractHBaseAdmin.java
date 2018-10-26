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
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.MasterNotRunningException;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableExistsException;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.TableNotFoundException;
import org.apache.hadoop.hbase.UnknownRegionException;
import org.apache.hadoop.hbase.ZooKeeperConnectionException;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.security.User;

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
  private final static Log LOG = LogFactory.getLog(HBaseAdmin.class);

  /**
   * Sets the User for the Admin instance.
   */
  public void setUser(User user) {
    LOG.debug("setUser() called with MapR Table without impersonation support.");
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

  public void closeRegionWithEncodedRegionName(String encodedRegionName,
      String serverName) throws IOException {
    LOG.warn("closeRegionWithEncodedRegionName() called for a MapR Table, silently ignoring.");
  }

  public void closeRegion(ServerName sn, HRegionInfo hri) throws IOException {
    LOG.warn("closeRegion() called for a MapR Table, silently ignoring.");
  }

  public void flush(byte[] tableNameOrRegionName) throws IOException {
    LOG.warn("flush() called for a MapR Table, silently ignoring.");
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

}
