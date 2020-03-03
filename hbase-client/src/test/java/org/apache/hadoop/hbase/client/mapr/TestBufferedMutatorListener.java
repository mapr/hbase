package org.apache.hadoop.hbase.client.mapr;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Append;
import org.apache.hadoop.hbase.client.BufferedMutator;
import org.apache.hadoop.hbase.client.BufferedMutatorParams;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RetriesExhaustedWithDetailsException;
import org.apache.hadoop.hbase.client.Row;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import java.io.IOException;

public class TestBufferedMutatorListener extends Configured implements Tool {
  /** The name of the column family used by the application. */
  private static final byte[] CF1 = Bytes.toBytes("cf1");
  private static final byte[] CF2 = Bytes.toBytes("cf2");

  public int run(String[] argv) throws IOException {
    if (argv.length < 1) {
      System.out.println("Test the table operations with HBase 1.1 style. Usage:\n"
                         +"To test in MapR DB\n"
                         + "\t hbase org.apache.hadoop.hbase.client.TestBufferedMutatorListener tableName MapRDB\n"
                         +"To test in HBase\n"
                         + "\t hbase org.apache.hadoop.hbase.client.TestBufferedMutatorListener tableName HBbase");
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
    /*
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
    */

    //final CountDownLatch latch = new CountDownLatch(1);

    BufferedMutator.ExceptionListener listener =
        new BufferedMutator.ExceptionListener() { 
          @Override
          public void onException(RetriesExhaustedWithDetailsException e, BufferedMutator mutator)
              throws RetriesExhaustedWithDetailsException {
            System.out.println("Listener report: Failed to send " + e.getNumExceptions() + " puts through mutator to table "
              + mutator.getName()); 
            for (int i = 0; i < e.getNumExceptions(); i++) { 
              Row row = e.getRow(i);
              String rstr = row.toString();
              System.out.println("Listener report: Failed put " + i + ":" + rstr +". Reason:");
              e.getCause(i).printStackTrace();
            }
            //throw e;
          }
        };

    BufferedMutatorParams params = new BufferedMutatorParams(tableName).listener(listener);
    BufferedMutator mutator = conn.getBufferedMutator(params);
    try{

       Put p1 = new Put("row1".getBytes());
       p1.addColumn(CF1, "column-1".getBytes(), "value-1".getBytes());
       //System.out.println("p1="+p1.toJSON());
       mutator.mutate(p1);

       Put p2 = new Put("row1".getBytes());
       p2.addColumn(CF2, "column-2".getBytes(), "value-2".getBytes());
       //System.out.println("p1="+p1.toJSON());
       mutator.mutate(p2);

       //Delete is a sync call
       //Delete d1 = new Delete("row1".getBytes());
       //d1.addColumn(CF1, "column-1".getBytes());
       //mutator.mutate(d1);

       //Delete d2 = new Delete("row1".getBytes());
       //d2.addColumn(CF2, "column-2".getBytes());
       //mutator.mutate(d2);

       /* Only support Put/Delete now
       Append a1 = new Append("row1".getBytes());
       a1.add(CF1, "column-1".getBytes(), "appendvalue-1".getBytes());
       mutator.mutate(a1);

       Append a2 = new Append("row1".getBytes());
       a2.add(CF2, "column-2".getBytes(), "appendvalue-2".getBytes());
       mutator.mutate(a2);
       */

      //without flush
      //mutator.flush();
    } catch (IOException e) {
      // exception while creating/destroying Connection or BufferedMutator
      System.out.println("exception while creating/destroying Connection or BufferedMutator"); 
      e.printStackTrace();

    }finally{
      mutator.close();
      System.out.println("Mutator close successfully");
    }

    //second mutator should work.
    BufferedMutator mutator2 = conn.getBufferedMutator(params);
    try{

      Put p1 = new Put("row2".getBytes());
      p1.addColumn(CF1, "column-1".getBytes(), "value-3".getBytes());
      //System.out.println("p1="+p1.toJSON());
      mutator2.mutate(p1);

      Put p2 = new Put("row2".getBytes());
      p2.addColumn(CF2, "column-2".getBytes(), "value-4".getBytes());
      //System.out.println("p1="+p1.toJSON());
      mutator2.mutate(p2);

      //with flush
      mutator2.flush();
    } catch (IOException e) {
      // exception while creating/destroying Connection or BufferedMutator
      System.out.println("exception while creating/destroying Connection or BufferedMutator"); 
      e.printStackTrace();

    }finally{
      mutator2.close();
      System.out.println("mutator2 close successfully");
      conn.close();
      System.out.println("Connection close successfully");
    }
    return 0;
  }

  public static void main(String[] argv) throws Exception {
    int ret = ToolRunner.run(new TestBufferedMutatorListener(), argv);
    System.exit(ret);
  }
}
