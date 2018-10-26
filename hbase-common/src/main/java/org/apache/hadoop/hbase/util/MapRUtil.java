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
package org.apache.hadoop.hbase.util;

import java.util.Arrays;

import org.apache.hadoop.hbase.TableName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.classification.InterfaceAudience;

/**
 * Utility methods by MapR for integration with M7.
 */
@InterfaceAudience.Private
public abstract class MapRUtil {
  private static final Log LOG = LogFactory.getLog(MapRUtil.class);

  private static final String HBASE_URI_PREFIX = "hbase://";
  private static final byte[] HBASE_URI_PREFIX_BYTES = Bytes.toBytes(HBASE_URI_PREFIX);

  protected MapRUtil() {
    super();
  }

  public static String adjustTableNameString(String tableName) {
    return tableName.startsWith(HBASE_URI_PREFIX)
        ? tableName.substring(tableName.lastIndexOf('/')+1)
            : tableName;
  }

  public static TableName adjustTableName(TableName tableName) {
    String alias = tableName.getAliasAsString();
    if (alias.startsWith(HBASE_URI_PREFIX)) {
      return TableName.valueOfWithAlias(alias.substring(alias.lastIndexOf('/')+1), alias);
    } else {
      return tableName;
    }
  }

  public static byte[] adjustTableName(String tableName) {
    return adjustTableName(Bytes.toBytes(tableName));
  }

  public static byte[] adjustTableName(byte[] tableName) {
    if(Bytes.startsWith(tableName, HBASE_URI_PREFIX_BYTES)) {
      int i = tableName.length-1;
      for (; i >= HBASE_URI_PREFIX_BYTES.length; i--) {
        if (tableName[i] == '/') {
          break;
        }
      }
      return Arrays.copyOfRange(tableName, i+1, tableName.length);
    }
    else {
      return tableName.clone();
    }
  }
}
