package org.apache.hadoop.hbase.mapreduce.mapr;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.util.Tool;

public class UnionJob extends Configured implements Tool {

  public static final String MapRTName1 = "/tmp/tmr1";
  public static final String MapRTName2 = "/tmp/tmr2";
  public static final String MapRTNamec = "/tmp/tmrc";

  public static final String HBaseTName1 = "hmr1";
  public static final String HBaseTName2 = "hmr2";
  public static final String HBaseTNamec = "hmrc";

  public static String TableName1 = null;
  public static String TableName2 = null;
  public static String TableNamec = null;

  /** The name of the column family used by the application. */
  private static final byte[] CF1 = Bytes.toBytes("cf1");
  private static final byte[] CF2 = Bytes.toBytes("cf2");

  String defaultdb = null;

  private void printUsage() {
    System.out.println("Test the TableMapReduceUtil::initTableMapperJob with Scan list Usage:\n"
        +"To test in MapR DB\n"
        + "\t hbase org.apache.hadoop.hbase.mapreduce.mapr.UnionJob MapRDB\n"
        +"To test in HBase\n"
        + "\t hbase org.apache.hadoop.hbase.mapreduce.mapr.UnionJob HBase\n"
        +"To test in mixed Mapr/Hbase tables\n"
        + "\t hbase org.apache.hadoop.hbase.mapreduce.mapr.UnionJob Mix");
  }

  private int parseArgs(String[] args) {
    if (args.length < 1) {
      printUsage();
      return -1;
    }

    if (args.length >= 1) {
      defaultdb = args[0];
    }

    return 0;
  }

