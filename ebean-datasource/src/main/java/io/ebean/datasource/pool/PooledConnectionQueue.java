package io.ebean.datasource.pool;

import io.ebean.datasource.PoolStatus;
import io.ebean.datasource.pool.ConnectionPool.Status;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.System.Logger.Level.DEBUG;

final class PooledConnectionQueue {

  private static final TimeUnit MILLIS_TIME_UNIT = TimeUnit.MILLISECONDS;

  private final String name;
  private final ConnectionPool pool;
  /**
   * A 'circular' buffer designed specifically for free connections.
   */
  private final FreeConnectionBuffer freeList;
  /**
   * A 'slots' buffer designed specifically for busy connections.
   * Fast add remove based on slot id.
   */
  private final BusyConnectionBuffer busyList;
  /**
   * Main lock guarding all access
   */
  private final ReentrantLock lock;
  /**
   * Condition for threads waiting to take a connection
   */
  private final Condition notEmpty;
  private int connectionId;
  private final long waitTimeoutMillis;
  private final long leakTimeMinutes;
  private final long maxAgeMillis;
  private int warningSize;
  private int maxSize;
  private int minSize;
  /**
   * Number of threads in the wait queue.
   */
  private int waitingThreads;
  /**
   * Number of times a thread had to wait.
   */
  private int waitCount;
  /**
   * Number of times a connection was got from this queue.
   */
  private int hitCount;
  /**
   * The high water mark for the queue size.
   */
  private int highWaterMark;
  /**
   * Last time the pool was reset. Used to close busy connections as they are
   * returned to the pool that where created prior to the lastResetTime.
   */
  private long lastResetTime;
  private boolean doingShutdown;

  PooledConnectionQueue(ConnectionPool pool) {
    this.pool = pool;
    this.name = pool.name();
    this.minSize = pool.getMinSize();
    this.maxSize = pool.getMaxSize();
    this.warningSize = pool.getWarningSize();
    this.waitTimeoutMillis = pool.waitTimeoutMillis();
    this.leakTimeMinutes = pool.leakTimeMinutes();
    this.maxAgeMillis = pool.maxAgeMillis();
    this.busyList = new BusyConnectionBuffer(maxSize, 20);
    this.freeList = new FreeConnectionBuffer();
    this.lock = new ReentrantLock(false);
    this.notEmpty = lock.newCondition();
  }

  private PoolStatus createStatus() {
    return new Status(minSize, maxSize, freeList.size(), busyList.size(), waitingThreads, highWaterMark, waitCount, hitCount);
  }

  @Override
  public String toString() {
    lock.lock();
    try {
      return createStatus().toString();
    } finally {
      lock.unlock();
    }
  }

  PoolStatus status(boolean reset) {
    lock.lock();
    try {
      PoolStatus s = createStatus();
      if (reset) {
        highWaterMark = busyList.size();
        hitCount = 0;
        waitCount = 0;
      }
      return s;
    } finally {
      lock.unlock();
    }
  }

  void setMinSize(int minSize) {
    lock.lock();
    try {
      if (minSize > this.maxSize) {
        throw new IllegalArgumentException("minSize " + minSize + " > maxSize " + this.maxSize);
      }
      this.minSize = minSize;
    } finally {
      lock.unlock();
    }
  }

  void setMaxSize(int maxSize) {
    lock.lock();
    try {
      if (maxSize < this.minSize) {
        throw new IllegalArgumentException("maxSize " + maxSize + " < minSize " + this.minSize);
      }
      this.busyList.setCapacity(maxSize);
      this.maxSize = maxSize;
    } finally {
      lock.unlock();
    }
  }

  void setWarningSize(int warningSize) {
    lock.lock();
    try {
      if (warningSize > this.maxSize) {
        throw new IllegalArgumentException("warningSize " + warningSize + " > maxSize " + this.maxSize);
      }
      this.warningSize = warningSize;
    } finally {
      lock.unlock();
    }
  }

  private int totalConnections() {
    return freeList.size() + busyList.size();
  }

