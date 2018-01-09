package org.avaje.datasource.pool;

import org.avaje.datasource.pool.PooledConnectionStatistics.LoadValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
class FreeConnectionBuffer {

  private static final Logger logger = LoggerFactory.getLogger(FreeConnectionBuffer.class);

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
    freeBuffer.addLast(pc);
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

    // create a temporary list
    List<PooledConnection> tempList = new ArrayList<>(freeBuffer.size());

    // add all the connections into it
    tempList.addAll(freeBuffer);

    // clear the buffer (in case it takes some time to close these connections).
    freeBuffer.clear();

    logger.debug("... closing all {} connections from the free list with logErrors: {}", tempList.size(), logErrors);
    for (int i = 0; i < tempList.size(); i++) {
      PooledConnection pooledConnection = tempList.get(i);
      logger.debug("... closing {} of {} connections from the free list", i, tempList.size());
      pooledConnection.closeConnectionFully(logErrors);
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

  /**
   * Collect the load statistics from all the free connections.
   */
  void collectStatistics(LoadValues values, boolean reset) {

    for (PooledConnection c : freeBuffer) {
      values.plus(c.getStatistics().getValues(reset));
    }
  }
}
