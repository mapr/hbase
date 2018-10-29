package org.apache.hadoop.hbase.mapreduce.mapr;

import java.util.Arrays;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.hbase.mapreduce.TableSplit;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;


public class UnionMapper extends TableMapper<Text, IntWritable> {
  static Log LOG = LogFactory.getLog(UnionMapper.class);

  byte[] sales;
  String storeSales;
  Integer sSales;
  String onlineSales;
  Integer oSales;
  Text mapperKey;
  IntWritable mapperValue;

  @Override
  public void map(ImmutableBytesWritable rowKey, Result columns, Context context) {
    // get table name
    TableSplit currentSplit = (TableSplit)context.getInputSplit();
    byte[] tableName = currentSplit.getTableName();

    //LOG.info("Mapper table "+String.valueOf(tableName)+" record("+rowKey.toString()
      //      +"\n UnionJob.MapRTName1("+String.valueOf(UnionJob.MapRTName1)+")"+", UnionJob.MapRTName2("+String.valueOf(UnionJob.MapRTName2)+")"
      //      +"\n UnionJob.HBaseTName1("+String.valueOf(UnionJob.HBaseTName1)+")"+", UnionJob.HBaseTName2("+String.valueOf(UnionJob.HBaseTName2)+")");

    String strTableName = Bytes.toStringBinary(tableName);
    LOG.info("Enter Mapper table("+strTableName+") record("+Bytes.toStringBinary(rowKey.get())
          +")\n UnionJob.MapRTName1("+String.valueOf(UnionJob.MapRTName1)+")"+", UnionJob.MapRTName2("+String.valueOf(UnionJob.MapRTName2)+")"
          +"\n UnionJob.HBaseTName1("+String.valueOf(UnionJob.HBaseTName1)+")"+", UnionJob.HBaseTName2("+String.valueOf(UnionJob.HBaseTName2)+")");

    try {
      if (strTableName.equals(UnionJob.MapRTName1) || strTableName.equals(UnionJob.HBaseTName1) ) {

        String date = new String(rowKey.get()).split("#")[0];
        sales = columns.getValue(Bytes.toBytes("cf1"), Bytes.toBytes("sSales"));
        storeSales = new String(sales);
        sSales = new Integer(storeSales);
        mapperKey = new Text("s#" + date);
        mapperValue = new IntWritable(sSales);
        LOG.info("Process Mapper table "+tableName+" record("+mapperKey+", "+mapperValue.toString()+")");
        context.write(mapperKey, mapperValue);
      } else if (strTableName.equals(UnionJob.MapRTName2) || strTableName.equals(UnionJob.HBaseTName2) ) {
        String date = new String(rowKey.get());
        sales = columns.getValue(Bytes.toBytes("cf2"), Bytes.toBytes("oSales"));
        onlineSales = new String(sales);
        Integer oSales = new Integer(onlineSales);
        mapperKey = new Text("o#"+date);
        mapperValue = new IntWritable(oSales);
        LOG.info("Process Mapper table "+tableName+" record("+mapperKey+", "+mapperValue.toString()+")");
        context.write(mapperKey, mapperValue);
      } else {
        LOG.info(strTableName + "does not much any table.");
      }
    } catch (Exception e) {
      // TODO : exception handling logic
      e.printStackTrace();
    }
  }
}
