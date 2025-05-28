package io.ebean.datasource.pool;

import io.ebean.datasource.*;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A robust DataSource implementation.
 * <ul>
 * <li>Manages the number of connections closing connections that have been idle for some time.</li>
 * <li>Notifies when the datasource goes down and comes back up.</li>
 * <li>Provides PreparedStatement caching</li>
 * <li>Knows the busy connections</li>
 * <li>Traces connections that have been leaked</li>
 * </ul>
 */
final class ConnectionPool implements DataSourcePool {

  @FunctionalInterface
  interface Heartbeat {

    void stop();
  }

  private static final String APPLICATION_NAME = "ApplicationName";
  private final ReentrantLock heartbeatLock = new ReentrantLock(false);
  private final ReentrantLock notifyLock = new ReentrantLock(false);
  /**
   * The name given to this dataSource.
   */
  private final String name;
  private final AtomicInteger size = new AtomicInteger(0);
  private final DataSourceConfig config;
  /**
   * Used to notify of changes to the DataSource status.
   */
  private final DataSourceAlert notify;
  private final DataSourcePoolListener poolListener;
  private final List<String> initSql;
  private final String user;
  private final String schema;
  private final String catalog;
  private final String heartbeatSql;
  private final int heartbeatFreqSecs;
  private final int heartbeatTimeoutSeconds;
  private final int heartbeatMaxPoolExhaustedCount;

  private final long trimPoolFreqMillis;
  private final int transactionIsolation;
  private final boolean autoCommit;
  private final boolean readOnly;
  private final boolean failOnStart;
  private final int maxInactiveMillis;
  private final long validateStaleMillis;
  private final boolean enforceCleanClose;
  /**
   * Max age a connection is allowed in millis.
   * A value of 0 means no limit (no trimming based on max age).
   */
  private final long maxAgeMillis;
  private final boolean captureStackTrace;
  private final int maxStackTraceSize;
  private final Properties clientInfo;
  private final String applicationName;
  private final DataSource source;
  private final boolean validateOnHeartbeat;
  private long nextTrimTime;

  /**
   * HeartBeat checking will discover when it goes down, and comes back up again.
   */
  private final AtomicBoolean dataSourceUp = new AtomicBoolean(false);
  private SQLException dataSourceDownReason;
  private final int minConnections;
  private final int initialConnections;
  private int maxConnections;
  private final int waitTimeoutMillis;
  private final int pstmtCacheSize;
  private final PooledConnectionQueue queue;
  private Heartbeat heartbeat;
  private int heartbeatPoolExhaustedCount;
  private final ExecutorService executor;

  /**
   * Used to find and close() leaked connections. Leaked connections are
   * thought to be busy but have not been used for some time. Each time a
   * connection is used it sets it's lastUsedTime.
   */
  private final long leakTimeMinutes;
  private final LongAdder pscHit = new LongAdder();
  private final LongAdder pscMiss = new LongAdder();
  private final LongAdder pscRem = new LongAdder();

  private final boolean shutdownOnJvmExit;
  private Thread shutdownHook;

