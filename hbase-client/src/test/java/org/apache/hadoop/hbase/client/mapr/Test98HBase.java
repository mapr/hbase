package org.apache.hadoop.hbase.client.mapr;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.NeedUnmanagedConnectionException;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import java.io.IOException;

public class Test98HBase extends Configured implements Tool {
  /** The name of the column family used by the application. */
  private static final byte[] CF1 = Bytes.toBytes("cf1");
  private static final byte[] CF2 = Bytes.toBytes("cf2");

  public int run(String[] argv) throws IOException {
    if (argv.length < 1) {
      System.out.println("Test the table operations with HBase 0.98 style. Usage:\n"
                         + "\t hbase org.apache.hadoop.hbase.client.Test98HBase tableName");
      return -1;
    }

    String tableName = argv[0];

    Configuration config = HBaseConfiguration.create();
    String defaultDb = config.get("mapr.hbase.default.db", org.apache.hadoop.hbase.client.mapr.TableMappingRulesFactory.UNSETDB);
    System.out.println("mapr.hbase.default.db=" + defaultDb);

    HTableInterface table = null;
    Table table2 = null;
    HBaseAdmin admin = null;
    Admin admin2 = null;

    try {
      admin = new HBaseAdmin(config);
      if (admin.tableExists(tableName)) {
        admin.disableTable(tableName);
        admin.deleteTable(tableName);
      }
      // retrieve a handle to the target table.
      HTableDescriptor tableDesc = new HTableDescriptor(tableName);
      HColumnDescriptor hcd1 = new HColumnDescriptor(CF1);
      HColumnDescriptor hcd2 = new HColumnDescriptor(CF2);
      tableDesc.addFamily(hcd1);
      tableDesc.addFamily(hcd2);
      admin.createTable(tableDesc);

      // retrieve a handle to the target table.
      table = new HTable(config, tableName);
      // describe the data we want to write.
      Put p = new Put(Bytes.toBytes("row1"));
      p.addColumn(CF1, Bytes.toBytes("col1"), Bytes.toBytes("val11"));
      p.addColumn(CF2, Bytes.toBytes("col2"), Bytes.toBytes("val12"));
      table.put(p);
      Put p2 = new Put(Bytes.toBytes("row2"));
      p2.addColumn(CF1, Bytes.toBytes("col2"), Bytes.toBytes("val21"));
      p2.addColumn(CF2, Bytes.toBytes("col1"), Bytes.toBytes("val22"));
      table.put(p2);

      Scan scan = new Scan();
      System.out.println("-----After put-----");
      ResultScanner scanner1 = table.getScanner(scan);
      for (Result result = scanner1.next(); (result != null); result = scanner1.next()) {
        for(KeyValue keyValue : result.list()) {
            System.out.println("Qualifier : " + keyValue.getKeyString() + " : Value : " + Bytes.toString(keyValue.getValue()));
        }
      }

      Delete del = new Delete(Bytes.toBytes("row1"));
      del.addColumn(CF1, Bytes.toBytes("col1"));
      table.delete(del);

      Delete del2 = new Delete(Bytes.toBytes("row2"));
      del2.addFamily(CF2);
      table.delete(del2);

      System.out.println("-----After delete-----");
      ResultScanner scanner2 = table.getScanner(scan);
      for (Result result = scanner2.next(); (result != null); result = scanner2.next()) {
        for(KeyValue keyValue : result.list()) {
            System.out.println("Qualifier : " + keyValue.getKeyString() + " : Value : " + Bytes.toString(keyValue.getValue()));
        }
      }

      System.out.println("-----create another admin through the connection should NOT work-----");
      try {
        admin2 = admin.getConnection().getAdmin();
      } catch (NeedUnmanagedConnectionException e) {
        System.out.println("-----NeedUnmanagedConnectionException is thrown as expected-----");
      }
      System.out.println("-----create another admin through the connection should NOT work-----");
      try {
        table2 = admin.getConnection().getTable(tableName);
      } catch (NeedUnmanagedConnectionException e) {
        System.out.println("-----NeedUnmanagedConnectionException is thrown as expected-----");
      }
    } finally {
      // close everything down
      if (table != null)
        table.close();
      if (admin != null)
        admin.close();
      if (admin2 != null)
        admin2.close();
      if (table2 != null)
        table2.close();
    }
    return 0;
  }

  public static void main(String[] argv) throws Exception {
    int ret = ToolRunner.run(new Test98HBase(), argv);
    System.exit(ret);
  }
}