  private void setup(Configuration conf) {

    if (defaultdb.equalsIgnoreCase("maprdb")) {
      TableName1 = MapRTName1;
      TableName2 = MapRTName2;
      TableNamec = MapRTNamec;
    } else if (defaultdb.equalsIgnoreCase("hbase")) {
      TableName1 = HBaseTName1;
      TableName2 = HBaseTName2;
      TableNamec = HBaseTNamec;
    } else if (defaultdb.equalsIgnoreCase("mix")) {
      TableName1 = HBaseTName1;
      TableName2 = MapRTName2;
      TableNamec = MapRTNamec;
    } else {
      printUsage();
      System.exit(-1);
    }

    TableName tn1 = TableName.valueOf(TableName1);
    TableName tn2 = TableName.valueOf(TableName2);
    TableName tnc = TableName.valueOf(TableNamec);

    System.out.println("Input table name 1 is "+tn1.getNameAsString()+
                       "Input table name 2 is "+tn2.getNameAsString()+
                       "Input table name combined is "+tnc.getNameAsString());

    System.out.println("TableName1 is "+String.valueOf(TableName1)+
        "TableName2 is "+String.valueOf(TableName2)+
        "TableNamec combined is "+String.valueOf(TableNamec));

    /**
     * Connection to the cluster. A single connection shared by all application
     * threads.
     */
    Connection connection = null;
    /** A lightweight handle to a specific table. Used from a single thread. */
    Table table1 = null;
    Table table2 = null;
    Table tablec = null;
    Admin admin = null;

    try {
      // establish the connection to the cluster.
      connection = ConnectionFactory.createConnection(conf);
      // retrieve a handle to the cluster admin.
      admin = connection.getAdmin();
      if (admin.tableExists(tn1)) {
        admin.disableTable(tn1);
        admin.deleteTable(tn1);
      }
      if (admin.tableExists(tn2)) {
        admin.disableTable(tn2);
        admin.deleteTable(tn2);
      }
      if (admin.tableExists(tnc)) {
        admin.disableTable(tnc);
        admin.deleteTable(tnc);
      }
      // retrieve a handle to the target table.
      HTableDescriptor tableDesc1 = new HTableDescriptor(tn1);
      HColumnDescriptor hcd1 = new HColumnDescriptor(CF1);
      tableDesc1.addFamily(hcd1);
      admin.createTable(tableDesc1);

      HTableDescriptor tableDesc2 = new HTableDescriptor(tn2);
      HColumnDescriptor hcd2 = new HColumnDescriptor(CF2);
      tableDesc2.addFamily(hcd2);
      admin.createTable(tableDesc2);

      HTableDescriptor tableDescC = new HTableDescriptor(tnc);
      tableDescC.addFamily(hcd1);
      admin.createTable(tableDescC);

      table1 = connection.getTable(tn1);
      table2 = connection.getTable(tn2);
      tablec = connection.getTable(tnc);

      //put 'storeSales', '20130101#1', 'cf1:sSales', '100'
      //put 'storeSales', '20130101#2', 'cf1:sSales', '110'
      //put 'storeSales', '20130102#1', 'cf1:sSales', '200'
      //put 'storeSales', '20130102#2', 'cf1:sSales', '210'
      // describe the data we want to write.
      Put p = new Put(Bytes.toBytes("20130101#1"));
      p.addColumn(CF1, Bytes.toBytes("sSales"), Bytes.toBytes("100"));
      table1.put(p);
      p = new Put(Bytes.toBytes("20130101#2"));
      p.addColumn(CF1, Bytes.toBytes("sSales"), Bytes.toBytes("110"));
      table1.put(p);
      p = new Put(Bytes.toBytes("20130102#1"));
      p.addColumn(CF1, Bytes.toBytes("sSales"), Bytes.toBytes("200"));
      table1.put(p);
      p = new Put(Bytes.toBytes("20130102#2"));
      p.addColumn(CF1, Bytes.toBytes("sSales"), Bytes.toBytes("210"));
      table1.put(p);

      Put p2 = new Put(Bytes.toBytes("20130101"));
      p2.addColumn(CF2, Bytes.toBytes("oSales"), Bytes.toBytes("400"));
      table2.put(p2);
      p2 = new Put(Bytes.toBytes("20130102"));
      p2.addColumn(CF2, Bytes.toBytes("oSales"), Bytes.toBytes("130"));
      table2.put(p2);

      Scan scan = new Scan();
      System.out.println("-----Setup "+tn1+" with data -----");
      ResultScanner scanner1 = table1.getScanner(scan);
      for (Result result = scanner1.next(); (result != null); result = scanner1.next()) {
        for(KeyValue keyValue : result.list()) {
            System.out.println("Qualifier : " + keyValue.getKeyString() + " : Value : " + Bytes.toString(keyValue.getValue()));
        }
      }

      System.out.println("-----Setup "+tn2+" with data -----");
      ResultScanner scanner2 = table2.getScanner(scan);
      for (Result result = scanner2.next(); (result != null); result = scanner2.next()) {
        for(KeyValue keyValue : result.list()) {
            System.out.println("Qualifier : " + keyValue.getKeyString() + " : Value : " + Bytes.toString(keyValue.getValue()));
        }
      }
      // close everything down
      if (admin != null)
        admin.close();
      if (table1 != null)
        table1.close();
      if (table2 != null)
        table2.close();
      if (tablec != null)
        tablec.close();
      if (connection != null)
        connection.close();
    } catch (Exception e) {
      System.out.println("setup failed with error");
      e.printStackTrace();
    }
  }

  @Override
  public int run(String[] args) throws Exception {

    Configuration config = HBaseConfiguration.create();
    String defaultdb = config.get("mapr.hbase.default.db");

    int ret = parseArgs(args);
    if (ret != 0) {
      return ret;
    }
    //config.set("mapr.hbase.default.db", defaultdb);
    System.out.println("-----Set DB "+defaultdb+"-----");

    setup(config);

    List<Scan> scans = new ArrayList<Scan>();
    Scan scan1 = new Scan();
    scan1.setAttribute("scan.attributes.table.name", TableName1.getBytes());
    System.out.println(scan1.getAttribute("scan.attributes.table.name"));
    scans.add(scan1);
    Scan scan2 = new Scan();
    scan2.setAttribute("scan.attributes.table.name", TableName2.getBytes());
    System.out.println(scan2.getAttribute("scan.attributes.table.name"));
    scans.add(scan2);
    Configuration conf = new Configuration();
    Job job = new Job(conf);
    job.setJarByClass(UnionJob.class);
    TableMapReduceUtil.initTableMapperJob(
            scans,
            UnionMapper.class,
            Text.class,
            IntWritable.class,
            job);
    TableMapReduceUtil.initTableReducerJob(
            String.valueOf(TableNamec),
            UnionReducer.class,
            job);
    job.waitForCompletion(true);
    return 0;
  }

  public static void main(String[] args) throws Exception {
    UnionJob runJob = new UnionJob();
    runJob.run(args);
  }
}
