package io.ebean.datasource.pool;

import io.ebean.datasource.*;

import java.io.PrintWriter;
import java.sql.*;
import java.util.*;
import java.util.Map.Entry;
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
  private final Properties connectionProps;
  private final List<String> initSql;
  private final String driver;
  private final String url;
  private final String user;
  private final String heartbeatsql;
  private final int heartbeatFreqSecs;
  private final int heartbeatTimeoutSeconds;
  private final long trimPoolFreqMillis;
  private final int transactionIsolation;
  private final boolean autoCommit;
  private final boolean readOnly;
  private final boolean failOnStart;
  private final int maxInactiveMillis;
  /**
   * Max age a connection is allowed in millis.
   * A value of 0 means no limit (no trimming based on max age).
   */
  private final long maxAgeMillis;
  private final boolean captureStackTrace;
  private final int maxStackTraceSize;
  private final Properties clientInfo;
  private final String applicationName;
  private long lastTrimTime;
  /**
   * HeartBeat checking will discover when it goes down, and comes back up again.
   */
  private final AtomicBoolean dataSourceUp = new AtomicBoolean(false);
  private SQLException dataSourceDownReason;
  private final AtomicBoolean inWarningMode = new AtomicBoolean();
  private int minConnections;
  private int maxConnections;
  private int warningSize;
  private final int waitTimeoutMillis;
  private final int pstmtCacheSize;
  private final PooledConnectionQueue queue;
  private Timer heartBeatTimer;
  /**
   * Used to find and close() leaked connections. Leaked connections are
   * thought to be busy but have not been used for some time. Each time a
   * connection is used it sets it's lastUsedTime.
   */
  private final long leakTimeMinutes;
  private final LongAdder pscHit = new LongAdder();
  private final LongAdder pscMiss = new LongAdder();
  private final LongAdder pscPut = new LongAdder();
  private final LongAdder pscRem = new LongAdder();

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
    this.url = params.getUrl();
    this.driver = params.getDriver();
    this.pstmtCacheSize = params.getPstmtCacheSize();
    this.minConnections = params.getMinConnections();
    this.maxConnections = params.getMaxConnections();
    this.waitTimeoutMillis = params.getWaitTimeoutMillis();
    this.heartbeatsql = params.getHeartbeatSql();
    this.heartbeatFreqSecs = params.getHeartbeatFreqSecs();
    this.heartbeatTimeoutSeconds = params.getHeartbeatTimeoutSeconds();
    this.trimPoolFreqMillis = 1000L * params.getTrimPoolFreqSecs();
    this.applicationName = params.getApplicationName();
    this.clientInfo = params.getClientInfo();
    this.queue = new PooledConnectionQueue(this);
    this.user = params.getUsername();
    if (user == null) {
      throw new DataSourceConfigurationException("DataSource user is not set? url is [" + url + "]");
    }
    String pw = params.getPassword();
    if (pw == null) {
      throw new DataSourceConfigurationException("DataSource password is null? url is [" + url + "]");
    }
    this.connectionProps = new Properties();
    this.connectionProps.setProperty("user", user);
    this.connectionProps.setProperty("password", pw);
    final String schema = params.getSchema();
    if (schema != null) {
      this.connectionProps.setProperty("schema", schema);
    }

    Map<String, String> customProperties = params.getCustomProperties();
    if (customProperties != null) {
      Set<Entry<String, String>> entrySet = customProperties.entrySet();
      for (Entry<String, String> entry : entrySet) {
        this.connectionProps.setProperty(entry.getKey(), entry.getValue());
      }
    }
    checkDriver();
    if (!params.isOffline()) {
      init();
    }
  }

  private void init() {
    try {
      if (config.useInitDatabase()) {
        initialiseDatabase();
      }
      initialiseConnections();
    } catch (SQLException e) {
      throw new DataSourceInitialiseException("Error initialising DataSource with user: " + user + " url:" + url + " error:" + e.getMessage(), e);
    }
  }

  /**
   * Return true if driver has been explicitly configured.
   */
  private boolean hasDriver() {
    return driver != null && !driver.isEmpty();
  }

  /**
   * Check driver exists when explicitly set.
   */
  private void checkDriver() {
    if (hasDriver()) {
      try {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
          Class.forName(driver, true, contextLoader);
        } else {
          Class.forName(driver, true, this.getClass().getClassLoader());
        }
      } catch (Throwable e) {
        throw new IllegalStateException("Problem loading Database Driver [" + driver + "]: " + e.getMessage(), e);
      }
    }
  }

  /**
   * Accumulate the prepared statement cache metrics as connections are closed.
   */
  void pstmtCacheMetrics(PstmtCache pstmtCache) {
    pscHit.add(pstmtCache.hitCount());
    pscMiss.add(pstmtCache.missCount());
    pscPut.add(pstmtCache.putCount());
    pscRem.add(pstmtCache.removeCount());
  }

  class HeartBeatRunnable extends TimerTask {
    @Override
    public void run() {
      checkDataSource();
    }
  }

  @Override
  public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
    throw new SQLFeatureNotSupportedException("We do not support java.util.logging");
  }

  private void tryEnsureMinimumConnections() {
    notifyLock.lock();
    try {
      queue.ensureMinimumConnections();
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
      queue.ensureMinimumConnections();
    } else {
      tryEnsureMinimumConnections();
    }
    startHeartBeatIfStopped();
    String msg = "DataSourcePool [" + name +
        "] autoCommit[" + autoCommit +
        "] transIsolation[" + TransactionIsolation.getDescription(transactionIsolation) +
        "] min[" + minConnections +
        "] max[" + maxConnections +
        "] in[" + (System.currentTimeMillis() - start) + "ms]";
    Log.info(msg);
  }

  /**
   * Initialise the database using the owner credentials if we can't connect using the normal credentials.
   * <p>
   * That is, if we think the username doesn't exist in the DB, initialise the DB using the owner credentials.
   */
  private void initialiseDatabase() throws SQLException {
    try (Connection connection = createConnection(connectionProps, false)) {
      // successfully obtained a connection so skip initDatabase
      connection.clearWarnings();
    } catch (SQLException e) {
      Log.info("Obtaining connection using ownerUsername:{0} to initialise database", config.getOwnerUsername());
      // expected when user does not exist, obtain a connection using owner credentials
      try (Connection ownerConnection = createConnection(config.getOwnerUsername(), config.getOwnerPassword())) {
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

  /**
   * Called when the pool hits the warning level.
   */
  void notifyWarning(String msg) {
    if (inWarningMode.compareAndSet(false, true)) {
      // send an Error to the event log...
      Log.warn(msg);
      if (notify != null) {
        notify.dataSourceWarning(this, msg);
      }
    }
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
        Log.error("FATAL: DataSourcePool [" + name + "] is down or has network error!!!", reason);
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
        Log.error("RESOLVED FATAL: DataSourcePool [" + name + "] is back up!");
        if (notify != null) {
          notify.dataSourceUp(this);
        }
      } else {
        Log.info("DataSourcePool [{0}] is back up!", name);
      }
    } finally {
      notifyLock.unlock();
    }
  }

  /**
   * Trim connections in the free list based on idle time and maximum age.
   */
  private void trimIdleConnections() {
    if (System.currentTimeMillis() > (lastTrimTime + trimPoolFreqMillis)) {
      try {
        queue.trim(maxInactiveMillis, maxAgeMillis);
        lastTrimTime = System.currentTimeMillis();
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
  private void checkDataSource() {
    trimIdleConnections();
    Connection conn = null;
    try {
      // Get a connection from the pool and test it
      conn = getConnection();
      if (testConnection(conn)) {
        notifyDataSourceIsUp();
      } else {
        notifyDataSourceIsDown(null);
      }
    } catch (SQLException ex) {
      notifyDataSourceIsDown(ex);
    } finally {
      try {
        if (conn != null) {
          conn.close();
        }
      } catch (SQLException ex) {
        Log.warn("Can't close connection in checkDataSource!");
      }
    }
  }

  /**
   * Initializes the connection we got from the driver.
   */
  private void initConnection(Connection conn) throws SQLException {
    conn.setAutoCommit(autoCommit);
    // isolation level is set globally for all connections (at least for H2) and
    // you will need admin rights - so we do not change it, if it already matches.
    if (conn.getTransactionIsolation() != transactionIsolation) {
      conn.setTransactionIsolation(transactionIsolation);
    }
    if (readOnly) {
      conn.setReadOnly(true);
    }
    if (applicationName != null) {
      try {
        conn.setClientInfo("ApplicationName", applicationName);
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
  }

  /**
   * Create an un-pooled connection with the given username and password.
   */
  private Connection createConnection(String username, String password) throws SQLException {
    Properties properties = new Properties(connectionProps);
    properties.setProperty("user", username);
    properties.setProperty("password", password);
    return createConnection(properties, true);
  }

  private Connection createConnection() throws SQLException {
    return createConnection(connectionProps, true);
  }

  private Connection createConnection(Properties properties, boolean notifyIsDown) throws SQLException {
    try {
      Connection conn = DriverManager.getConnection(url, properties);
      initConnection(conn);
      return conn;
    } catch (SQLException ex) {
      if (notifyIsDown) {
        notifyDataSourceIsDown(null);
      }
      throw ex;
    }
  }

  @Override
  public void setMaxSize(int max) {
    queue.setMaxSize(max);
    this.maxConnections = max;
  }

  /**
   * Return the max size this pool can grow to.
   */
  public int getMaxSize() {
    return maxConnections;
  }

  /**
   * Set the min size this pool should maintain.
   */
  public void setMinSize(int min) {
    queue.setMinSize(min);
    this.minConnections = min;
  }

  /**
   * Return the min size this pool should maintain.
   */
  public int getMinSize() {
    return minConnections;
  }

  @Override
  public void setWarningSize(int warningSize) {
    queue.setWarningSize(warningSize);
    this.warningSize = warningSize;
  }

  @Override
  public int getWarningSize() {
    return warningSize;
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

  private boolean testConnection(Connection conn) throws SQLException {
    if (heartbeatsql == null) {
      return conn.isValid(heartbeatTimeoutSeconds);
    }
    Statement stmt = null;
    ResultSet rset = null;
    try {
      // It should only error IF the DataSource is down or a network issue
      stmt = conn.createStatement();
      if (heartbeatTimeoutSeconds > 0) {
        stmt.setQueryTimeout(heartbeatTimeoutSeconds);
      }
      rset = stmt.executeQuery(heartbeatsql);
      conn.commit();

      return true;

    } finally {
      try {
        if (rset != null) {
          rset.close();
        }
      } catch (SQLException e) {
        Log.error("Error closing resultSet", e);
      }
      try {
        if (stmt != null) {
          stmt.close();
        }
      } catch (SQLException e) {
        Log.error("Error closing statement", e);
      }
    }
  }

  /**
   * Make sure the connection is still ok to use. If not then remove it from the pool.
   */
  boolean validateConnection(PooledConnection conn) {
    try {
      return testConnection(conn);
    } catch (Exception e) {
      Log.warn("Heartbeat test failed on connection:{0} message: {1}", conn.name(), e.getMessage());
      return false;
    }
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
  void returnConnectionForceClose(PooledConnection pooledConnection) {
    returnTheConnection(pooledConnection, true);
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
    if (forceClose) {
      // Got a bad connection so check the pool
      checkDataSource();
    }
  }

  void returnConnectionReset(PooledConnection pooledConnection) {
    queue.returnPooledConnection(pooledConnection, true);
    Log.warn("Resetting DataSourcePool on read-only failure [{0}]", name);
    reset();
  }

  /**
   * Grow the pool by creating a new connection. The connection can either be
   * added to the available list, or returned.
   */
  PooledConnection createConnectionForQueue(int connId) throws SQLException {
    try {
      Connection c = createConnection();
      PooledConnection pc = new PooledConnection(this, connId, c);
      pc.resetForUse();
      notifyDataSourceIsUp();
      return pc;
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
    queue.reset(leakTimeMinutes);
    inWarningMode.set(false);
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

  /**
   * This will close all the free connections, and then go into a wait loop,
   * waiting for the busy connections to be freed.
   * <p>
   * The DataSources's should be shutdown AFTER thread pools. Leaked
   * Connections are not waited on, as that would hang the server.
   */
  @Override
  public void shutdown() {
    shutdownPool(true);
  }

  @Override
  public void offline() {
    shutdownPool(false);
  }

  private void shutdownPool(boolean closeBusyConnections) {
    stopHeartBeatIfRunning();
    PoolStatus status = queue.shutdown(closeBusyConnections);
    Log.info("DataSourcePool [{0}] shutdown {1}  psc[hit:{2} miss:{3} put:{4} rem:{5}]", name, status, pscHit, pscMiss, pscPut, pscRem);
    dataSourceUp.set(false);
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
      if (heartBeatTimer == null) {
        int freqMillis = heartbeatFreqSecs * 1000;
        if (freqMillis > 0) {
          heartBeatTimer = new Timer(name + ".heartBeat", true);
          heartBeatTimer.scheduleAtFixedRate(new HeartBeatRunnable(), freqMillis, freqMillis);
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
      if (heartBeatTimer != null) {
        heartBeatTimer.cancel();
        heartBeatTimer = null;
      }
    } finally {
      heartbeatLock.unlock();
    }
  }

  /**
   * Return the default autoCommit setting for the pool.
   */
  @Override
  public boolean isAutoCommit() {
    return autoCommit;
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
   * Create an un-pooled connection with the given username and password.
   * <p>
   * This uses the default isolation level and autocommit mode.
   */
  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    Properties props = new Properties();
    props.putAll(connectionProps);
    props.setProperty("user", username);
    props.setProperty("password", password);
    Connection conn = DriverManager.getConnection(url, props);
    initConnection(conn);
    return conn;
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

  /**
   * Return the current status of the connection pool.
   * <p>
   * If you pass reset = true then the counters such as
   * hitCount, waitCount and highWaterMark are reset.
   * </p>
   */
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

    Status(int minSize, int maxSize, int free, int busy, int waiting, int highWaterMark, int waitCount, int hitCount) {
      this.minSize = minSize;
      this.maxSize = maxSize;
      this.free = free;
      this.busy = busy;
      this.waiting = waiting;
      this.highWaterMark = highWaterMark;
      this.waitCount = waitCount;
      this.hitCount = hitCount;
    }

    @Override
    public String toString() {
      return "min[" + minSize + "] max[" + maxSize + "] free[" + free + "] busy[" + busy + "] waiting[" + waiting
        + "] highWaterMark[" + highWaterMark + "] waitCount[" + waitCount + "] hitCount[" + hitCount + "]";
    }

    /**
     * Return the min pool size.
     */
    @Override
    public int minSize() {
      return minSize;
    }

    /**
     * Return the max pool size.
     */
    @Override
    public int maxSize() {
      return maxSize;
    }

    /**
     * Return the current number of free connections in the pool.
     */
    @Override
    public int free() {
      return free;
    }

    /**
     * Return the current number of busy connections in the pool.
     */
    @Override
    public int busy() {
      return busy;
    }

    /**
     * Return the current number of threads waiting for a connection.
     */
    @Override
    public int waiting() {
      return waiting;
    }

    /**
     * Return the high water mark of busy connections.
     */
    @Override
    public int highWaterMark() {
      return highWaterMark;
    }

    /**
     * Return the total number of times a thread had to wait.
     */
    @Override
    public int waitCount() {
      return waitCount;
    }

    /**
     * Return the total number of times there was an attempt to get a
     * connection.
     * <p>
     * If the attempt to get a connection failed with a timeout or other
     * exception those attempts are still included in this hit count.
     * </p>
     */
    @Override
    public int hitCount() {
      return hitCount;
    }

  }

}