  ConnectionPool(String name, DataSourceConfig params) {
    this.config = params;
    this.name = name;
    this.notify = params.getAlert();
    this.poolListener = params.getListener();
    this.autoCommit = params.isAutoCommit();
    this.readOnly = params.isReadOnly();
    this.failOnStart = params.isFailOnStart();
    this.initSql = params.getInitSql();
    this.transactionIsolation = params.getIsolationLevel();
    this.maxInactiveMillis = 1000 * params.getMaxInactiveTimeSecs();
    this.maxAgeMillis = 60000L * params.getMaxAgeMinutes();
    this.leakTimeMinutes = params.getLeakTimeMinutes();
    this.captureStackTrace = params.isCaptureStackTrace();
    this.maxStackTraceSize = params.getMaxStackTraceSize();
    this.pstmtCacheSize = params.getPstmtCacheSize();
    this.minConnections = params.getMinConnections();
    this.initialConnections = params.getInitialConnections();
    this.maxConnections = params.getMaxConnections();
    this.waitTimeoutMillis = params.getWaitTimeoutMillis();
    this.heartbeatFreqSecs = params.getHeartbeatFreqSecs();
    this.heartbeatTimeoutSeconds = params.getHeartbeatTimeoutSeconds();
    this.heartbeatMaxPoolExhaustedCount = params.getHeartbeatMaxPoolExhaustedCount();
    this.heartbeatSql = params.getHeartbeatSql();
    this.validateOnHeartbeat = params.isValidateOnHeartbeat();
    this.trimPoolFreqMillis = 1000L * params.getTrimPoolFreqSecs();
    this.validateStaleMillis = params.validateStaleMillis();
    this.applicationName = params.getApplicationName();
    this.clientInfo = params.getClientInfo();
    this.queue = new PooledConnectionQueue(this);
    this.schema = params.getSchema();
    this.catalog = params.catalog();
    this.user = params.getUsername();
    this.shutdownOnJvmExit = params.isShutdownOnJvmExit();
    this.source = DriverDataSource.of(name, params);
    this.enforceCleanClose = params.enforceCleanClose();
    if (!params.isOffline()) {
      init();
    }
    this.nextTrimTime = System.currentTimeMillis() + trimPoolFreqMillis;
    this.executor = ExecutorFactory.newExecutor();
  }

  private void init() {
    try {
      if (config.useInitDatabase()) {
        initialiseDatabase();
      }
      initialiseConnections();
    } catch (SQLException e) {
      throw new DataSourceInitialiseException("Error initialising DataSource with user: " + user + " error:" + e.getMessage(), e);
    }
  }

  /**
   * Accumulate the prepared statement cache metrics as connections are closed.
   */
  void pstmtCacheMetrics(PstmtCache pstmtCache) {
    pscHit.add(pstmtCache.hitCount());
    pscMiss.add(pstmtCache.missCount());
    pscRem.add(pstmtCache.removeCount());
  }

