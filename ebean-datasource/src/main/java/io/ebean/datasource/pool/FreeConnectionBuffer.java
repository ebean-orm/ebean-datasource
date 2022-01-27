package io.ebean.datasource.pool;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * A buffer designed especially to hold free pooled connections.
 * <p>
 * All thread safety controlled externally (by PooledConnectionQueue).
 * </p>
 */
final class FreeConnectionBuffer {

  /**
   * Buffer oriented for add and remove.
   */
  private final LinkedList<PooledConnection> freeBuffer = new LinkedList<>();

  FreeConnectionBuffer() {
  }

  /**
   * Return the number of entries in the buffer.
   */
  int size() {
    return freeBuffer.size();
  }

  /**
   * Return true if the buffer is empty.
   */
  boolean isEmpty() {
    return freeBuffer.isEmpty();
  }

  /**
   * Add connection to the free list.
   */
  void add(PooledConnection pc) {
    freeBuffer.addFirst(pc);
  }

  /**
   * Remove a connection from the free list.
   */
  PooledConnection remove() {
    return freeBuffer.removeFirst();
  }

  /**
   * Close all connections in this buffer.
   */
  void closeAll(boolean logErrors) {
    List<PooledConnection> tempList = new ArrayList<>(freeBuffer);
    freeBuffer.clear();
    if (Log.isLoggable(System.Logger.Level.TRACE)) {
      Log.trace("... closing all %s connections from the free list with logErrors: %s", tempList.size(), logErrors);
    }
    for (PooledConnection connection : tempList) {
      connection.closeConnectionFully(logErrors);
    }
  }

  /**
   * Trim any inactive connections that have not been used since usedSince.
   */
  int trim(long usedSince, long createdSince) {
    int trimCount = 0;
    Iterator<PooledConnection> iterator = freeBuffer.iterator();
    while (iterator.hasNext()) {
      PooledConnection pooledConnection = iterator.next();
      if (pooledConnection.shouldTrim(usedSince, createdSince)) {
        iterator.remove();
        pooledConnection.closeConnectionFully(true);
        trimCount++;
      }
    }
    return trimCount;
  }
}
