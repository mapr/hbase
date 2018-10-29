/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable
 * law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 */

package org.apache.hadoop.hbase.client;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.classification.InterfaceAudience;

import com.google.common.annotations.VisibleForTesting;

/**
 * Configuration parameters for the connection.
 * Configuration is a heavy weight registry that does a lot of string operations and regex matching.
 * Method calls into Configuration account for high CPU usage and have huge performance impact.
 * This class caches connection-related configuration values in the  ConnectionConfiguration
 * object so that expensive conf.getXXX() calls are avoided every time HTable, etc is instantiated.
 * see HBASE-12128
 */
@InterfaceAudience.Private
public class ConnectionConfiguration extends TableConfiguration{

  /**
   * Constructor
   * @param conf Configuration object
   */
  ConnectionConfiguration(Configuration conf) {
    super(conf);
  }

  /**
   * Constructor
   * This is for internal testing purpose (using the default value).
   * In real usage, we should read the configuration from the Configuration object.
   */
  @VisibleForTesting
  protected ConnectionConfiguration() {
    super();
  }
}
