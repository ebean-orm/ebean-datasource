package io.ebean.datasource.pool;

import io.ebean.datasource.DataSourceAlert;
import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourceConfigurationException;
import io.ebean.datasource.DataSourceInitialiseException;
import io.ebean.datasource.DataSourcePool;
import io.ebean.datasource.DataSourcePoolListener;
import io.ebean.datasource.InitDatabase;
import io.ebean.datasource.PoolStatistics;
import io.ebean.datasource.PoolStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A robust DataSource implementation.
 * <p>
 * <ul>
 * <li>Manages the number of connections closing connections that have been idle for some time.</li>
 * <li>Notifies when the datasource goes down and comes back up.</li>
 * <li>Provides PreparedStatement caching</li>
 * <li>Knows the busy connections</li>
 * <li>Traces connections that have been leaked</li>
 * </ul>
 * </p>
 */
public class ConnectionPool implements DataSourcePool {

  private static final Logger logger = LoggerFactory.getLogger(ConnectionPool.class);

  /**
   * The name given to this dataSource.
   */
  private final String name;

  private final DataSourceConfig config;

  /**
   * Used to notify of changes to the DataSource status.
   */
  private final DataSourceAlert notify;

  /**
   * Optional listener that can be notified when connections are got from and
   * put back into the pool.
   */
  private final DataSourcePoolListener poolListener;

  /**
   * Properties used to create a Connection.
   */
  private final Properties connectionProps;

  /**
   * Queries, that are run for each connection on first open.
   */
  private final List<String> initSql;

  /**
   * The jdbc connection url.
   */
  private final String databaseUrl;

  /**
   * The jdbc driver.
   */
  private final String databaseDriver;

  /**
   * The sql used to test a connection.
   */
  private final String heartbeatsql;

  private final int heartbeatFreqSecs;

  private final int heartbeatTimeoutSeconds;


  private final long trimPoolFreqMillis;

  /**
   * The transaction isolation level as per java.sql.Connection.
   */
  private final int transactionIsolation;

  /**
   * The default autoCommit setting for Connections in this pool.
   */
  private final boolean autoCommit;

  private final boolean readOnly;

  private final boolean failOnStart;

  /**
   * Max idle time in millis.
   */
  private final int maxInactiveMillis;

  /**
   * Max age a connection is allowed in millis.
   * A value of 0 means no limit (no trimming based on max age).
   */
  private final long maxAgeMillis;

  /**
   * Flag set to true to capture stackTraces (can be expensive).
   */
  private boolean captureStackTrace;

  /**
   * The max size of the stack trace to report.
   */
  private final int maxStackTraceSize;

  /**
   * flag to indicate we have sent an alert message.
   */
  private boolean dataSourceDownAlertSent;

  /**
   * The time the pool was last trimmed.
   */
  private long lastTrimTime;

  /**
   * HeartBeat checking will discover when it goes down, and comes back up again.
   */
  private boolean dataSourceUp;

  /**
   * Stores the dataSourceDown-reason (if there is any)
   */
  private SQLException dataSourceDownReason;

  /**
   * The current alert.
   */
  private AtomicBoolean inWarningMode = new AtomicBoolean();

  /**
   * The minimum number of connections this pool will maintain.
   */
  private int minConnections;

  /**
   * The maximum number of connections this pool will grow to.
   */
  private int maxConnections;

  /**
   * The number of connections to exceed before a warning Alert is fired.
   */
  private int warningSize;

  /**
   * The time a thread will wait for a connection to become available.
   */
  private final int waitTimeoutMillis;

  /**
   * The size of the preparedStatement cache;
   */
  private int pstmtCacheSize;

  private final PooledConnectionQueue queue;

  private Timer heartBeatTimer;

  /**
   * Used to find and close() leaked connections. Leaked connections are
   * thought to be busy but have not been used for some time. Each time a
   * connection is used it sets it's lastUsedTime.
   */
  private long leakTimeMinutes;