  @Override
  public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
    throw new SQLFeatureNotSupportedException("We do not support java.util.logging");
  }

  private void tryEnsureMinimumConnections() {
    notifyLock.lock();
    try {
      queue.createConnections(initialConnections);
      // if we successfully come up without an exception, send datasource up
      // notification. This makes it easier, because the application needs not
      // to implement special handling, if the db comes up the first time or not.
      if (notify != null) {
        notify.dataSourceUp(this);
      }
    } catch (SQLException e) {
      Log.error("Error trying to ensure minimum connections, maybe db server is down - message:" + e.getMessage(), e);
    } finally {
      notifyLock.unlock();
    }
  }

  private void initialiseConnections() throws SQLException {
    long start = System.currentTimeMillis();
    dataSourceUp.set(true);
    if (failOnStart) {
      queue.createConnections(initialConnections);
    } else {
      tryEnsureMinimumConnections();
    }
    startHeartBeatIfStopped();

    if (shutdownOnJvmExit && shutdownHook == null) {
      shutdownHook = new Thread(() -> shutdownPool(true, true));
      shutdownHook.setName("DataSourcePool-ShutdownHook");
      Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    final var ro = readOnly ? "readOnly[true] " : "";
    Log.info("DataSource [{0}] {1}autoCommit[{2}] [{3}] min[{4}] max[{5}] in[{6}ms]",
      name, ro, autoCommit, description(transactionIsolation), minConnections, maxConnections, (System.currentTimeMillis() - start), validateOnHeartbeat);
  }

  /**
   * Return the string description of the transaction isolation level specified.
   */
  private static String description(int level) {
    switch (level) {
      case Connection.TRANSACTION_NONE:
        return "NONE";
      case Connection.TRANSACTION_READ_COMMITTED:
        return "READ_COMMITTED";
      case Connection.TRANSACTION_READ_UNCOMMITTED:
        return "READ_UNCOMMITTED";
      case Connection.TRANSACTION_REPEATABLE_READ:
        return "REPEATABLE_READ";
      case Connection.TRANSACTION_SERIALIZABLE:
        return "SERIALIZABLE";
      case -1:
        return "NotSet";
      default:
        return "UNKNOWN[" + level + "]";
    }
  }

  /**
   * Initialise the database using the owner credentials if we can't connect using the normal credentials.
   * <p>
   * That is, if we think the username doesn't exist in the DB, initialise the DB using the owner credentials.
   */
  private void initialiseDatabase() throws SQLException {
    try (Connection connection = createConnection()) {
      // successfully obtained a connection so skip initDatabase
      connection.clearWarnings();
    } catch (SQLException e) {
      Log.info("Obtaining connection using ownerUsername:{0} to initialise database", config.getOwnerUsername());
      // expected when user does not exist, obtain a connection using owner credentials
      try (Connection ownerConnection = getConnection(config.getOwnerUsername(), config.getOwnerPassword())) {
        // initialise the DB (typically create the user/role using the owner credentials etc)
        InitDatabase initDatabase = config.getInitDatabase();
        initDatabase.run(ownerConnection, config);
        ownerConnection.commit();
      } catch (SQLException e2) {
        throw new SQLException("Failed to run InitDatabase with ownerUsername:" + config.getOwnerUsername() + " message:" + e2.getMessage(), e2);
      }
    }
  }

  /**
   * Returns false.
   */
  @Override
  public boolean isWrapperFor(Class<?> arg0) {
    return false;
  }

  /**
   * Not Implemented.
   */
  @Override
  public <T> T unwrap(Class<T> arg0) throws SQLException {
    throw new SQLException("Not Implemented");
  }

  /**
   * Return the dataSource name.
   */
  @Override
  public String name() {
    return name;
  }

  @Override
  public int size() {
    return size.get();
  }

  String schema() {
    return schema;
  }

  String catalog() {
    return catalog;
  }

  /**
   * Increment the current pool size.
   */
  void inc() {
    size.incrementAndGet();
  }

  /**
   * Decrement the current pool size.
   */
  void dec() {
    size.decrementAndGet();
  }

  int maxStackTraceSize() {
    return maxStackTraceSize;
  }

  @Override
  public SQLException dataSourceDownReason() {
    return dataSourceDownReason;
  }

  private void notifyDataSourceIsDown(SQLException reason) {
    if (dataSourceUp.get()) {
      reset();
      notifyDown(reason);
    }
  }

  private void notifyDown(SQLException reason) {
    notifyLock.lock();
    try {
      if (dataSourceUp.get()) {
        // check and set false immediately so that we only alert once
        dataSourceUp.set(false);
        dataSourceDownReason = reason;
        Log.error("FATAL: DataSource [" + name + "] is down or has network error!!!", reason);
        if (notify != null) {
          notify.dataSourceDown(this, reason);
        }
      }
    } finally {
      notifyLock.unlock();
    }
  }

  private void notifyDataSourceIsUp() {
    if (!dataSourceUp.get()) {
      reset();
      notifyUp();
    }
  }

  private void notifyUp() {
    notifyLock.lock();
    try {
      // check such that we only notify once
      if (!dataSourceUp.get()) {
        dataSourceUp.set(true);
        startHeartBeatIfStopped();
        dataSourceDownReason = null;
        Log.error("RESOLVED FATAL: DataSource [" + name + "] is back up!");
        if (notify != null) {
          notify.dataSourceUp(this);
        }
      } else {
        Log.info("DataSource [{0}] is back up!", name);
      }
    } finally {
      notifyLock.unlock();
    }
  }

  /**
   * Trim connections in the free list based on idle time and maximum age.
   */
  private void trimIdleConnections() {
    if (System.currentTimeMillis() > nextTrimTime) {
      try {
        queue.trim(maxInactiveMillis, maxAgeMillis);
        nextTrimTime = System.currentTimeMillis() + trimPoolFreqMillis;
      } catch (Exception e) {
        Log.error("Error trying to trim idle connections - message:" + e.getMessage(), e);
      }
    }
  }

  /**
   * Check the dataSource is up. Trim connections.
   * <p>
   * This is called by the HeartbeatRunnable which should be scheduled to
   * run periodically (every heartbeatFreqSecs seconds).
   */
  void heartbeat() {
    trimIdleConnections();
    if (validateOnHeartbeat) {
      testConnection();
    }
  }

  private void testConnection() {
    PooledConnection conn = null;
    try {
      // Get a connection from the pool and test it
      conn = getPooledConnection();
      heartbeatPoolExhaustedCount = 0;
      if (testConnection(conn)) {
        notifyDataSourceIsUp();
      } else {
        notifyDataSourceIsDown(null);
      }
    } catch (ConnectionPoolExhaustedException be) {
      heartbeatPoolExhaustedCount++;
      if (heartbeatPoolExhaustedCount > heartbeatMaxPoolExhaustedCount) {
        notifyDataSourceIsDown(be);
      } else {
        Log.warn("Heartbeat: " + be.getMessage());
      }
    } catch (SQLException ex) {
      notifyDataSourceIsDown(ex);
    } finally {
      try {
        if (conn != null) {
          try {
            if (!conn.getAutoCommit()) {
              conn.rollback();
            }
          } finally {
            conn.closePooledConnection(false);
          }
        }
      } catch (SQLException ex) {
        Log.warn("Can't close connection in checkDataSource!");
      }
    }
  }

  /**
   * Initializes the connection we got from the driver.
   */
  private Connection initConnection(Connection conn) throws SQLException {
    conn.setAutoCommit(autoCommit);
    // isolation level is set globally for all connections (at least for H2) and
    // you will need admin rights - so we do not change it, if it already matches.
    if (conn.getTransactionIsolation() != transactionIsolation) {
      conn.setTransactionIsolation(transactionIsolation);
    }
    if (readOnly) {
      conn.setReadOnly(true);
    }
    if (catalog != null) {
      conn.setCatalog(catalog);
    }
    if (schema != null) {
      conn.setSchema(schema);
    }
    if (applicationName != null) {
      try {
        conn.setClientInfo(APPLICATION_NAME, applicationName);
      } catch (SQLClientInfoException e) {
        Log.error("Error setting clientInfo ApplicationName", e);
      }
    }
    if (clientInfo != null) {
      try {
        conn.setClientInfo(clientInfo);
      } catch (SQLClientInfoException e) {
        Log.error("Error setting clientInfo", e);
      }
    }
    if (initSql != null) {
      for (String query : initSql) {
        try (Statement stmt = conn.createStatement()) {
          stmt.execute(query);
        }
      }
    }
    return conn;
  }

  private Connection createConnection() throws SQLException {
    return initConnection(source.getConnection());
  }

  @Override
  public void setMaxSize(int max) {
    queue.setMaxSize(max);
    this.maxConnections = max;
  }

  int maxSize() {
    return maxConnections;
  }

  int minSize() {
    return minConnections;
  }

  /**
   * Return the time in millis that threads will wait when the pool has hit
   * the max size. These threads wait for connections to be returned by the
   * busy connections.
   */
  int waitTimeoutMillis() {
    return waitTimeoutMillis;
  }

  /**
   * Return the maximum age a connection is allowed to be before it is trimmed
   * out of the pool. This value can be 0 which means there is no maximum age.
   */
  long maxAgeMillis() {
    return maxAgeMillis;
  }

  /**
   * When obtaining a connection that has been idle for longer than maxInactiveMillis
   * perform a validation test on the connection before giving it to the application.
   */
  long validateStaleMillis() {
    return validateStaleMillis;
  }

  private boolean testConnection(Connection conn) throws SQLException {
    if (heartbeatSql == null) {
      return conn.isValid(heartbeatTimeoutSeconds);
    }
    // It should only error IF the DataSource is down or a network issue
    try (Statement stmt = conn.createStatement()) {
      if (heartbeatTimeoutSeconds > 0) {
        stmt.setQueryTimeout(heartbeatTimeoutSeconds);
      }
      stmt.execute(heartbeatSql);
      return true;
    } finally {
      if (!conn.getAutoCommit()) {
        conn.rollback();
      }
    }
  }

  /**
   * Make sure the connection is still ok to use. If not then remove it from the pool.
   */
  boolean invalidConnection(PooledConnection conn) {
    try {
      return !testConnection(conn);
    } catch (Exception e) {
      Log.warn("Validation test failed on connection:{0} message: {1}", conn.name(), e.getMessage());
      return true;
    }
  }

  /**
   * Fail hard in close() when there is uncommitted work. This is for debugging to find wrong code.
   */
  boolean enforceCleanClose() {
    return enforceCleanClose;
  }

  /**
   * Called by the PooledConnection themselves, returning themselves to the
   * pool when they have been finished with.
   * <p>
   * Note that connections may not be added back to the pool if returnToPool
   * is false or if they where created before the recycleTime. In both of
   * these cases the connection is fully closed and not pooled.
   */
  void returnConnection(PooledConnection pooledConnection) {
    // return a normal 'good' connection
    returnTheConnection(pooledConnection, false);
  }

  /**
   * This is a bad connection and must be removed from the pool's busy list and fully closed.
   */
  void returnConnectionForceClose(PooledConnection pooledConnection, boolean testPool) {
    returnTheConnection(pooledConnection, true);
    if (testPool) {
      // Got a bad connection so check the pool
      testConnection();
    }
  }

  void removeClosedConnection(PooledConnection pooledConnection) {
    queue.returnPooledConnection(pooledConnection, true);
  }

  /**
   * Return connection. If forceClose is true then this is a bad connection that
   * must be removed and closed fully.
   */
  private void returnTheConnection(PooledConnection pooledConnection, boolean forceClose) {
    if (poolListener != null && !forceClose) {
      poolListener.onBeforeReturnConnection(pooledConnection);
    }
    queue.returnPooledConnection(pooledConnection, forceClose);
  }

  void returnConnectionReset(PooledConnection pooledConnection) {
    queue.returnPooledConnection(pooledConnection, true);
    Log.warn("Resetting DataSource on read-only failure [{0}]", name);
    reset();
  }

  /**
   * Grow the pool by creating a new connection. The connection can either be
   * added to the available list, or returned.
   */
  PooledConnection createConnectionForQueue(int connId) throws SQLException {
    try {
      final var pooledConnection = new PooledConnection(this, connId, createConnection());
      pooledConnection.resetForUse();
      notifyDataSourceIsUp();
      return pooledConnection;
    } catch (SQLException ex) {
      notifyDataSourceIsDown(ex);
      throw ex;
    }
  }

  /**
   * Close all the connections in the pool.
   * <ul>
   * <li>Checks that the database is up.</li>
   * <li>Resets the Alert level.</li>
   * <li>Closes busy connections that have not been used for some time (aka leaks).</li>
   * <li>This closes all the currently available connections.</li>
   * <li>Busy connections are closed when they are returned to the pool.</li>
   * </ul>
   */
  private void reset() {
    heartbeatPoolExhaustedCount = 0;
    queue.reset(leakTimeMinutes);
  }

  /**
   * Create an unpooled connection with the given username and password.
   * <p>
   * This uses the default isolation level and autocommit mode.
   */
  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return initConnection(source.getConnection(username, password));
  }

  /**
   * Return a pooled connection.
   */
  @Override
  public Connection getConnection() throws SQLException {
    return getPooledConnection();
  }

  /**
   * Get a connection from the pool.
   * <p>
   * This will grow the pool if all the current connections are busy. This
   * will go into a wait if the pool has hit its maximum size.
   */
  private PooledConnection getPooledConnection() throws SQLException {
    PooledConnection c = queue.obtainConnection();
    if (captureStackTrace) {
      c.setStackTrace(Thread.currentThread().getStackTrace());
    }
    if (poolListener != null) {
      poolListener.onAfterBorrowConnection(c);
    }
    return c;
  }

  @Override
  public void shutdown() {
    shutdownPool(true, false);
  }

  @Override
  public void offline() {
    shutdownPool(false, false);
  }

  private void shutdownPool(boolean fullShutdown, boolean fromHook) {
    heartbeatLock.lock();
    try {
      stopHeartBeatIfRunning();
      PoolStatus status = queue.shutdown(fullShutdown);
      dataSourceUp.set(false);
      if (fullShutdown) {
        shutdownExecutor();
      }
      if (fromHook) {
        Log.info("DataSource [{0}] shutdown on JVM exit {1}  psc[hit:{2} miss:{3} rem:{4}]", name, status, pscHit, pscMiss, pscRem);
      } else {
        Log.info("DataSource [{0}] shutdown {1}  psc[hit:{2} miss:{3} rem:{4}]", name, status, pscHit, pscMiss, pscRem);
        removeShutdownHook();
      }
    } finally {
      heartbeatLock.unlock();
    }
  }

  private void removeShutdownHook() {
    if (shutdownHook != null) {
      try {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
      } catch (IllegalStateException e) {
        // alredy in shutdown - may happen if shutdown is called by an application shutdown listener
      }
      shutdownHook = null;
    }
  }

  @Override
  public void online() throws SQLException {
    if (!dataSourceUp.get()) {
      initialiseConnections();
    }
  }

  @Override
  public boolean isOnline() {
    return dataSourceUp.get();
  }

  @Override
  public boolean isDataSourceUp() {
    return dataSourceUp.get();
  }

  private void startHeartBeatIfStopped() {
    heartbeatLock.lock();
    try {
      // only start if it is not already running
      if (heartbeat == null) {
        int freqMillis = heartbeatFreqSecs * 1000;
        if (freqMillis > 0) {
          heartbeat = ExecutorFactory.newHeartBeat(this, freqMillis);
        }
      }
    } finally {
      heartbeatLock.unlock();
    }
  }

  private void stopHeartBeatIfRunning() {
    heartbeatLock.lock();
    try {
      // only stop if it was running
      if (heartbeat != null) {
        heartbeat.stop();
        heartbeat = null;
      }
    } finally {
      heartbeatLock.unlock();
    }
  }

  private static final class AsyncCloser implements Runnable {
    final PooledConnection pc;
    final boolean logErrors;

    private AsyncCloser(PooledConnection pc, boolean logErrors) {
      this.pc = pc;
      this.logErrors = logErrors;
    }

    @Override
    public void run() {
      pc.doCloseConnection(logErrors);
    }

    @Override
    public String toString() {
      return pc.toString();
    }
  }

  /**
   * Closes the connection in the background as it may be slow or block.
   */
  void closeConnectionFullyAsync(PooledConnection pc, boolean logErrors) {
    if (!executor.isShutdown()) {
      try {
        executor.submit(new AsyncCloser(pc, logErrors));
        return;
      } catch (RejectedExecutionException e) {
        Log.trace("DataSource [{0}] closing connection synchronously", name);
      }
    }
    // it is possible that we receive runnables after shutdown.
    // in this case, we will execute them immediately (outside lock)
    pc.doCloseConnection(logErrors);
  }

  private void shutdownExecutor() {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        Log.warn("DataSource [{0}] on shutdown, timeout waiting for connections to close", name);
      }
    } catch (InterruptedException ie) {
      Log.warn("DataSource [{0}] on shutdown, interrupted closing connections", name, ie);
    }
    final var pendingTasks = executor.shutdownNow();
    if (!pendingTasks.isEmpty()) {
      Log.warn("DataSource [{0}] on shutdown, {1} pending connections were not closed", name, pendingTasks.size());
    }
  }

  /**
   * Return the default autoCommit setting for the pool.
   */
  @Override
  public boolean isAutoCommit() {
    return autoCommit;
  }

  public boolean isReadOnly() {
    return readOnly;
  }

  int transactionIsolation() {
    return transactionIsolation;
  }

  boolean captureStackTrace() {
    return captureStackTrace;
  }

  long leakTimeMinutes() {
    return leakTimeMinutes;
  }

  int pstmtCacheSize() {
    return pstmtCacheSize;
  }

  /**
   * Not implemented and shouldn't be used.
   */
  @Override
  public int getLoginTimeout() throws SQLException {
    throw new SQLException("Method not supported");
  }

  /**
   * Not implemented and shouldn't be used.
   */
  @Override
  public void setLoginTimeout(int seconds) throws SQLException {
    throw new SQLException("Method not supported");
  }

  /**
   * Returns null.
   */
  @Override
  public PrintWriter getLogWriter() {
    return null;
  }

  /**
   * Not implemented.
   */
  @Override
  public void setLogWriter(PrintWriter writer) throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public PoolStatus status(boolean reset) {
    return queue.status(reset);
  }

  static final class Status implements PoolStatus {

    private final int minSize;
    private final int maxSize;
    private final int free;
    private final int busy;
    private final int waiting;
    private final int highWaterMark;
    private final int waitCount;
    private final int hitCount;
    private final long totalAcquireMicros;
    private final long maxAcquireMicros;
    private final long totalWaitMicros;
    private final long meanAcquireNanos;

    Status(int minSize, int maxSize, int free, int busy, int waiting, int highWaterMark, int waitCount, int hitCount, long totalAcquireNanos, long maxAcquireNanos, long totalWaitNanos) {
      this.minSize = minSize;
      this.maxSize = maxSize;
      this.free = free;
      this.busy = busy;
      this.waiting = waiting;
      this.highWaterMark = highWaterMark;
      this.waitCount = waitCount;
      this.hitCount = hitCount;
      this.totalAcquireMicros = totalAcquireNanos / 1000;
      this.maxAcquireMicros = maxAcquireNanos / 1000;
      this.totalWaitMicros = totalWaitNanos / 1000;
      this.meanAcquireNanos = hitCount == 0 ? 0 : totalAcquireNanos / hitCount;
    }

    @Override
    public String toString() {
      return "min[" + minSize + "] max[" + maxSize + "] free[" + free + "] busy[" + busy + "] waiting[" + waiting
        + "] highWaterMark[" + highWaterMark + "] waitCount[" + waitCount + "] hitCount[" + hitCount
        + "] totalAcquireMicros[" + totalAcquireMicros + "] maxAcquireMicros[" + maxAcquireMicros + "] totalWaitMicros[" + totalWaitMicros + "]";
    }

    @Override
    public int minSize() {
      return minSize;
    }

    @Override
    public int maxSize() {
      return maxSize;
    }

    @Override
    public int free() {
      return free;
    }

    @Override
    public int busy() {
      return busy;
    }

    @Override
    public int waiting() {
      return waiting;
    }

    @Override
    public int highWaterMark() {
      return highWaterMark;
    }

    @Override
    public int waitCount() {
      return waitCount;
    }

    @Override
    public int hitCount() {
      return hitCount;
    }

    @Override
    public long totalAcquireMicros() {
      return totalAcquireMicros;
    }

    @Override
    public long totalWaitMicros() {
      return totalWaitMicros;
    }

    @Override
    public long maxAcquireMicros() {
      return maxAcquireMicros;
    }

    @Override
    public long meanAcquireNanos() {
      return meanAcquireNanos;
    }
  }

}
