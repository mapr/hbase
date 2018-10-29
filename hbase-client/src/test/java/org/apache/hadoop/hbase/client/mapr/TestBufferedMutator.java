package org.apache.hadoop.hbase.client.mapr;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.BufferedMutator;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import com.sun.tools.javac.util.Assert;

import java.io.IOException;

public class TestBufferedMutator extends Configured implements Tool {
  /** The name of the column family used by the application. */
  private static final byte[] CF1 = Bytes.toBytes("cf1");
  private static final byte[] CF2 = Bytes.toBytes("cf2");

  public int run(String[] argv) throws IOException {
    if (argv.length < 1) {
      System.out.println("Test the table operations with HBase 1.1 style. Usage:\n"
                         +"To test in MapR DB\n"
                         + "\t hbase org.apache.hadoop.hbase.client.TestBufferedMutator tableName MapRDB\n"
                         +"To test in HBase\n"
                         + "\t hbase org.apache.hadoop.hbase.client.TestBufferedMutator tableName HBbase");
      return -1;
    }
    TableName tableName = TableName.valueOf(argv[0]);

    Configuration conf = HBaseConfiguration.create();
    String defaultdb = conf.get("mapr.hbase.default.db");
    if (argv.length > 1) {
      defaultdb = argv[1];
      conf.set("mapr.hbase.default.db", defaultdb);
      System.out.println("-----Set DB "+defaultdb+"-----");
    }

    /**
     * Connection to the cluster. A single connection shared by all application
     * threads.
     */
    Connection conn = ConnectionFactory.createConnection(conf);

    /** A lightweight handle to a specific table. Used from a single thread. */
    Admin admin = null;

    admin = conn.getAdmin();
    if (admin.tableExists(tableName)) {
      admin.disableTable(tableName);
      admin.deleteTable(tableName);
    }
    HTableDescriptor tableDesc = new HTableDescriptor(tableName);
    HColumnDescriptor hcd1 = new HColumnDescriptor(CF1);
    HColumnDescriptor hcd2 = new HColumnDescriptor(CF2);
    tableDesc.addFamily(hcd1);
    tableDesc.addFamily(hcd2);
    admin.createTable(tableDesc);

    BufferedMutator mutator=conn.getBufferedMutator(tableName);

    Table table=conn.getTable(tableName);

    try{
      Put p = new Put(Bytes.toBytes("row1"));
      p.addColumn(CF1, Bytes.toBytes("col1"), Bytes.toBytes("val11"));
      p.addColumn(CF2, Bytes.toBytes("col2"), Bytes.toBytes("val12"));

      mutator.mutate(p);

      Get g=new Get(Bytes.toBytes("row1"));
      g.addColumn(CF1, Bytes.toBytes("col1"));

      Result result=table.get(g);
      System.out.println("Result: "+result.size());
      if (result.size() > 0) {
        for(KeyValue keyValue : result.list()) {
          System.out.println("Qualifier : " + keyValue.getKeyString() + " : Value : " + Bytes.toString(keyValue.getValue()));
        }
        System.out.println("Error: Autoflush is false, but data reaches the table!");
        return -1;
      }

      mutator.flush();
      Result result2=table.get(g);
      System.out.println("Result: "+result2.size());
      if (result2.size() <= 0) {
        System.out.println("Error: called flush, but data is NOT in the table!");
        return -2;
      }
      for(KeyValue keyValue : result2.list()) {
        System.out.println("Qualifier : " + keyValue.getKeyString() + " : Value : " + Bytes.toString(keyValue.getValue()));
      }
      if (result2.size() != 1) {
        System.out.println("Error: called flush, but data size is not 1 in the table!");
        return -3;
      }

      Delete d=new Delete(Bytes.toBytes("row1"));
      d.addFamily(CF1);
      mutator.mutate(d);

      g=new Get(Bytes.toBytes("row1"));
      g.addFamily(CF1);

      Result result3=table.get(g);
      System.out.println("After delete Result: "+result3.size());
      //for(KeyValue keyValue : result3.list()) {
      //  System.out.println("Qualifier : " + keyValue.getKeyString() + " : Value : " + Bytes.toString(keyValue.getValue()));
      //}

    } catch (IOException e) {
      // exception while creating/destroying Connection or BufferedMutator
      System.out.println("exception while creating/destroying Connection or BufferedMutator");
      e.printStackTrace();

    } finally{
      mutator.close();
      System.out.println("mutator closed successfully");

      table.close();
      System.out.println("table closed successfully");

      conn.close();
      System.out.println("connection closed successfully");
    }
    return 0;
  }

  public static void main(String[] argv) throws Exception {
    int ret = ToolRunner.run(new TestBufferedMutator(), argv);
    System.exit(ret);
  }
}
