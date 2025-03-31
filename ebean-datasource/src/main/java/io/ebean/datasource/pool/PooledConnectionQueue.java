package io.ebean.datasource.pool;

import io.ebean.datasource.ConnectionPoolExhaustedException;
import io.ebean.datasource.PoolStatus;
import io.ebean.datasource.pool.ConnectionPool.Status;

import java.sql.SQLException;
import java.util.Collections;
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
   * A buffer designed specifically for free and busy connections.
   */
  private final ConnectionBuffer buffer;
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
    this.buffer = new ConnectionBuffer(pool.affinitySize());
    this.lock = new ReentrantLock(false);
    this.notEmpty = lock.newCondition();
  }

  private PoolStatus createStatus() {
    return new Status(minSize, maxSize, buffer.freeSize(), buffer.busySize(), waitingThreads, highWaterMark,
      waitCount, hitCount, totalAcquireNanos, maxAcquireNanos);
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
        highWaterMark = buffer.busySize();
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
    if (maxSize < this.minSize) {
      throw new IllegalArgumentException("maxSize " + maxSize + " < minSize " + this.minSize);
    }
    this.maxSize = maxSize;
  }

  private int totalConnections() {
    return buffer.freeSize() + buffer.busySize();
  }

  void ensureMinimumConnections() throws SQLException {
    lock.lock();
    try {
      int add = minSize - totalConnections();
      if (add > 0) {
        for (int i = 0; i < add; i++) {
          buffer.addFree(pool.createConnectionForQueue(connectionId++));
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
    boolean closeConnection = false;
    lock.lock();
    try {
      if (!buffer.removeBusy(c)) {
        Log.error("Connection [{0}] not found in BusyList?", c);
      }
      if (forceClose || c.shouldTrimOnReturn(lastResetTime, maxAgeMillis)) {
        closeConnection = true;
      } else {
        buffer.addFree(c);
        notEmpty.signal();
      }
    } finally {
      lock.unlock();
    }
    if (closeConnection) {
      c.closeConnectionFully(false);
    }
  }

  private PooledConnection extractFromFreeList(Object affinitiyId) {

    PooledConnection c = buffer.removeFree(affinitiyId);
    if (c == null) {
      return null;
    }
    if (validateStaleMillis > 0 && staleEviction(c)) {
      c.closeConnectionFully(false);
      return null;
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

  PooledConnection obtainConnection(Object affinitiyId) throws SQLException {
    try {
      PooledConnection pc = _obtainConnection(affinitiyId);
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
    int busySize = buffer.addBusy(connection);
    if (busySize > highWaterMark) {
      highWaterMark = busySize;
    }
    return busySize;
  }

  private PooledConnection _obtainConnection(Object affinitiyId) throws InterruptedException, SQLException {
    var start = System.nanoTime();
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
        PooledConnection connection = extractFromFreeList(affinitiyId);
        if (connection != null) {
          return connection;
        }
        connection = createConnection();
        if (connection == null && buffer.isAffinitySupported()) {
          // we could not find connection with required affinity and
          // buffer is full. So try to get oldest connection from buffer
          connection = extractFromFreeList(ConnectionBuffer.GET_OLDEST);
        }
        if (connection != null) {
          return connection;
        }
      }
      try {
        // The pool is at maximum size. We are going to go into
        // a wait loop until connections are returned into the pool.
        waitCount++;
        waitingThreads++;
        return _obtainConnectionWaitLoop(null);
      } finally {
        waitingThreads--;
      }
    } finally {
      final var elapsed = System.nanoTime() - start;
      totalAcquireNanos += elapsed;
      maxAcquireNanos = Math.max(maxAcquireNanos, elapsed);
      lock.unlock();
    }
  }

  private PooledConnection createConnection() throws SQLException {
    if (buffer.busySize() < maxSize) {
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
  private PooledConnection _obtainConnectionWaitLoop(Object affinitiyId) throws SQLException, InterruptedException {
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
        PooledConnection c = extractFromFreeList(affinitiyId);
        if (c == null && buffer.isAffinitySupported()) {
          c = extractFromFreeList(ConnectionBuffer.GET_OLDEST);
        }
        if (c != null) {
          // successfully waited
          return c;
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
        if (buffer.busySize() > 0) {
          Log.warn("Closing busy connections on shutdown size: {0}", buffer.busySize());
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
    final List<PooledConnection> toTrim;
    lock.lock();
    try {
      if (buffer.freeSize() > minSize) {
        // trim on maxInactive and maxAge
        long usedSince = System.currentTimeMillis() - maxInactiveMillis;
        toTrim = buffer.trim(minSize, usedSince, createdSince);
      } else if (createdSince > 0) {
        // trim only on maxAge
        toTrim = buffer.trim(0, createdSince, createdSince);
      } else {
        toTrim = Collections.emptyList();
      }
    } finally {
      lock.unlock();
    }
    if (toTrim.isEmpty()) {
      return false;
    }
    toTrim.forEach(pc -> pc.closeConnectionFully(true));
    if (Log.isLoggable(DEBUG)) {
      Log.debug("DataSource [{0}] trimmed [{1}] inactive connections. New size[{2}]", name, toTrim.size(), totalConnections());
    }
    return buffer.freeSize() < minSize;
  }

  /**
   * Close all the connections that are in the free list.
   */
  private void closeFreeConnections(boolean logErrors) {
    List<PooledConnection> tempList;
    lock.lock();
    try {
      tempList = buffer.clearFreeList();
    } finally {
      lock.unlock();
    }
    // closing the connections is done outside lock
    if (Log.isLoggable(System.Logger.Level.TRACE)) {
      Log.trace("... closing all {0} connections from the free list with logErrors: {1}", tempList.size(), logErrors);
    }
    for (PooledConnection connection : tempList) {
      connection.closeConnectionFully(logErrors);
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
    List<PooledConnection> tempList;
    lock.lock();
    try {
      tempList = buffer.cleanupBusyList(leakTimeMinutes);
    } finally {
      lock.unlock();
    }
    for (PooledConnection pc : tempList) {
      try {
        Log.warn("DataSource closing busy connection? {0}", pc.fullDescription());
        System.out.println("CLOSING busy connection: " + pc.fullDescription());
        pc.closeConnectionFully(false);
      } catch (Exception ex) {
        Log.error("Error when closing potentially leaked connection " + pc.description(), ex);
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
      return buffer.busyConnectionInformation(toLogger);
    } finally {
      lock.unlock();
    }
  }

}

