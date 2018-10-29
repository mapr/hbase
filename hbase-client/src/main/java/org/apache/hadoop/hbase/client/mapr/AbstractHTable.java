package org.apache.hadoop.hbase.client.mapr;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.HRegionLocation;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.client.Append;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Durability;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Increment;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Row;
import org.apache.hadoop.hbase.client.RowMutations;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.coprocessor.Batch;
import org.apache.hadoop.hbase.client.coprocessor.Batch.Callback;
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp;
import org.apache.hadoop.hbase.ipc.CoprocessorRpcChannel;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Pair;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.Service;
import com.google.protobuf.ServiceException;

/**
 * This class defines public methods implemented in {@link HTable} class
 */
public abstract class AbstractHTable {

  private final static Log LOG = LogFactory.getLog(AbstractHTable.class);

  public abstract void batch(List<? extends Row> actions, Object[] results)
      throws IOException, InterruptedException;

  public abstract Object[] batch(List<? extends Row> actions)
      throws IOException, InterruptedException;

  public ResultScanner getScanner(byte[] family) throws IOException {
    Scan scan = new Scan();
    scan.addFamily(family);
    return getScanner(scan);
  }

  public ResultScanner getScanner(byte[] family, byte[] qualifier)
      throws IOException {
    Scan scan = new Scan();
    scan.addColumn(family, qualifier);
    return getScanner(scan);
  }

  public HRegionLocation getRegionLocation(final String row) throws IOException {
    return getRegionLocation(Bytes.toBytes(row));
  }

  public abstract HRegionLocation getRegionLocation(final byte[] row)
      throws IOException;

  public HRegionLocation getRegionLocation(byte[] row, boolean reload)
      throws IOException {
    return getRegionLocation(row);
  }

  public byte[][] getStartKeys() throws IOException {
    return getStartEndKeys().getFirst();
  }

  public byte[][] getEndKeys() throws IOException {
    return getStartEndKeys().getSecond();
  }

  public abstract Pair<byte[][],byte[][]> getStartEndKeys() throws IOException;

  public abstract NavigableMap<HRegionInfo, ServerName> getRegionLocations()
      throws IOException;

  public long getWriteBufferSize() {
    return 0;
  }

  public void setWriteBufferSize(long writeBufferSize) throws IOException {
    /* NO-OP */
  }

  public abstract byte[] getTableName();

  public abstract Configuration getConfiguration();

  public abstract HTableDescriptor getTableDescriptor() throws IOException;

  public abstract boolean exists(Get get) throws IOException;

  public abstract Boolean[] exists(List<Get> gets) throws IOException;

  public abstract Result get(Get get) throws IOException;

  public abstract Result[] get(List<Get> gets) throws IOException;

  public abstract Result getRowOrBefore(byte[] row, byte[] family)
      throws IOException;

  public abstract ResultScanner getScanner(Scan scan) throws IOException;

  public abstract void put(Put put) throws InterruptedIOException;

  public abstract void put(List<Put> puts) throws InterruptedIOException;

  public abstract boolean checkAndPut(byte[] row, byte[] family, 
      byte[] qualifier, byte[] value, Put put) throws IOException;

  public boolean checkAndPut(byte [] row, byte [] family,
      byte [] qualifier, CompareOp compareOp, byte [] value,
      Put put) throws IOException {
    throw new UnsupportedOperationException("checkAndDelete() is not supported for this version of MapR-DB tables.");
  }

  public abstract void delete(Delete delete) throws IOException;

  public abstract void delete(List<Delete> deletes) throws IOException;

  public abstract boolean checkAndDelete(byte[] row, byte[] family,
      byte[] qualifier, byte[] value, Delete delete) throws IOException;

  public boolean checkAndDelete(byte [] row, byte [] family,
      byte [] qualifier, CompareOp compareOp, byte [] value,
      Delete delete) throws IOException {
    throw new UnsupportedOperationException("checkAndDelete() is not supported for this version of MapR-DB tables.");
  }

  public abstract void mutateRow(RowMutations rm) throws IOException;

  public abstract Result append(Append append) throws IOException;

  public abstract Result increment(Increment increment) throws IOException;

  public abstract long incrementColumnValue(byte[] row, byte[] family,
      byte[] qualifier, long amount) throws IOException;

  public abstract long incrementColumnValue(byte[] row, byte[] family,
      byte[] qualifier, long amount, boolean writeToWAL) throws IOException;

  public abstract long incrementColumnValue(byte[] row, byte[] family,
      byte[] qualifier, long amount, Durability durability) throws IOException;

  public abstract boolean isAutoFlush();

  public abstract void flushCommits() throws InterruptedIOException;

  public abstract void close() throws IOException;

  public abstract void setAutoFlush(boolean autoFlush);

  public abstract void setAutoFlush(boolean autoFlush, boolean clearBufferOnFail);

  public boolean checkAndMutate(byte[] row, byte[] family, byte[] qualifier,
      CompareOp compareOp, byte[] value, RowMutations rm) throws IOException {
    throw new UnsupportedOperationException("checkAndMutate() is not supported for this version of MapR-DB tables.");
  }

  /**
   * <b>NO-OP for MapR Tables</b><p>
   * Explicitly clears the region cache to fetch the latest value from META.
   * This is a power user function: avoid unless you know the ramifications.
   */
  public void clearRegionCache() {
    LOG.warn("clearRegionCache() called for a MapR Table, silently ignoring.");
  }
  /**
   * <b>NO-OP for MapR Tables, returns <code>null</code>.</b><p>
   * {@inheritDoc}
   */
  public CoprocessorRpcChannel coprocessorService(byte[] row) {
    throw new UnsupportedOperationException("coprocessorService is not supported for MapR.");
  }

  public <T extends Service, R> Map<byte[], R> coprocessorService(final Class<T> service,
      byte[] startKey, byte[] endKey, final Batch.Call<T,R> callable)
      throws ServiceException, Throwable {
    throw new UnsupportedOperationException("coprocessorService is not supported for MapR.");
  }

  public <T extends Service, R> void coprocessorService(final Class<T> service,
      byte[] startKey, byte[] endKey, final Batch.Call<T,R> callable,
      final Batch.Callback<R> callback) throws ServiceException, Throwable {
    throw new UnsupportedOperationException("coprocessorService is not supported for MapR.");
  }

  /**
   * {@inheritDoc}
   */
  public <R extends Message> Map<byte[], R> batchCoprocessorService(
      Descriptors.MethodDescriptor methodDescriptor, Message request,
      byte[] startKey, byte[] endKey, R responsePrototype) throws ServiceException, Throwable {
    throw new UnsupportedOperationException("coprocessorService is not supported for MapR.");
  }

  /**
   * {@inheritDoc}
   */
  public <R extends Message> void batchCoprocessorService(
      final Descriptors.MethodDescriptor methodDescriptor, final Message request,
      byte[] startKey, byte[] endKey, final R responsePrototype, final Callback<R> callback)
      throws ServiceException, Throwable {
    throw new UnsupportedOperationException("coprocessorService is not supported for MapR.");
  }

}
