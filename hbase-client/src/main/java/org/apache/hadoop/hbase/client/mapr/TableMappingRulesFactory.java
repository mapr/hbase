/**
 * Copyright The Apache Software Foundation
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

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;

public class TableMappingRulesFactory {
  private static final Log LOG = LogFactory.getLog(TableMappingRulesFactory.class);
  private static final GenericHFactory<BaseTableMappingRules> ruleFactory_ =
      new GenericHFactory<BaseTableMappingRules>();

  private static volatile BaseTableMappingRules base_instance = null;

  /**
   * @param conf
   * @return
   * @throws IOException
   */
  public static BaseTableMappingRules create(Configuration conf)
      throws IOException {
    try {
      if (base_instance != null) {
        return base_instance;
      }
      try {
        return ruleFactory_.getImplementorInstance(
          conf.get("hbase.mappingrule.impl", "com.mapr.fs.hbase.MapRTableMappingRules"),
          new Object[] {conf}, new Class[] {Configuration.class});
      } catch (Throwable t) {
        Throwable cause = t.getCause();
        while (cause != null) {
          if ((cause instanceof ClassNotFoundException || cause instanceof NoClassDefFoundError)
              && cause.getMessage().contains("MapRTableMappingRules")) {
            LOG.info("Could not find MapRTableMappingRules class in the classpath, assuming HBase only cluster.");
            LOG.info("If you are trying to access M7 tables, add mapr-hbase jar to your classpath.");
            LOG.debug(t.getMessage(), t);
            return (base_instance = BaseTableMappingRules.INSTANCE);
          }
          cause = cause.getCause();
        }
        throw t;
      }
    } catch (Throwable e) {
      throw (e instanceof IOException ? (IOException)e : new IOException(e));
    }
  }

  public static boolean isHbaseOnly() {
    return (base_instance != null);
  }
}
