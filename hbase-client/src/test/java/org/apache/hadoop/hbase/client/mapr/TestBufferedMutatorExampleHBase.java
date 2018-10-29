package org.apache.hadoop.hbase.client.mapr;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.BufferedMutator;
import org.apache.hadoop.hbase.client.BufferedMutatorParams;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RetriesExhaustedWithDetailsException;
import org.apache.hadoop.hbase.client.Row;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An example of using the {@link BufferedMutator} interface.
 */
public class TestBufferedMutatorExampleHBase extends Configured implements Tool {

  /** The name of the column family used by the application. */
  private static final byte[] CF1 = Bytes.toBytes("cf1");
  private static final byte[] CF2 = Bytes.toBytes("cf2");

  private static final Log LOG = LogFactory.getLog(TestBufferedMutatorExampleHBase.class);

  private static final int POOL_SIZE = 10;
  private static final int TASK_COUNT = 100;
  private static final byte[] FAMILY = Bytes.toBytes("f");

  public static class SinglePut implements Callable<Void> {
    private int rowid;
    private BufferedMutator mutator;
    
    public SinglePut(int rowid, BufferedMutator mutator){
        this.rowid = rowid;
        this.mutator = mutator;
    }

    public Void call() throws Exception {
        putarow(rowid);
        return null;
    }

    private void putarow(int n) throws Exception {
      Put p = new Put(Bytes.toBytes("Row"+n));
      p.addColumn("cf3".getBytes(), Bytes.toBytes("qual1"), Bytes.toBytes("value1"));
      mutator.mutate(p);
      // do work... maybe you want to call mutator.flush() after many edits to ensure any of
      // this worker's edits are sent before exiting the Callable
      mutator.flush();
    }

}


  @Override
  public int run(String[] argv) throws InterruptedException, ExecutionException, TimeoutException {

    if (argv.length < 1) {
      System.out.println("Test the table operations with HBase 1.1 style. Usage:\n"
                         +"To test in MapR DB\n"
                         + "\t hbase org.apache.hadoop.hbase.client.TestBufferedMutatorExampleHBase tableName MapRDB\n"
                         +"To test in HBase\n"
                         + "\t hbase org.apache.hadoop.hbase.client.TestBufferedMutatorExampleHBase tableName HBbase");
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


    /** a callback invoked when an asynchronous write fails. */
    final BufferedMutator.ExceptionListener listener = new BufferedMutator.ExceptionListener() {
      @Override
      public void onException(RetriesExhaustedWithDetailsException e, BufferedMutator mutator) {
        for (int i = 0; i < e.getNumExceptions(); i++) {
          Row row = e.getRow(i);
          LOG.info("Failed to sent put " + row + ".");
        }
      }
    };
    BufferedMutatorParams params = new BufferedMutatorParams(tableName).listener(listener);
    try (final Connection conn = ConnectionFactory.createConnection(conf);
        final BufferedMutator mutator = conn.getBufferedMutator(params)) {

    //
    // step 1: create a single Connection and a BufferedMutator, shared by all worker threads.
    //

      /** worker pool that operates on BufferedTable instances */
      ExecutorService workerPool = Executors.newFixedThreadPool(POOL_SIZE);
      List<Future<Void>> futures = new ArrayList<>(TASK_COUNT);

      for (int i = 0; i < TASK_COUNT; i++) {
        futures.add(workerPool.submit(new SinglePut(i, mutator)));
      }

      //
      // step 3: clean up the worker pool, shut down.
      //
      for (Future<Void> f : futures) {
        f.get(5, TimeUnit.MINUTES);
      }
      workerPool.shutdown();

    } catch (IOException e) {
      // exception while creating/destroying Connection or BufferedMutator
      LOG.info("exception while creating/destroying Connection or BufferedMutator", e);
    } // BufferedMutator.close() ensures all work is flushed. Could be the custom listener is
      // invoked from here.
    return 0;
  }

  public static void main(String[] args) throws Exception {
    ToolRunner.run(new TestBufferedMutatorExampleHBase(), args);
  }
}
