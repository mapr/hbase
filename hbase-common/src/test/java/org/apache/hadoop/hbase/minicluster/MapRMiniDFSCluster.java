package org.apache.hadoop.hbase.minicluster;

import java.io.File;
import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

public class MapRMiniDFSCluster {
  private Configuration conf;
  private FileSystem fs;
  private Path workDir;

  public MapRMiniDFSCluster() throws IOException {
    this.workDir = new Path(System.getProperty("test.tmp.dir", "target" + File.separator + "test" + File.separator + "tmp"));
    this.conf = new Configuration();
    this.conf.set("fs.default.name", "file:///");
    this.fs = FileSystem.getLocal(this.conf);
    this.fs.setWorkingDirectory(this.workDir);
  }

  public MapRMiniDFSCluster(Configuration conf) throws IOException {
    this.workDir = new Path(System.getProperty("test.tmp.dir", "target" + File.separator + "test" + File.separator + "tmp"));
    this.conf = conf;
    this.fs = FileSystem.getLocal(conf);
    this.fs.setWorkingDirectory(this.workDir);
  }


  public FileSystem getFileSystem() throws IOException {
    return this.fs;
  }
}
