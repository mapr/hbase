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

package org.apache.hadoop.hbase.util;

import java.io.IOException;

import org.apache.hadoop.hbase.classification.InterfaceAudience;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * <a href="http://www.mapr.com">MapR</a> implementation.
 */
@InterfaceAudience.Private
public class FSMapRUtils extends FSUtils {
  private static final Log LOG = LogFactory.getLog(FSMapRUtils.class);

  private static Class<?> MapRFileSystemClass;
  static {
    try {
      MapRFileSystemClass = Class.forName("com.mapr.fs.MapRFileSystem");
    } catch (ClassNotFoundException e) {
      MapRFileSystemClass = null;
    }
  }

  /**
   * @param conf the Configuration of HBase
   * @param srcFs
   * @param desFs
   * @return Whether srcFs and desFs are on same hdfs or not
   */
  @Override
  public boolean isSameFileSystem(Configuration conf, FileSystem srcFs, FileSystem desFs) {

    boolean srcIsMapRFs = (MapRFileSystemClass != null && MapRFileSystemClass.isAssignableFrom(srcFs.getClass()));
    boolean desIsMapRFs = (MapRFileSystemClass != null && MapRFileSystemClass.isAssignableFrom(desFs.getClass()));
    if (srcIsMapRFs && desIsMapRFs) {
      LOG.info("srcFs "+srcFs.getUri()+" is maprfs and desFs "+desFs.getUri()+" is maprfs.");
      return true;
    } else if (srcIsMapRFs && !desIsMapRFs) {
      LOG.warn("srcFs "+srcFs.getUri()+" is maprfs but desFs "+desFs.getUri()+" is NOT maprfs.");
      return false;
    } else if (!srcIsMapRFs && desIsMapRFs) {
      LOG.warn("srcFs "+srcFs.getUri()+" is NOT maprfs but desFs "+desFs.getUri()+" is maprfs.");
      return false;
    }  else {
      LOG.error("This should NOT happen. both srcFs "+srcFs.getUri()+" and desFs "+desFs.getUri()+" are NOT maprfs.");
      return false;
    }
  }

  public void recoverFileLease(final FileSystem fs, final Path p,
      Configuration conf, CancelableProgressable reporter) throws IOException {
    LOG.info("Recovering file " + p.toString() +
      " by changing permission to readonly");
    FsPermission roPerm = new FsPermission((short) 0444);
    fs.setPermission(p, roPerm);
  }
}
