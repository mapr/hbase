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

package org.apache.hadoop.hbase;

import com.mapr.web.security.SslConfig;
import com.mapr.web.security.WebSecurityManager;

public class MapRSslConfigReader {

  private MapRSslConfigReader() {
  }

  /**
   * Reads client keystore location.
   * @return client keystore location as string
   */

  public static String getClientKeystoreLocation() {
    try (SslConfig sslConfig = WebSecurityManager.getSslConfig(SslConfig.SslConfigScope.SCOPE_CLIENT_ONLY)) {
      return sslConfig.getClientKeystoreLocation();
    }
  }

  /**
   * Reads client keystore password value.
   * @return client keystore password value as string
   */

  public static String getClientKeystorePassword() {
    try (SslConfig sslConfig = WebSecurityManager.getSslConfig(SslConfig.SslConfigScope.SCOPE_CLIENT_ONLY)) {
      return new String(sslConfig.getClientKeystorePassword());

    }
  }

  /**
   * Reads client key password value.
   * @return client key password value as string
   */

  public static String getClientKeyPassword() {
    try (SslConfig sslConfig = WebSecurityManager.getSslConfig(SslConfig.SslConfigScope.SCOPE_CLIENT_ONLY)) {
      return new String(sslConfig.getClientKeyPassword());

    }
  }

}
