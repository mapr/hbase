package org.apache.hadoop.hbase.mapreduce.mapr;

import java.io.IOException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableReducer;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;

public class UnionReducer extends TableReducer<Text, IntWritable, ImmutableBytesWritable>{

  static Log LOG = LogFactory.getLog(UnionReducer.class);

  @Override
  public void reduce(Text key, Iterable<IntWritable> values, Context context) {
    LOG.info("Enter Reducer key("+key.toString());

    if (key.toString().startsWith("s")) {
      Integer dayStoreSales = 0;
      for (IntWritable storeSale : values) {
        dayStoreSales = dayStoreSales + new Integer(storeSale.toString());
      }
      Put put = new Put(Bytes.toBytes(key.toString()));
      put.add(Bytes.toBytes("cf1"), Bytes.toBytes("tSales"), Bytes.toBytes(dayStoreSales));
      try {
        LOG.info("Process Reducer write record("+put+")");
        context.write(null, put);
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    } else {
      Integer dayStoreSales = 0;
      for (IntWritable storeSale : values) {
        dayStoreSales = dayStoreSales + new Integer(storeSale.toString());
      }
      Put put = new Put(Bytes.toBytes(key.toString()));
      put.add(Bytes.toBytes("cf1"), Bytes.toBytes("tSales"), Bytes.toBytes(dayStoreSales));
      try {
        LOG.info("Process Reducer write record("+put+")");
        context.write(null, put);
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }
}