  public ConnectionPool(String name, DataSourceConfig params) {
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
    this.maxAgeMillis = 60000 * params.getMaxAgeMinutes();
    this.leakTimeMinutes = params.getLeakTimeMinutes();
    this.captureStackTrace = params.isCaptureStackTrace();
    this.maxStackTraceSize = params.getMaxStackTraceSize();
    this.databaseDriver = params.getDriver();
    this.databaseUrl = params.getUrl();
    this.pstmtCacheSize = params.getPstmtCacheSize();

    this.minConnections = params.getMinConnections();
    this.maxConnections = params.getMaxConnections();
    this.waitTimeoutMillis = params.getWaitTimeoutMillis();
    this.heartbeatsql = params.getHeartbeatSql();
    this.heartbeatFreqSecs = params.getHeartbeatFreqSecs();
    this.heartbeatTimeoutSeconds = params.getHeartbeatTimeoutSeconds();
    this.trimPoolFreqMillis = 1000 * params.getTrimPoolFreqSecs();

    queue = new PooledConnectionQueue(this);

    String un = params.getUsername();
    String pw = params.getPassword();
    if (un == null) {
      throw new DataSourceConfigurationException("DataSource user is null?");
    }
    if (pw == null) {
      throw new DataSourceConfigurationException("DataSource password is null?");
    }
    this.connectionProps = new Properties();
    this.connectionProps.setProperty("user", un);
    this.connectionProps.setProperty("password", pw);

    Map<String, String> customProperties = params.getCustomProperties();
    if (customProperties != null) {
      Set<Entry<String, String>> entrySet = customProperties.entrySet();
      for (Entry<String, String> entry : entrySet) {
        this.connectionProps.setProperty(entry.getKey(), entry.getValue());
      }
    }
    checkDriver();
    try {
      if (!params.isOffline()) {
        if (config.useInitDatabase()) {
          initialiseDatabase();
        }
        initialise();
      }
    } catch (SQLException e) {
      throw new DataSourceInitialiseException("Error initialising DataSource: " + e.getMessage(), e);
    }
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

  private void checkDriver() {

    // Ensure database driver is loaded
    try {
      ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
      if (contextLoader != null) {
        Class.forName(databaseDriver, true, contextLoader);
      } else {
        Class.forName(databaseDriver, true, this.getClass().getClassLoader());
      }
    } catch (Throwable e) {
      throw new IllegalStateException("Problem loading Database Driver [" + this.databaseDriver + "]: " + e.getMessage(), e);
    }
  }

  private void initialise() throws SQLException {

    //noinspection StringBufferReplaceableByString
    StringBuilder sb = new StringBuilder(70);
    sb.append("DataSourcePool [").append(name);
    sb.append("] autoCommit[").append(autoCommit);
    sb.append("] transIsolation[").append(TransactionIsolation.getDescription(transactionIsolation));
    sb.append("] min[").append(minConnections);
    sb.append("] max[").append(maxConnections).append("]");

    logger.info(sb.toString());

    try {
      dataSourceUp = true;
      queue.ensureMinimumConnections();
      startHeartBeatIfStopped();
    } catch (SQLException e) {
      if (failOnStart) {
        throw e;
      }
      logger.error("Error trying to ensure minimum connections, maybe db server is down - message:" + e.getMessage(), e);
    }
  }

  /**
   * Initialise the database using the owner credentials if we can't connect using the normal credentials.
   * <p>
   * That is, if we think the username doesn't exist in the DB, initialise the DB using the owner credentials.
   * </p>
   */
  private void initialiseDatabase() throws SQLException {
    try (Connection connection = createUnpooledConnection(connectionProps, false)) {
      // successfully obtained a connection so skip initDatabase
      connection.clearWarnings();
    } catch (SQLException e) {
      logger.info("Obtaining connection using ownerUsername:{} to initialise database", config.getOwnerUsername());
      // expected when user does not exists, obtain a connection using owner credentials
      try (Connection ownerConnection = createUnpooledConnection(config.getOwnerUsername(), config.getOwnerPassword())) {
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
  public String getName() {
    return name;
  }

  /**
   * Return the max size of stack traces used when trying to find connection pool leaks.
   * <p>
   * This is only used when {@link #isCaptureStackTrace()} is true.
   * </p>
   */
  int getMaxStackTraceSize() {
    return maxStackTraceSize;
  }

  /**
   * Returns false when the dataSource is down.
   */
  @Override
  public boolean isDataSourceUp() {
    return dataSourceUp;
  }

  @Override
  public SQLException getDataSourceDownReason() {
    return dataSourceDownReason;
  }

  /**
   * Called when the pool hits the warning level.
   */
  protected void notifyWarning(String msg) {
    if (inWarningMode.compareAndSet(false, true)) {
      // send an Error to the event log...
      logger.warn(msg);
      if (notify != null) {
        notify.dataSourceWarning(this, msg);
      }
    }
  }

  private synchronized void notifyDataSourceIsDown(SQLException ex) {

    if (dataSourceUp) {
      reset();
    }
    dataSourceUp = false;
    if (ex != null) {
      dataSourceDownReason = ex;
    }
    if (!dataSourceDownAlertSent) {
      dataSourceDownAlertSent = true;
      logger.error("FATAL: DataSourcePool [" + name + "] is down or has network error!!!", ex);
      if (notify != null) {
        notify.dataSourceDown(this, ex);
      }
    }
  }

  private synchronized void notifyDataSourceIsUp() {
    if (dataSourceDownAlertSent) {
      // set to false here, so that a getConnection() call in DataSourceAlert.dataSourceUp
      // in same thread does not fire the event again (and end in recursion)
      // all other threads will be blocked, becasue method is synchronized.
      dataSourceDownAlertSent = false;
      logger.error("RESOLVED FATAL: DataSourcePool [" + name + "] is back up!");
      if (notify != null) {
        notify.dataSourceUp(this);
      }

    } else if (!dataSourceUp) {
      logger.info("DataSourcePool [" + name + "] is back up!");
    }

    if (!dataSourceUp) {
      dataSourceUp = true;
      dataSourceDownReason = null;
      reset();
      startHeartBeatIfStopped();
    }
  }

  /**
   * Trim connections (in the free list) based on idle time and maximum age.
   */
  private void trimIdleConnections() {
    if (System.currentTimeMillis() > (lastTrimTime + trimPoolFreqMillis)) {
      try {
        queue.trim(maxInactiveMillis, maxAgeMillis);
        lastTrimTime = System.currentTimeMillis();
      } catch (Exception e) {
        logger.error("Error trying to trim idle connections - message:" + e.getMessage(), e);
      }
    }
  }

  /**
   * Check the dataSource is up. Trim connections.
   * <p>
   * This is called by the HeartbeatRunnable which should be scheduled to
   * run periodically (every heartbeatFreqSecs seconds actually).
   * </p>
   */
  private void checkDataSource() {

    // first trim idle connections
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
        logger.warn("Can't close connection in checkDataSource!");
      }
    }
  }

  /**
   * Initializes the connection we got from the driver.
   */
  private void initConnection(Connection conn) throws SQLException {
    conn.setAutoCommit(autoCommit);
    // isolation level is set globally for all connections (at least for H2)
    // and you will need admin rights - so we do not change it, if it already
    // matches.
    if (conn.getTransactionIsolation() != transactionIsolation) {
      conn.setTransactionIsolation(transactionIsolation);
    }
    if (readOnly) {
      conn.setReadOnly(true);
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
  public Connection createUnpooledConnection(String username, String password) throws SQLException {

    Properties properties = new Properties(connectionProps);
    properties.setProperty("user", username);
    properties.setProperty("password", password);
    return createUnpooledConnection(properties, true);
  }

  /**
   * Create an un-pooled connection.
   */
  public Connection createUnpooledConnection() throws SQLException {
    return createUnpooledConnection(connectionProps, true);
  }

  private Connection createUnpooledConnection(Properties properties, boolean notifyIsDown) throws SQLException {
    try {
      Connection conn = DriverManager.getConnection(databaseUrl, properties);
      initConnection(conn);
      return conn;

    } catch (SQLException ex) {
      if (notifyIsDown) {
        notifyDataSourceIsDown(null);
      }
      throw ex;
    }
  }

  /**
   * Set a new maximum size. The pool should respect this new maximum
   * immediately and not require a restart. You may want to increase the
   * maxConnections if the pool gets large and hits the warning level.
   */
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

  /**
   * Set a new maximum size. The pool should respect this new maximum
   * immediately and not require a restart. You may want to increase the
   * maxConnections if the pool gets large and hits the warning and or alert
   * levels.
   */
  @Override
  public void setWarningSize(int warningSize) {
    queue.setWarningSize(warningSize);
    this.warningSize = warningSize;
  }

  /**
   * Return the warning size. When the pool hits this size it can send a
   * notify message to an administrator.
   */
  @Override
  public int getWarningSize() {
    return warningSize;
  }

  /**
   * Return the time in millis that threads will wait when the pool has hit
   * the max size. These threads wait for connections to be returned by the
   * busy connections.
   */
  public int getWaitTimeoutMillis() {
    return waitTimeoutMillis;
  }

  /**
   * Return the time after which inactive connections are trimmed.
   */
  public int getMaxInactiveMillis() {
    return maxInactiveMillis;
  }

  /**
   * Return the maximum age a connection is allowed to be before it is trimmed
   * out of the pool. This value can be 0 which means there is no maximum age.
   */
  public long getMaxAgeMillis() {
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
        logger.error("Error closing resultSet", e);
      }
      try {
        if (stmt != null) {
          stmt.close();
        }
      } catch (SQLException e) {
        logger.error("Error closing statement", e);
      }
    }
  }

  /**
   * Make sure the connection is still ok to use. If not then remove it from
   * the pool.
   */
  boolean validateConnection(PooledConnection conn) {
    try {
      return testConnection(conn);

    } catch (Exception e) {
      logger.warn("heartbeatsql test failed on connection:" + conn.getName() + " message:" + e.getMessage());
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
   * </p>
   *
   * @param pooledConnection the returning connection
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

  /**
   * Collect statistics of a connection that is fully closing
   */
  void reportClosingConnection(PooledConnection pooledConnection) {

    queue.reportClosingConnection(pooledConnection);
  }

  /**
   * Returns information describing connections that are currently being used.
   */
  public String getBusyConnectionInformation() {

    return queue.getBusyConnectionInformation();
  }

  /**
   * Dumps the busy connection information to the logs.
   * <p>
   * This includes the stackTrace elements if they are being captured. This is
   * useful when needing to look a potential connection pool leaks.
   * </p>
   */
  public void dumpBusyConnectionInformation() {

    queue.dumpBusyConnectionInformation();
  }

  /**
   * Close any busy connections that have not been used for some time.
   * <p>
   * These connections are considered to have leaked from the connection pool.
   * </p>
   * <p>
   * Connection leaks occur when code doesn't ensure that connections are
   * closed() after they have been finished with. There should be an
   * appropriate try catch finally block to ensure connections are always
   * closed and put back into the pool.
   * </p>
   */
  public void closeBusyConnections(long leakTimeMinutes) {

    queue.closeBusyConnections(leakTimeMinutes);
  }

  /**
   * Grow the pool by creating a new connection. The connection can either be
   * added to the available list, or returned.
   * <p>
   * This method is protected by synchronization in calling methods.
   * </p>
   */
  PooledConnection createConnectionForQueue(int connId) throws SQLException {

    try {
      Connection c = createUnpooledConnection();

      PooledConnection pc = new PooledConnection(this, connId, c);
      pc.resetForUse();

      if (!dataSourceUp) {
        notifyDataSourceIsUp();
      }
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
  public void reset() {
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
   * </p>
   */
  private PooledConnection getPooledConnection() throws SQLException {

    PooledConnection c = queue.getPooledConnection();

    if (captureStackTrace) {
      c.setStackTrace(Thread.currentThread().getStackTrace());
    }

    if (poolListener != null) {
      poolListener.onAfterBorrowConnection(c);
    }
    return c;
  }

  /**
   * Send a message to the DataSourceAlertListener to test it. This is so that
   * you can make sure the alerter is configured correctly etc.
   */
  public void testAlert() {

    String msg = "Just testing if alert message is sent successfully.";

    if (notify != null) {
      notify.dataSourceWarning(this, msg);
    }
  }

  @Override
  public void shutdown() {
    shutdown(false);
  }

  /**
   * This will close all the free connections, and then go into a wait loop,
   * waiting for the busy connections to be freed.
   * <p>
   * <p>
   * The DataSources's should be shutdown AFTER thread pools. Leaked
   * Connections are not waited on, as that would hang the server.
   * </p>
   */
  @Override
  public synchronized void shutdown(boolean deregisterDriver) {
    offline();
    if (deregisterDriver) {
      deregisterDriver();
    }
  }

  @Override
  public synchronized void offline() {
    stopHeartBeatIfRunning();
    queue.shutdown();
    dataSourceUp = false;
  }

  @Override
  public synchronized boolean isOnline() {
    return dataSourceUp;
  }

  @Override
  public synchronized void online() throws SQLException {
    if (!dataSourceUp) {
      initialise();
    }
  }

  private void startHeartBeatIfStopped() {
    // only start if it is not already running
    if (heartBeatTimer == null) {
      int freqMillis = heartbeatFreqSecs * 1000;
      if (freqMillis > 0) {
        heartBeatTimer = new Timer(name + ".heartBeat", true);
        heartBeatTimer.scheduleAtFixedRate(new HeartBeatRunnable(), freqMillis, freqMillis);
      }
    }
  }

  private void stopHeartBeatIfRunning() {
    // only stop if it was running
    if (heartBeatTimer != null) {
      heartBeatTimer.cancel();
      heartBeatTimer = null;
    }
  }

  /**
   * Return the default autoCommit setting Connections in this pool will use.
   *
   * @return true if the pool defaults autoCommit to true
   */
  @Override
  public boolean isAutoCommit() {
    return autoCommit;
  }

  /**
   * Return the default transaction isolation level connections in this pool
   * should have.
   *
   * @return the default transaction isolation level
   */
  int getTransactionIsolation() {
    return transactionIsolation;
  }

  /**
   * Return true if the connection pool is currently capturing the StackTrace
   * when connections are 'got' from the pool.
   * <p>
   * This is set to true to help diagnose connection pool leaks.
   * </p>
   */
  public boolean isCaptureStackTrace() {
    return captureStackTrace;
  }

  /**
   * Set this to true means that the StackElements are captured every time a
   * connection is retrieved from the pool. This can be used to identify
   * connection pool leaks.
   */
  public void setCaptureStackTrace(boolean captureStackTrace) {
    this.captureStackTrace = captureStackTrace;
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
    Connection conn = DriverManager.getConnection(databaseUrl, props);
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
   * For detecting and closing leaked connections. Connections that have been
   * busy for more than leakTimeMinutes are considered leaks and will be
   * closed on a reset().
   * <p>
   * If you want to use a connection for that longer then you should consider
   * creating an unpooled connection or setting longRunning to true on that
   * connection.
   * </p>
   */
  public void setLeakTimeMinutes(long leakTimeMinutes) {
    this.leakTimeMinutes = leakTimeMinutes;
  }

  /**
   * Return the number of minutes after which a busy connection could be
   * considered leaked from the connection pool.
   */
  public long getLeakTimeMinutes() {
    return leakTimeMinutes;
  }

  /**
   * Return the preparedStatement cache size.
   */
  public int getPstmtCacheSize() {
    return pstmtCacheSize;
  }

  /**
   * Set the preparedStatement cache size.
   */
  public void setPstmtCacheSize(int pstmtCacheSize) {
    this.pstmtCacheSize = pstmtCacheSize;
  }

  /**
   * Return the current status of the connection pool.
   * <p>
   * If you pass reset = true then the counters such as
   * hitCount, waitCount and highWaterMark are reset.
   * </p>
   */
  @Override
  public PoolStatus getStatus(boolean reset) {
    return queue.getStatus(reset);
  }

  /**
   * Return the aggregated load statistics collected on all the connections in the pool.
   */
  @Override
  public PoolStatistics getStatistics(boolean reset) {
    return queue.getStatistics(reset);
  }

  /**
   * Deregister the JDBC driver.
   */
  private void deregisterDriver() {
    try {
      logger.debug("Deregister the JDBC driver " + this.databaseDriver);
      DriverManager.deregisterDriver(DriverManager.getDriver(this.databaseUrl));
    } catch (SQLException e) {
      logger.warn("Error trying to deregister the JDBC driver " + this.databaseDriver, e);
    }
  }

  public static class Status implements PoolStatus {

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

    public String toString() {
      return "min[" + minSize + "] max[" + maxSize + "] free[" + free + "] busy[" + busy + "] waiting[" + waiting
        + "] highWaterMark[" + highWaterMark + "] waitCount[" + waitCount + "] hitCount[" + hitCount + "]";
    }

    /**
     * Return the min pool size.
     */
    @Override
    public int getMinSize() {
      return minSize;
    }

    /**
     * Return the max pool size.
     */
    @Override
    public int getMaxSize() {
      return maxSize;
    }

    /**
     * Return the current number of free connections in the pool.
     */
    @Override
    public int getFree() {
      return free;
    }

    /**
     * Return the current number of busy connections in the pool.
     */
    @Override
    public int getBusy() {
      return busy;
    }

    /**
     * Return the current number of threads waiting for a connection.
     */
    @Override
    public int getWaiting() {
      return waiting;
    }

    /**
     * Return the high water mark of busy connections.
     */
    @Override
    public int getHighWaterMark() {
      return highWaterMark;
    }

    /**
     * Return the total number of times a thread had to wait.
     */
    @Override
    public int getWaitCount() {
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
    public int getHitCount() {
      return hitCount;
    }

  }

}
