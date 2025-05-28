package io.ebean.datasource.pool;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An LRU based cache for PreparedStatements.
 */
final class PstmtCache extends LinkedHashMap<String, ExtendedPreparedStatement> {

  private static final long serialVersionUID = -3096406924865550697L;

  private final int maxSize;
  private long removeCount;
  private long hitCount;
  private long missCount;

  PstmtCache(int maxCacheSize) {
    // note = access ordered list.  This is what gives it the LRU order
    super(maxCacheSize * 3, 0.75f, true);
    this.maxSize = maxCacheSize;
  }

  /**
   * Return a summary description of this cache.
   */
  String description() {
    return "size[" + size() + "] max[" + maxSize + "] hits[" + hitCount + "] miss[" + missCount + "] hitRatio[" + hitRatio() + "] removes[" + removeCount + "]";
  }

  /**
   * Gets the hit ratio (A number between 0 and 100).
   */
  private long hitRatio() {
    if (hitCount == 0) {
      return 0;
    } else {
      return hitCount * 100 / (hitCount + missCount);
    }
  }

  long hitCount() {
    return hitCount;
  }

  long missCount() {
    return missCount;
  }

  long removeCount() {
    return removeCount;
  }

  /**
   * Try to add the returning statement to the cache. If there is already a
   * matching ExtendedPreparedStatement in the cache return false else add
   * the statement to the cache and return true.
   */
  boolean returnStatement(ExtendedPreparedStatement stmt) {
    ExtendedPreparedStatement alreadyInCache = super.get(stmt.cacheKey());
    if (alreadyInCache != null) {
      return false;
    }
    try {
      // before putting a statement back to the cache, we will clear the parameters, batch and warnings
      stmt.clearParameters();
      stmt.clearBatch();
      stmt.clearWarnings();
    } catch (SQLException e) {
      Log.error("Error clearing PreparedStatement", e);
      return false;
    }
    // add the returning prepared statement to the cache.
    // Note that the LRUCache will automatically close fully old unused
    // statements when the cache has hit its maximum size.
    put(stmt.cacheKey(), stmt);
    return true;
  }

  @Override
  public ExtendedPreparedStatement get(Object key) {
    ExtendedPreparedStatement o = super.get(key);
    if (o == null) {
      missCount++;
    } else {
      hitCount++;
    }
    return o;
  }

  @Override
  public ExtendedPreparedStatement remove(Object key) {
    ExtendedPreparedStatement o = super.remove(key);
    if (o == null) {
      missCount++;
    } else {
      hitCount++;
    }
    return o;
  }

  @Override
  protected boolean removeEldestEntry(Map.Entry<String, ExtendedPreparedStatement> eldest) {
    if (size() < maxSize) {
      return false;
    }
    removeCount++;
    try {
      ExtendedPreparedStatement stmt = eldest.getValue();
      stmt.closeDestroy();
    } catch (SQLException e) {
      Log.error("Error closing ExtendedPreparedStatement", e);
    }
    return true;
  }

}

