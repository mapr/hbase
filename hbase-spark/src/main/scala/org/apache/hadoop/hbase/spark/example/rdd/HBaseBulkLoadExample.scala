package org.apache.hadoop.hbase.spark.example.rdd

import org.apache.hadoop.fs.Path
import org.apache.hadoop.hbase.mapreduce.LoadIncrementalHFiles
import org.apache.hadoop.hbase.{HBaseConfiguration, TableName}
import org.apache.hadoop.hbase.spark.{HBaseConnectionCache, HBaseContext, KeyFamilyQualifier}
import org.apache.hadoop.hbase.spark.HBaseRDDFunctions._
import org.apache.hadoop.hbase.util.Bytes._
import org.apache.spark.SparkContext

object HBaseBulkLoadExample {
  def main(args: Array[String]): Unit = {
    val sc = new SparkContext("local", "test")
    val config = new HBaseConfiguration()

    val hbaseContext = new HBaseContext(sc, config)

    val tableName = args(0)
    val stagingFolder = "/home/mapr"
    val columnFamily1 = "cf1"
    val rdd = sc.parallelize(Array(
      (toBytes("1"), (toBytes(columnFamily1), toBytes("a"), toBytes("foo1"))),
      (toBytes("3"), (toBytes(columnFamily1), toBytes("b"), toBytes("foo2.b")))
    ))

    rdd.hbaseBulkLoad(hbaseContext,
      TableName.valueOf(tableName),
      t => {
        val rowKey = t._1
        val family: Array[Byte] = t._2._1
        val qualifier = t._2._2
        val value: Array[Byte] = t._2._3

        val keyFamilyQualifier= new KeyFamilyQualifier(rowKey, family, qualifier)

        Seq((keyFamilyQualifier, value)).iterator
      },
      stagingFolder)

    val connection = HBaseConnectionCache.getConnection(config)
    val table = connection.getTable(TableName.valueOf(tableName))
    val load = new LoadIncrementalHFiles(config)
    load.doBulkLoad(
      new Path(stagingFolder),
      connection.getAdmin,
      table,
      connection.getRegionLocator(TableName.valueOf(tableName)))
  }
}
