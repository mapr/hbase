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
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.HRegionLocation;
import org.apache.hadoop.hbase.RegionLocations;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.classification.InterfaceAudience;
import org.apache.hadoop.hbase.classification.InterfaceStability;
import org.apache.hadoop.hbase.client.mapr.AbstractHTable;
import org.apache.hadoop.hbase.client.mapr.AbstractMapRClusterConnection;
import org.apache.hadoop.hbase.client.mapr.BaseTableMappingRules;
import org.apache.hadoop.hbase.client.mapr.TableMappingRulesFactory;
import org.apache.hadoop.hbase.util.Pair;

import com.google.common.annotations.VisibleForTesting;

/**
 * An implementation of {@link RegionLocator}. Used to view region location information for a single
 * HBase table. Lightweight. Get as needed and just close when done. Instances of this class SHOULD
 * NOT be constructed directly. Obtain an instance via {@link Connection}. See
 * {@link ConnectionFactory} class comment for an example of how.
 *
 * <p> This class is thread safe
 */
@InterfaceAudience.Private
@InterfaceStability.Stable
public class HRegionLocator implements RegionLocator {

  private static final Log LOG = LogFactory.getLog(HRegionLocator.class);
  private final TableName tableName;
  private final ClusterConnection connection;

  // Band-aid with a cached maprTable handle. We should implement a mapr version RegionLocator
  // in the com.mapr.fs.hbase, so that we use maprRegionLocator handle here.
  private AbstractHTable maprTable_ = null;

  public HRegionLocator(TableName tableName, ClusterConnection connection) {
    this.connection = connection;
    this.tableName = tableName;

    if (BaseTableMappingRules.isInHBaseService()) {
      // Calling from the hbase servers, we are done.
      return;
    }

    if (connection instanceof AbstractMapRClusterConnection) {
      maprTable_ = ((AbstractMapRClusterConnection) connection).createAbstractMapRTable(connection.getConfiguration(), tableName);
      if (maprTable_ == null) {
        throw new IllegalArgumentException("Could not find table " + this.tableName + " through MapRClusterConnection.");
      }
      return;
    } else if (connection instanceof org.apache.hadoop.hbase.client.ConnectionManager.HConnectionImplementation) {
      BaseTableMappingRules tableMappingRule = null;
      try {
        tableMappingRule = TableMappingRulesFactory.create(connection.getConfiguration());
      } catch (IOException e) {
        throw new IllegalArgumentException("Could not get tableMappingRule for table " + this.tableName + " through HConnection. Reason:"
                                         + e.getStackTrace());
      }
      if ((tableMappingRule != null) && tableMappingRule.isMapRTable(tableName)) {
        maprTable_ = HTable.createMapRTable(connection.getConfiguration(), tableName);
      }
      //maprTable_ can be null in this case.
      return;
    } else {
      LOG.warn("Unknown connection type "+connection.getClass().getName());
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void close() throws IOException {
    if (maprTable_ != null) {
      maprTable_.close();
      maprTable_ = null;
      return;
    }
    // This method is required by the RegionLocator interface. This implementation does not have any
    // persistent state in hbase, so there is no need to do anything here for hbase.
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public HRegionLocation getRegionLocation(final byte [] row)
  throws IOException {
    if (maprTable_ != null) {
      return maprTable_.getRegionLocation(row);
    }
    return connection.getRegionLocation(tableName, row, false);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public HRegionLocation getRegionLocation(final byte [] row, boolean reload)
  throws IOException {
    if (maprTable_ != null) {
      return maprTable_.getRegionLocation(row, reload);
    }
    return connection.getRegionLocation(tableName, row, reload);
  }

  @Override
  public List<HRegionLocation> getAllRegionLocations() throws IOException {
    TableName tableName = getName();
    NavigableMap<HRegionInfo, ServerName> locations = null;
    if (maprTable_ != null) {
      locations = maprTable_.getRegionLocations();
    } else {
      locations = MetaScanner.allTableRegions(this.connection, tableName);
    }
    ArrayList<HRegionLocation> regions = new ArrayList<>(locations.size());
    for (Entry<HRegionInfo, ServerName> entry : locations.entrySet()) {
      regions.add(new HRegionLocation(entry.getKey(), entry.getValue()));
    }
    if (regions.size() > 0) {
      connection.cacheLocation(tableName, new RegionLocations(regions));
    }
    return regions;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[][] getStartKeys() throws IOException {
    if (maprTable_ != null) {
      return maprTable_.getStartKeys();
    }
    return getStartEndKeys().getFirst();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[][] getEndKeys() throws IOException {
    if (maprTable_ != null) {
      return maprTable_.getEndKeys();
    }
    return getStartEndKeys().getSecond();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Pair<byte[][], byte[][]> getStartEndKeys() throws IOException {
    if (maprTable_ != null) {
      return maprTable_.getStartEndKeys();
    }
    return getStartEndKeys(listRegionLocations());
  }

  @VisibleForTesting
  Pair<byte[][], byte[][]> getStartEndKeys(List<RegionLocations> regions) {
    if (maprTable_ != null) {
        throw new UnsupportedOperationException("This is not a public API, and should not be called for MapRDB");
    }

    final byte[][] startKeyList = new byte[regions.size()][];
    final byte[][] endKeyList = new byte[regions.size()][];

    for (int i = 0; i < regions.size(); i++) {
      HRegionInfo region = regions.get(i).getRegionLocation().getRegionInfo();
      startKeyList[i] = region.getStartKey();
      endKeyList[i] = region.getEndKey();
    }

    return new Pair<>(startKeyList, endKeyList);
  }

  @Override
  public TableName getName() {
    return this.tableName;
  }

  @VisibleForTesting
  List<RegionLocations> listRegionLocations() throws IOException {
    if (maprTable_ != null) {
      throw new UnsupportedOperationException("This is not a public API, and should not be called for MapRDB");
    }
    return MetaScanner.listTableRegionLocations(getConfiguration(), this.connection, getName());
  }

  public Configuration getConfiguration() {
    return connection.getConfiguration();
  }

  public boolean isMapRTable() {
    return (maprTable_ != null);
  }
}
