package io.ebean.datasource.pool;

import java.util.*;

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
      Log.trace("... closing all {0} connections from the free list with logErrors: {1}", tempList.size(), logErrors);
    }
    for (PooledConnection connection : tempList) {
      connection.closeConnectionFully(logErrors);
    }
  }

  /**
   * Trim any inactive connections that have not been used since usedSince.
   */
  int trim(int minSize, long usedSince, long createdSince, boolean forced) {
    int trimCount = 0;
    ListIterator<PooledConnection> iterator = freeBuffer.listIterator(minSize);
    while (iterator.hasNext()) {
      PooledConnection pooledConnection = iterator.next();
      if (pooledConnection.shouldTrim(usedSince, createdSince, forced)) {
        iterator.remove();
        pooledConnection.closeConnectionFully(true);
        trimCount++;
      }
    }
    return trimCount;
  }
}