  void ensureMinimumConnections() throws SQLException {
    lock.lock();
    try {
      int add = minSize - totalConnections();
      if (add > 0) {
        for (int i = 0; i < add; i++) {
          freeList.add(pool.createConnectionForQueue(connectionId++));
        }
        notEmpty.signal();
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Return a PooledConnection.
   */
  void returnPooledConnection(PooledConnection c, boolean forceClose) {
    lock.lock();
    try {
      if (!busyList.remove(c)) {
        Log.error("Connection [{0}] not found in BusyList?", c);
      }
      if (forceClose || c.shouldTrimOnReturn(lastResetTime, maxAgeMillis)) {
        c.closeConnectionFully(false);

      } else {
        freeList.add(c);
        notEmpty.signal();
      }
    } finally {
      lock.unlock();
    }
  }

  private PooledConnection extractFromFreeList() {
    PooledConnection c = freeList.remove();
    registerBusyConnection(c);
    return c;
  }

  PooledConnection obtainConnection() throws SQLException {
    try {
      PooledConnection pc = _obtainConnection();
      pc.resetForUse();
      return pc;

    } catch (InterruptedException e) {
      // restore the interrupted status as we throw SQLException
      Thread.currentThread().interrupt();
      throw new SQLException("Interrupted getting connection from pool", e);
    }
  }

  /**
   * Register the PooledConnection with the busyList.
   */
  private int registerBusyConnection(PooledConnection connection) {
    int busySize = busyList.add(connection);
    if (busySize > highWaterMark) {
      highWaterMark = busySize;
    }
    return busySize;
  }

  private PooledConnection _obtainConnection() throws InterruptedException, SQLException {
    lock.lockInterruptibly();
    try {
      if (doingShutdown) {
        throw new SQLException("Trying to access the Connection Pool when it is shutting down");
      }
      // this includes attempts that fail with InterruptedException
      // or SQLException but that is ok as its only an indicator
      hitCount++;
      // are other threads already waiting? (they get priority)
      if (waitingThreads == 0) {
        if (!freeList.isEmpty()) {
          // we have a free connection to return
          return extractFromFreeList();
        }
        if (busyList.size() < maxSize) {
          // grow the connection pool
          PooledConnection c = pool.createConnectionForQueue(connectionId++);
          int busySize = registerBusyConnection(c);
          if (Log.isLoggable(DEBUG)) {
            Log.debug("DataSource [{0}] grow; id[{1}] busy[{2}] max[{3}]", name, c.name(), busySize, maxSize);
          }
          checkForWarningSize();
          return c;
        }
      }
      try {
        // The pool is at maximum size. We are going to go into
        // a wait loop until connections are returned into the pool.
        waitCount++;
        waitingThreads++;
        return _obtainConnectionWaitLoop();
      } finally {
        waitingThreads--;
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Got into a loop waiting for connections to be returned to the pool.
   */
  private PooledConnection _obtainConnectionWaitLoop() throws SQLException, InterruptedException {
    long nanos = MILLIS_TIME_UNIT.toNanos(waitTimeoutMillis);
    for (; ; ) {
      if (nanos <= 0) {
        String msg = "Unsuccessfully waited [" + waitTimeoutMillis + "] millis for a connection to be returned."
          + " No connections are free. You need to Increase the max connections of [" + maxSize + "]"
          + " or look for a connection pool leak using datasource.xxx.capturestacktrace=true";
        if (pool.captureStackTrace()) {
          dumpBusyConnectionInformation();
        }

        throw new SQLException(msg);
      }

      try {
        nanos = notEmpty.awaitNanos(nanos);
        if (!freeList.isEmpty()) {
          // successfully waited
          return extractFromFreeList();
        }
      } catch (InterruptedException ie) {
        notEmpty.signal(); // propagate to non-interrupted thread
        throw ie;
      }
    }
  }

  PoolStatus shutdown(boolean closeBusyConnections) {
    lock.lock();
    try {
      doingShutdown = true;
      PoolStatus status = createStatus();
      closeFreeConnections(true);

      if (!closeBusyConnections) {
        // connections close on return to pool
        lastResetTime = System.currentTimeMillis() - 100;
      } else {
        if (!busyList.isEmpty()) {
          Log.warn("Closing busy connections on shutdown size: {0}", busyList.size());
          dumpBusyConnectionInformation();
          closeBusyConnections(0);
        }
      }
      return status;
    } finally {
      lock.unlock();
      doingShutdown = false;
    }
  }

  /**
   * Close all the connections in the pool and any current busy connections
   * when they are returned. New connections will be then created on demand.
   * <p>
   * This is typically done when a database down event occurs.
   */
  void reset(long leakTimeMinutes) {
    lock.lock();
    try {
      PoolStatus status = createStatus();
      Log.info("Resetting DataSource [{0}] {1}", name, status);
      lastResetTime = System.currentTimeMillis();

      closeFreeConnections(false);
      closeBusyConnections(leakTimeMinutes);

      String busyInfo = getBusyConnectionInformation();
      if (!busyInfo.isEmpty()) {
        Log.info("Busy Connections:\n {0}", busyInfo);
      }

    } finally {
      lock.unlock();
    }
  }

  void trim(long maxInactiveMillis, long maxAgeMillis) {
    lock.lock();
    try {
      if (trimInactiveConnections(maxInactiveMillis, maxAgeMillis)) {
        try {
          ensureMinimumConnections();
        } catch (SQLException e) {
          Log.error("Error trying to ensure minimum connections", e);
        }
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Trim connections that have been not used for some time.
   */
  private boolean trimInactiveConnections(long maxInactiveMillis, long maxAgeMillis) {
    final long createdSince = (maxAgeMillis == 0) ? 0 : System.currentTimeMillis() - maxAgeMillis;
    final int trimmedCount;
    if (freeList.size() > minSize) {
      // trim on maxInactive and maxAge
      long usedSince = System.currentTimeMillis() - maxInactiveMillis;
      trimmedCount = freeList.trim(minSize, usedSince, createdSince);
    } else if (createdSince > 0) {
      // trim only on maxAge
      trimmedCount = freeList.trim(0, createdSince, createdSince);
    } else {
      trimmedCount = 0;
    }
    if (trimmedCount > 0 && Log.isLoggable(DEBUG)) {
      Log.debug("DataSource [{0}] trimmed [{1}] inactive connections. New size[{2}]", name, trimmedCount, totalConnections());
    }
    return trimmedCount > 0 && freeList.size() < minSize;
  }

  /**
   * Close all the connections that are in the free list.
   */
  private void closeFreeConnections(boolean logErrors) {
    lock.lock();
    try {
      freeList.closeAll(logErrors);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Close any busy connections that have not been used for some time.
   * <p>
   * These connections are considered to have leaked from the connection pool.
   * <p>
   * Connection leaks occur when code doesn't ensure that connections are
   * closed() after they have been finished with. There should be an
   * appropriate try catch finally block to ensure connections are always
   * closed and put back into the pool.
   */
  void closeBusyConnections(long leakTimeMinutes) {
    lock.lock();
    try {
      busyList.closeBusyConnections(leakTimeMinutes);
    } finally {
      lock.unlock();
    }
  }

  /**
   * As the pool grows it gets closer to the maxConnections limit. We can send
   * an Alert (or warning) as we get close to this limit and hence an
   * Administrator could increase the pool size if desired.
   * <p>
   * This is called whenever the pool grows in size (towards the max limit).
   */
  private void checkForWarningSize() {
    // the total number of connections that we can add
    // to the pool before it hits the maximum
    int availableGrowth = (maxSize - totalConnections());
    if (availableGrowth < warningSize) {
      closeBusyConnections(leakTimeMinutes);
      pool.notifyWarning("DataSource [" + name + "] is [" + availableGrowth + "] connections from its maximum size.");
    }
  }

  String getBusyConnectionInformation() {
    return getBusyConnectionInformation(false);
  }

  void dumpBusyConnectionInformation() {
    getBusyConnectionInformation(true);
  }

  /**
   * Returns information describing connections that are currently being used.
   */
  private String getBusyConnectionInformation(boolean toLogger) {
    lock.lock();
    try {
      return busyList.busyConnectionInformation(toLogger);
    } finally {
      lock.unlock();
    }
  }

}

