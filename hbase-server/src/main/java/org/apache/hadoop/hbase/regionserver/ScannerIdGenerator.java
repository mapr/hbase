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
package org.apache.hadoop.hbase.regionserver;

import org.apache.hadoop.hbase.shaded.com.google.common.hash.Hashing;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.classification.InterfaceAudience;
import org.apache.hadoop.hbase.util.Bytes;

/**
 * Generate a new style scanner id to prevent collision with previous started server or other RSs.
 * We have 64 bits to use.
 * The first 32 bits are MurmurHash32 of ServerName string "host,port,ts".
 * The ServerName contains both host, port, and start timestamp so it can prevent collision.
 * The lowest 32bit is generated by atomic int.
 */
@InterfaceAudience.Private
public class ScannerIdGenerator {

  private final long serverNameHash;
  private final AtomicInteger scannerIdGen = new AtomicInteger(0);

  public ScannerIdGenerator(ServerName serverName) {
    this.serverNameHash = (long)Hashing.murmur3_32().hashString(serverName.toString()).asInt() << 32;
  }

  public long generateNewScannerId() {
    return (scannerIdGen.incrementAndGet() & 0x00000000FFFFFFFFL) | serverNameHash;
  }

}
