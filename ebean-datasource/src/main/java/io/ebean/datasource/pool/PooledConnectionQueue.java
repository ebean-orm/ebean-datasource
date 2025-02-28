package io.ebean.datasource.pool;

import io.ebean.datasource.ConnectionPoolExhaustedException;
import io.ebean.datasource.PoolStatus;
import io.ebean.datasource.pool.ConnectionPool.Status;

import java.sql.SQLException;
import java.util.List;
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
  private final long maxAgeMillis;
  private final int minSize;
  private int maxSize;
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
  private long totalAcquireNanos;
  private long maxAcquireNanos;

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
  private final long validateStaleMillis;

  PooledConnectionQueue(ConnectionPool pool) {
    this.pool = pool;
    this.name = pool.name();
    this.minSize = pool.minSize();
    this.maxSize = pool.maxSize();
    this.waitTimeoutMillis = pool.waitTimeoutMillis();
    this.maxAgeMillis = pool.maxAgeMillis();
    this.validateStaleMillis = pool.validateStaleMillis();
    this.busyList = new BusyConnectionBuffer(maxSize, 20);
    this.freeList = new FreeConnectionBuffer();
    this.lock = new ReentrantLock(false);
    this.notEmpty = lock.newCondition();
  }

  private PoolStatus createStatus() {
    return new Status(minSize, maxSize, freeList.size(), busyList.size(), waitingThreads, highWaterMark,
      waitCount, hitCount, totalAcquireNanos, maxAcquireNanos);
  }

  @Override
  public String toString() {
    return status(false).toString();
  }

  PoolStatus status(boolean reset) {
    lock.lock();
    try {
      PoolStatus s = createStatus();
      if (reset) {
        highWaterMark = busyList.size();
        hitCount = 0;
        waitCount = 0;
        maxAcquireNanos = 0;
        totalAcquireNanos = 0;
      }
      return s;
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
  void returnPooledConnection(PooledConnection c, boolean forceClose, boolean logErrors) {
    boolean closeConnection = false;
    lock.lock();
    try {
      if (!busyList.remove(c)) {
        Log.error("Connection [{0}] not found in BusyList?", c);
      }
      if (forceClose || c.shouldTrimOnReturn(lastResetTime, maxAgeMillis)) {
        closeConnection = true; // close outside lock
      } else {
        freeList.add(c);
        notEmpty.signal(); // notify _obtainConnectionWaitLoop, that there are new connections in the freelist
      }
    } finally {
      lock.unlock();
    }
    if (closeConnection) {
      c.closeConnectionFully(logErrors);
    }
  }

  /**
   * Returns one connection from the free list and put it into the busy list or null if no connections are free.
   *
   * @throws StaleConnectionException there was a connection in the freeList, but it is stale.
   *                                  The connection has to be closed outside the lock.
   */
  private PooledConnection extractFromFreeList() throws StaleConnectionException {
    if (freeList.isEmpty()) {
      return null;
    }
    final PooledConnection c = freeList.remove();
    if (validateStaleMillis > 0 && staleEviction(c)) {
      throw new StaleConnectionException(c);
    }
    registerBusyConnection(c);
    return c;
  }

  private boolean staleEviction(PooledConnection c) {
    if (!stale(c)) {
      return false;
    }
    if (Log.isLoggable(DEBUG)) {
      Log.debug("stale connection validation millis:{0}", (System.currentTimeMillis() - c.lastUsedTime()));
    }
    return pool.invalidConnection(c);
  }

  private boolean stale(PooledConnection c) {
    return c.lastUsedTime() < System.currentTimeMillis() - validateStaleMillis;
  }

  PooledConnection obtainConnection() throws SQLException {
    var start = System.nanoTime();
    while (true) {
      try {
        PooledConnection pc = _obtainConnection();
        pc.resetForUse();
        final var elapsed = System.nanoTime() - start;
        totalAcquireNanos += elapsed;
        maxAcquireNanos = Math.max(maxAcquireNanos, elapsed);
        return pc;

      } catch (InterruptedException e) {
        // restore the interrupted status as we throw SQLException
        Thread.currentThread().interrupt();
        throw new SQLException("Interrupted getting connection from pool", e);
      } catch (StaleConnectionException e) {
        e.getConnection().closeConnectionFully(true);
        // try again...
      }
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

  private PooledConnection _obtainConnection() throws InterruptedException, SQLException, StaleConnectionException {

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
        PooledConnection connection = extractFromFreeList();
        if (connection != null) {
          return connection;
        }
        connection = createConnection();
        if (connection != null) {
          return connection;
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

  private PooledConnection createConnection() throws SQLException {
    if (busyList.size() < maxSize) {
      // grow the connection pool
      PooledConnection c = pool.createConnectionForQueue(connectionId++);
      int busySize = registerBusyConnection(c);
      if (Log.isLoggable(DEBUG)) {
        Log.debug("DataSource [{0}] grow; id[{1}] busy[{2}] max[{3}]", name, c.name(), busySize, maxSize);
      }
      return c;
    } else {
      return null;
    }
  }

  /**
   * Got into a loop waiting for connections to be returned to the pool.
   */
  private PooledConnection _obtainConnectionWaitLoop() throws SQLException, InterruptedException, StaleConnectionException {
    long nanos = MILLIS_TIME_UNIT.toNanos(waitTimeoutMillis);
    for (; ; ) {
      if (nanos <= 0) {
        // We waited long enough, that a connection was returned, so we try to create a new connection.
        PooledConnection conn = createConnection();
        if (conn != null) {
          return conn;
        }
        String msg = "Unsuccessfully waited [" + waitTimeoutMillis + "] millis for a connection to be returned."
          + " No connections are free. You need to Increase the max connections of [" + maxSize + "]"
          + " or look for a connection pool leak using datasource.xxx.capturestacktrace=true";
        if (pool.captureStackTrace()) {
          dumpBusyConnectionInformation();
        }

        throw new ConnectionPoolExhaustedException(msg);
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
      freeList.closeAll(true);

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
  void reset(long leakTimeMinutes, boolean logErrors) {
    lock.lock();
    try {
      PoolStatus status = createStatus();
      Log.info("Resetting DataSource [{0}] {1}", name, status);
      lastResetTime = System.currentTimeMillis();

      freeList.closeAll(logErrors);
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
    if (trimInactiveConnections(maxInactiveMillis, maxAgeMillis)) {
      try {
        ensureMinimumConnections();
      } catch (SQLException e) {
        Log.error("Error trying to ensure minimum connections", e);
      }
    }
  }

  /**
   * Trim connections that have been not used for some time.
   */
  private boolean trimInactiveConnections(long maxInactiveMillis, long maxAgeMillis) {
    final long createdSince = (maxAgeMillis == 0) ? 0 : System.currentTimeMillis() - maxAgeMillis;
    final List<PooledConnection> trimmed;
    lock.lock();
    try {
      if (freeList.size() > minSize) {
        // trim on maxInactive and maxAge
        long usedSince = System.currentTimeMillis() - maxInactiveMillis;
        trimmed = freeList.trim(minSize, usedSince, createdSince);
      } else if (createdSince > 0) {
        // trim only on maxAge
        trimmed = freeList.trim(0, createdSince, createdSince);
      } else {
        trimmed = null;
      }
    } finally {
      lock.unlock();
    }
    if (trimmed != null) {
      if (Log.isLoggable(DEBUG)) {
        Log.debug("DataSource [{0}] trimmed [{1}] inactive connections. New size[{2}]", name, trimmed.size(), totalConnections());
      }
      for (PooledConnection pc : trimmed) {
        pc.closeConnectionFully(true);
      }
      return freeList.size() < minSize;
    }
    return false;
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
    List<PooledConnection> busyConnections;
    lock.lock();
    try {
      busyConnections = busyList.removeBusyConnections(leakTimeMinutes);
    } finally {
      lock.unlock();
    }
    if (busyConnections != null) {
      for (PooledConnection pc : busyConnections) {
        try {
          Log.warn("DataSource closing busy connection? {0}", pc.fullDescription());
          pc.closeConnectionFully(true);
        } catch (Exception ex) {
          Log.error("Error when closing potentially leaked connection " + pc.description(), ex);
        }
      }
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

