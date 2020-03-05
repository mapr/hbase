package org.apache.hadoop.hbase.client.mapr;

import org.apache.hadoop.security.UserGroupInformation;

import java.io.IOException;
import java.security.PrivilegedExceptionAction;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;


public class TestImpersonate {
  static void createTable98(String user1, final String tableName) {

    try { 
      UserGroupInformation ugi1 = UserGroupInformation
          .createProxyUser(user1,
              UserGroupInformation.getLoginUser());
      ugi1.doAs(new PrivilegedExceptionAction<Void>() {
        public Void run() throws Exception {
          try { 
            // create table
            Configuration config = HBaseConfiguration.create();
            HBaseAdmin admin = new HBaseAdmin(config);
            HTableDescriptor tableDescriptor = new HTableDescriptor(
                tableName.getBytes());
            tableDescriptor.addFamily(new HColumnDescriptor("cf1".getBytes()));
            admin.createTable(tableDescriptor);

            // retrieve a handle to the target table.
            HTable table = new HTable(config, tableName);
            // describe the data we want to write.
            Put p = new Put(Bytes.toBytes("row1"));
            p.addColumn("cf1".getBytes(), Bytes.toBytes("col98"), Bytes.toBytes("val11"));
            table.put(p);

            admin.close();
            table.close();
          } catch (Exception ie) {
            System.out.print("Create Table failed. Reason:");
            ie.printStackTrace();
          }
          return null; 
        }
      });
    } catch (Exception ie) {
      System.out.print("Impersonate faile. Reason:");
      ie.printStackTrace();
    } finally {
      System.out.print("Done Impersonation.");
    }
  }

  static void createTable11(String user1, final String tableName) {

    try { 
      UserGroupInformation ugi1 = UserGroupInformation
          .createProxyUser(user1,
              UserGroupInformation.getLoginUser());
      ugi1.doAs(new PrivilegedExceptionAction<Void>() {
        public Void run() throws Exception {
          try { 
            // create table
            Configuration config = HBaseConfiguration.create();
            Connection connection = ConnectionFactory.createConnection(config);
            Admin admin = connection.getAdmin();
            HTableDescriptor tableDescriptor = new HTableDescriptor(
                tableName.getBytes());
            tableDescriptor.addFamily(new HColumnDescriptor("cf1".getBytes()));
            admin.createTable(tableDescriptor);

            Table table = connection.getTable(TableName.valueOf(tableName));
            // describe the data we want to write.
            Put p = new Put(Bytes.toBytes("row1"));
            p.addColumn("cf1".getBytes(), Bytes.toBytes("col11"), Bytes.toBytes("val11"));
            table.put(p);

            admin.close();
            table.close();
            connection.close();
          } catch (Exception ie) {
            System.out.print("Create Table failed. Reason:");
            ie.printStackTrace();
          }
          return null;
        }
      });
    } catch (Exception ie) {
      System.out.print("Impersonate faile. Reason:");
      ie.printStackTrace();
    } finally {
      System.out.print("Done Impersonation.");
    }
  }

  public static void main(String[] argv) throws Exception {
    if (argv.length < 3) {
      System.out.println("Usage: hbase org.apache.hadoop.hbase.client.mapr.TestImpersonate tableName userName version\n");
      System.exit(-1);
    }
    String tableName = argv[0];
    String userName = argv[1];
    String version = argv[2];
    if (version.equals("98")) {
      System.out.println("test hbase 98 style");
      createTable98(userName, tableName);
    } else if (version.equals("11")) {
      System.out.println("test hbase 11 style");
      createTable11(userName, tableName);
    } else {
      System.out.println("version should be either 11 or 98");
      System.exit(1);
    }
  }
}
