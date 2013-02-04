package ezdb.leveldb;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.fusesource.leveldbjni.JniDBFactory;
import org.iq80.leveldb.Options;
import ezdb.Db;
import ezdb.DbException;
import ezdb.RangeTable;
import ezdb.Table;
import ezdb.comparator.LexicographicalComparator;
import ezdb.serde.ByteSerde;
import ezdb.serde.Serde;

/**
 * An implementation of Db that uses LevelDb tables to persist data. Each
 * "table" is just a LevelDB database persisted as a subdirectory inside of
 * EzLevelDb's root.
 * 
 * @author criccomini
 * 
 */
public class EzLevelDb implements Db {
  private final File root;
  private final Map<String, RangeTable<?, ?, ?>> cache;

  public EzLevelDb(File root) {
    this.root = root;
    this.cache = new HashMap<String, RangeTable<?, ?, ?>>();
  }

  @Override
  public void deleteTable(String tableName) {
    try {
      synchronized(cache) {
        cache.remove(tableName);
        JniDBFactory.factory.destroy(getFile(tableName), new Options());
      }
    } catch (IOException e) {
      throw new DbException(e);
    }
  }

  @Override
  public <H, V> Table<H, V> getTable(String tableName, Serde<H> hashKeySerde, Serde<V> valueSerde) {
    return getTable(tableName, hashKeySerde, ByteSerde.get, valueSerde);
  }

  @Override
  public <H, R, V> RangeTable<H, R, V> getTable(
      String tableName,
      Serde<H> hashKeySerde,
      Serde<R> rangeKeySerde,
      Serde<V> valueSerde) {
    return getTable(tableName, hashKeySerde, rangeKeySerde, valueSerde, new LexicographicalComparator(), new LexicographicalComparator());
  }

  @SuppressWarnings("unchecked")
  @Override
  public <H, R, V> RangeTable<H, R, V> getTable(
      String tableName,
      Serde<H> hashKeySerde,
      Serde<R> rangeKeySerde,
      Serde<V> valueSerde,
      Comparator<byte[]> hashKeyComparator,
      Comparator<byte[]> rangeKeyComparator) {
    synchronized (cache) {
      RangeTable<?, ?, ?> table = cache.get(tableName);

      if (table == null) {
        table = new EzLevelDbTable<H, R, V>(new File(root, tableName), hashKeySerde, rangeKeySerde, valueSerde, hashKeyComparator, rangeKeyComparator);
        cache.put(tableName, table);
      }

      return (RangeTable<H, R, V>) table;
    }
  }

  /**
   * A helper method used to convert a table name to the location on disk where
   * this LevelDB database will be persisted.
   * 
   * @param tableName
   *          The logical name of the table.
   * @return The physical location of the directory where this table should be
   *         persisted.
   */
  private File getFile(String tableName) {
    return new File(root, tableName);
  }
}
