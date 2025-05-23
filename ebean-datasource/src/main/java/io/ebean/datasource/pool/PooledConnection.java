package io.ebean.datasource.pool;

import java.sql.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Is a connection that belongs to a DataSourcePool.
 * <p>
 * It is designed to be part of DataSourcePool. Closing the connection puts it
 * back into the pool.
 * <p>
 * It defaults autoCommit and Transaction Isolation to the defaults of the
 * DataSourcePool.
 * <p>
 * It has caching of Statements and PreparedStatements. Remembers the last
 * statement that was executed. Keeps statistics on how long it is in use.
 */
final class PooledConnection extends ConnectionDelegator {

  private static final String IDLE_CONNECTION_ACCESSED_ERROR = "Pooled Connection has been accessed whilst idle in the pool, via method: ";

  /**
   * Marker for when connection is closed due to exceeding the max allowed age.
   */
  private static final String REASON_MAXAGE = "maxAge";

  /**
   * Marker for when connection is closed due to exceeding the max inactive time.
   */
  private static final String REASON_IDLE = "idleTime";

  /**
   * Marker for when the connection is closed due to a reset.
   */
  private static final String REASON_RESET = "reset";

  /**
   * Set when connection is idle in the pool. In general when in the pool the
   * connection should not be modified.
   */
  private static final int STATUS_IDLE = 88;

  /**
   * Set when connection given to client.
   */
  private static final int STATUS_ACTIVE = 89;

  /**
   * Set when commit() or rollback() called.
   */
  private static final int STATUS_ENDED = 87;

  private static final String RO_POSTGRES_STATE = "25006";

  private static final int RO_MYSQL_1290 = 1290;

  /**
   * Constant for schema/catalog, when we are in SCHEMA_CATALOG_UNKNOWN state
   * This is used for correct cache key computation. (We cannot use 'null'
   * here, as we might get a collision in edge cases)
   */
  private static final String UNKNOWN = "@unknown";

  /**
   * The schema/catalog is unknown, that means, we have not yet touched the
   * value on the underlying connection.
   * We do not have to restore it.
   */
  private static final int SCHEMA_CATALOG_UNKNOWN = 0;

  /**
   * The schema/catalog is changed. The original value has to be restored on
   * close()
   */
  private static final int SCHEMA_CATALOG_CHANGED = 1;

  /**
   * We know the original value of the underlying connection, but there is no
   * demand to restore it.
   */
  private static final int SCHEMA_CATALOG_KNOWN = 2;

  private final String name;
  private final ConnectionPool pool;
  private final Connection connection;
  private final long creationTime;
  private final PstmtCache pstmtCache;
  private final ReentrantLock lock = new ReentrantLock();
  /**
   * The status of the connection. IDLE, ACTIVE or ENDED.
   */
  private int status = STATUS_IDLE;
  /**
   * The reason for a connection closing.
   */
  private String closeReason;
  /**
   * Flag to indicate that this connection had errors and should be checked to
   * make sure it is okay.
   */
  private boolean hadErrors;
  /**
   * Flag to indicate if we think there has been a DB failover and the pool is
   * connected to a read-only instance and should reset.
   */
  private boolean failoverToReadOnly;
  private boolean autoCommit;
  private boolean readOnly;
  private int transactionIsolation;
  private int schemaState = SCHEMA_CATALOG_UNKNOWN;
  private int catalogState = SCHEMA_CATALOG_UNKNOWN;

  // this is used for cache computation
  private String cacheKeySchema = UNKNOWN;
  private String cacheKeyCatalog = UNKNOWN;

  // original values are lazily initialized and restored on close()
  private String originalSchema;
  private String originalCatalog;

  private long startUseTime;
  private long lastUseTime;
  /**
   * The last statement executed by this connection.
   */
  private String lastStatement;
  /**
   * The non ebean method that created the connection.
   */
  private String createdByMethod;
  private StackTraceElement[] stackTrace;
  private final int maxStackTrace;
  /**
   * Slot position in the BusyConnectionBuffer.
   */
  private int slotId;


  /**
   * Construct the connection that can refer back to the pool it belongs to.
   * <p>
   * close() will return the connection back to the pool , while
   * closeDestroy() will close() the underlining connection properly.
   */
  PooledConnection(ConnectionPool pool, int uniqueId, Connection connection) throws SQLException {
    super(connection);
    this.pool = pool;
    this.connection = connection;
    this.name = pool.name() + uniqueId;
    this.originalSchema = pool.schema();
    this.originalCatalog = pool.catalog();
    this.autoCommit = pool.isAutoCommit();
    this.readOnly = pool.isReadOnly();
    this.transactionIsolation = pool.transactionIsolation();
    if (originalSchema != null) {
      this.schemaState = SCHEMA_CATALOG_KNOWN;
      this.cacheKeySchema = originalSchema;
    }
    if (originalCatalog != null) {
      this.catalogState = SCHEMA_CATALOG_KNOWN;
      this.cacheKeyCatalog = originalCatalog;
    }
    this.pstmtCache = new PstmtCache(pool.pstmtCacheSize());
    this.maxStackTrace = pool.maxStackTraceSize();
    this.creationTime = System.currentTimeMillis();
    this.lastUseTime = creationTime;
    pool.inc();
  }

  /**
   * For testing the pool without real connections.
   */
  PooledConnection(String name) {
    super(null);
    this.name = name;
    this.pool = null;
    this.connection = null;
    this.pstmtCache = null;
    this.maxStackTrace = 0;
    this.creationTime = System.currentTimeMillis();
    this.lastUseTime = creationTime;
  }

  /**
   * Return the slot position in the busy buffer.
   */
  int slotId() {
    return slotId;
  }

  /**
   * Set the slot position in the busy buffer.
   */
  void setSlotId(int slotId) {
    this.slotId = slotId;
  }

  /**
   * Return a string to identify the connection.
   */
  String name() {
    return name;
  }

  @Override
  public String toString() {
    return description();
  }

  private long busySeconds() {
    return (System.currentTimeMillis() - startUseTime) / 1000;
  }

  String description() {
    return "name[" + name + "] startTime[" + startUseTime() + "] busySeconds[" + busySeconds() + "] createdBy[" + createdByMethod() + "] stmt[" + lastStatement() + "]";
  }

  String fullDescription() {
    return "name[" + name + "] startTime[" + startUseTime() + "] busySeconds[" + busySeconds() + "] stackTrace[" + stackTraceAsString(stackTrace) + "] stmt[" + lastStatement() + "]";
  }

  /**
   * Close the connection fully NOT putting in back into the pool.
   * <p>
   * The logErrors parameter exists so that expected errors are not logged
   * such as when the database is known to be down.
   * </p>
   *
   * @param logErrors if false then don't log errors when closing
   */
  void closeConnectionFully(boolean logErrors) {
    if (Log.isLoggable(System.Logger.Level.TRACE)) {
      Log.trace("Closing Connection[{0}] reason[{1}], pstmtStats: {2}", name, closeReason, pstmtCache.description());
    }
    if (pool == null) {
      return; // this can happen in tests only.
    }
    pool.pstmtCacheMetrics(pstmtCache);
    pool.closeConnectionFullyAsync(this, logErrors);
  }

  /**
   * this method performs network IO and may block
   */
  void doCloseConnection(boolean logErrors) {
    pool.dec();
    long start = System.nanoTime();
    try {
      try {
        if (connection.isClosed()) {
          // Typically, the JDBC Driver has its own JVM shutdown hook and already
          // closed the connections in our DataSource pool so making this DEBUG level
          Log.trace("Closing Connection[{0}] that is already closed?", name);
          return;
        }
      } catch (SQLException ex) {
        if (logErrors) {
          Log.error("Error checking if connection [" + name + "] is closed", ex);
        }
      }
      try {
        clearPreparedStatementCache();
      } catch (SQLException ex) {
        if (logErrors) {
          Log.warn("Error when closing connection Statements", ex);
        }
      }
      try {
        // DB2 (and some other DBMS) may have uncommitted changes and do not allow close
        // so try to do a rollback.
        if (!connection.getAutoCommit()) {
          connection.rollback();
        }
      } catch (SQLException ex) {
        if (logErrors) {
          Log.warn("Could not perform rollback", ex);
        }
      }
      try {
        connection.close();
      } catch (SQLException ex) {
        if (logErrors || Log.isLoggable(System.Logger.Level.DEBUG)) {
          Log.error("Error when fully closing connection [" + fullDescription() + "]", ex);
        }
      }
    } finally {
      long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      if (millis > 500) {
        Log.warn("Closing connection [" + fullDescription() + "] took an unexpected long time of " + millis + " ms");
      }
    }
  }

  void clearPreparedStatementCache() throws SQLException {
    lock.lock();
    try {
      for (ExtendedPreparedStatement ps : pstmtCache.values()) {
        ps.closeDestroy();
      }

    } finally {
      lock.unlock();
    }
  }

  /**
   * Creates a wrapper ExtendedStatement so that I can get the executed sql. I
   * want to do this so that I can get the slowest query statements etc, and
   * log that information.
   */
  @Override
  public Statement createStatement() throws SQLException {
    if (status == STATUS_IDLE) {
      throw new SQLException(IDLE_CONNECTION_ACCESSED_ERROR + "createStatement()");
    }
    try {
      return connection.createStatement();
    } catch (SQLException ex) {
      markWithError(ex);
      throw ex;
    }
  }

  @Override
  public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
    if (status == STATUS_IDLE) {
      throw new SQLException(IDLE_CONNECTION_ACCESSED_ERROR + "createStatement()");
    }
    try {
      return connection.createStatement(resultSetType, resultSetConcurrency);
    } catch (SQLException ex) {
      markWithError(ex);
      throw ex;
    }
  }

  /**
   * Return a PreparedStatement back into the cache.
   */
  void returnPreparedStatement(ExtendedPreparedStatement pstmt) {
    lock.lock();
    try {
      if (!pstmtCache.returnStatement(pstmt)) {
        try {
          // Already an entry in the cache with the exact same SQL...
          pstmt.closeDestroy();
        } catch (SQLException e) {
          Log.error("Error closing PreparedStatement", e);
        }
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * This will try to use a cache of PreparedStatements.
   */
  @Override
  public PreparedStatement prepareStatement(String sql, int returnKeysFlag) throws SQLException {
    String key = sql + ':' + cacheKeySchema + ':' + cacheKeyCatalog + ':' + returnKeysFlag;
    return prepareStatement(sql, true, returnKeysFlag, key);
  }

  /**
   * This will try to use a cache of PreparedStatements.
   */
  @Override
  public PreparedStatement prepareStatement(String sql) throws SQLException {
    String key = sql + ':' + cacheKeySchema + ':' + cacheKeyCatalog;
    return prepareStatement(sql, false, 0, key);
  }

  /**
   * This will try to use a cache of PreparedStatements.
   */
  private PreparedStatement prepareStatement(String sql, boolean useFlag, int flag, String cacheKey) throws SQLException {
    if (status == STATUS_IDLE) {
      throw new SQLException(IDLE_CONNECTION_ACCESSED_ERROR + "prepareStatement()");
    }
    lock.lock();
    try {
      lastStatement = sql;
      // try to get a matching cached PStmt from the cache.
      ExtendedPreparedStatement pstmt = pstmtCache.remove(cacheKey);
      if (pstmt != null) {
        return pstmt.reset();
      }

      PreparedStatement actualPstmt;
      if (useFlag) {
        actualPstmt = connection.prepareStatement(sql, flag);
      } else {
        actualPstmt = connection.prepareStatement(sql);
      }
      return new ExtendedPreparedStatement(this, actualPstmt, cacheKey);
    } catch (SQLException ex) {
      markWithError(ex);
      throw ex;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
    if (status == STATUS_IDLE) {
      throw new SQLException(IDLE_CONNECTION_ACCESSED_ERROR + "prepareStatement()");
    }
    try {
      // no caching when creating PreparedStatements this way
      lastStatement = sql;
      return connection.prepareStatement(sql, resultSetType, resultSetConcurrency);
    } catch (SQLException ex) {
      markWithError(ex);
      throw ex;
    }
  }

  /**
   * Reset the connection for returning to the client. Resets the status,
   * startUseTime and hadErrors.
   */
  void resetForUse() {
    this.status = STATUS_ACTIVE;
    this.startUseTime = System.currentTimeMillis();
    this.createdByMethod = null;
    this.lastStatement = null;
    this.hadErrors = false;
  }

  /**
   * When an error occurs during use add it the connection.
   * <p>
   * Any PooledConnection that has an error is checked to make sure it works
   * before it is placed back into the connection pool.
   */
  void markWithError(SQLException ex) {
    hadErrors = true;
    failoverToReadOnly = isReadOnlyError(ex);
  }

  /**
   * Return true if this connection is on a read-only DB instance most likely
   * due to a DB failover and the pool should reset in this case.
   */
  private boolean isReadOnlyError(SQLException ex) {
    return (RO_POSTGRES_STATE.equals(ex.getSQLState()) && isReadOnlyMessage(ex))
      || (RO_MYSQL_1290 == ex.getErrorCode() && isReadOnlyMessage(ex));
  }

  private boolean isReadOnlyMessage(SQLException ex) {
    final String msg = ex.getMessage();
    return msg != null && msg.contains("read-only");
  }

  /**
   * close the connection putting it back into the connection pool.
   * <p>
   * Note that to ensure that the next transaction starts at the correct time
   * a commit() or rollback() should be called. If neither has occurred at this
   * time then a rollback() is used (to end the transaction).
   * <p>
   * To close the connection fully use closeConnectionFully().
   */
  @Override
  public void close() throws SQLException {
    closePooledConnection(true);
  }

  void closePooledConnection(boolean testPool) throws SQLException {
    if (status == STATUS_IDLE) {
      throw new SQLException(IDLE_CONNECTION_ACCESSED_ERROR + "close()");
    }
    boolean mayHaveUncommittedChanges = !autoCommit && !readOnly && status == STATUS_ACTIVE;
    if (mayHaveUncommittedChanges && pool.enforceCleanClose()) {
      pool.returnConnectionForceClose(this, testPool);
      throw new AssertionError("Tried to close a dirty connection. See https://github.com/ebean-orm/ebean-datasource/issues/116 for details.");
    }
    if (hadErrors) {
      if (failoverToReadOnly) {
        pool.returnConnectionReset(this);
        return;
      } else if (pool.invalidConnection(this)) {
        // the connection is BAD, remove it, close it and test the pool
        pool.returnConnectionForceClose(this, testPool);
        return;
      }
    }

    try {
      if (connection.isClosed()) {
        pool.removeClosedConnection(this);
        return;
      }
      if (mayHaveUncommittedChanges) {
        Log.warn("Tried to close a dirty connection at {0}. See https://github.com/ebean-orm/ebean-datasource/issues/116 for details.",
          stackTraceAsString(Thread.currentThread().getStackTrace()));
        connection.rollback();
      }
      // reset the autoCommit back if client code changed it
      if (autoCommit != pool.isAutoCommit()) {
        connection.setAutoCommit(pool.isAutoCommit());
        autoCommit = pool.isAutoCommit();
      }
      // Generally resetting Isolation level seems expensive.
      // Hence using resetIsolationReadOnlyRequired flag
      // performance reasons.
      if (transactionIsolation != pool.transactionIsolation()) {
        connection.setTransactionIsolation(pool.transactionIsolation());
        transactionIsolation = pool.transactionIsolation();
      }
      if (readOnly != pool.isReadOnly()) {
        connection.setReadOnly(readOnly);
        readOnly = pool.isReadOnly();
      }

      if (catalogState == SCHEMA_CATALOG_CHANGED) {
        connection.setCatalog(originalCatalog);
        cacheKeyCatalog = originalCatalog;
        catalogState = SCHEMA_CATALOG_KNOWN;
      }

      if (schemaState == SCHEMA_CATALOG_CHANGED) {
        connection.setSchema(originalSchema);
        // we can use original value for cache computation from now on
        cacheKeySchema = originalSchema;
        schemaState = SCHEMA_CATALOG_KNOWN;
      }

      // the connection is assumed GOOD so put it back in the pool
      lastUseTime = System.currentTimeMillis();
      connection.clearWarnings();
      status = STATUS_IDLE;
      pool.returnConnection(this);

    } catch (Exception ex) {
      // the connection is BAD, remove it, close it and test the pool
      Log.warn("Error when trying to return connection to pool, closing fully.", ex);
      pool.returnConnectionForceClose(this, testPool);
    }
  }

  /**
   * Return true if the connection is too old.
   */
  private boolean exceedsMaxAge(long maxAgeMillis) {
    if (maxAgeMillis > 0 && (creationTime < (System.currentTimeMillis() - maxAgeMillis))) {
      this.closeReason = REASON_MAXAGE;
      return true;
    }
    return false;
  }

  boolean shouldTrimOnReturn(long lastResetTime, long maxAgeMillis) {
    if (creationTime <= lastResetTime) {
      this.closeReason = REASON_RESET;
      return true;
    }
    return exceedsMaxAge(maxAgeMillis);
  }

  /**
   * Return true if the connection has been idle for too long or is too old.
   */
  boolean shouldTrim(long usedSince, long createdSince) {
    if (lastUseTime < usedSince) {
      // been idle for too long so trim it
      this.closeReason = REASON_IDLE;
      return true;
    }
    if (createdSince > 0 && createdSince > creationTime) {
      // exceeds max age so trim it
      this.closeReason = REASON_MAXAGE;
      return true;
    }
    return false;
  }

  /**
   * Return the time the connection was passed to the client code.
   * <p>
   * Used to detect busy connections that could be leaks.
   */
  private long startUseTime() {
    return startUseTime;
  }

  /**
   * Returns the time the connection was last used.
   * <p>
   * Used to close connections that have been idle for some time. Typically 5 minutes.
   */
  long lastUsedTime() {
    return lastUseTime;
  }

  /**
   * Returns the last sql statement executed.
   */
  private String lastStatement() {
    return lastStatement;
  }

  /**
   * Called by ExtendedStatement to trace the sql being executed.
   */
  void setLastStatement(String lastStatement) {
    this.lastStatement = lastStatement;
  }

  /**
   * Also note the read only status needs to be reset when put back into the pool.
   */
  @Override
  public void setReadOnly(boolean readOnly) throws SQLException {
    if (status == STATUS_IDLE) {
      throw new SQLException(IDLE_CONNECTION_ACCESSED_ERROR + "setTransactionIsolation()");
    }
    try {
      connection.setReadOnly(readOnly);
      this.readOnly = readOnly;
    } catch (SQLException ex) {
      markWithError(ex);
      throw ex;
    }
  }


  /**
   * Also note the Isolation level needs to be reset when put back into the pool.
   */
  @Override
  public void setTransactionIsolation(int level) throws SQLException {
    if (status == STATUS_IDLE) {
      throw new SQLException(IDLE_CONNECTION_ACCESSED_ERROR + "setTransactionIsolation()");
    }
    try {
      connection.setTransactionIsolation(level);
      this.transactionIsolation = level;
    } catch (SQLException ex) {
      markWithError(ex);
      throw ex;
    }
  }

  @Override
  public boolean isClosed() throws SQLException {
    return status == STATUS_IDLE ? true : connection.isClosed();
  }

  //
  //
  // Simple wrapper methods which pass a method call onto the acutal
  // connection object. These methods are safe-guarded to prevent use of
  // the methods whilst the connection is in the connection pool.
  //
  //
  @Override
  public void clearWarnings() throws SQLException {
    if (status == STATUS_IDLE) {
      throw new SQLException(IDLE_CONNECTION_ACCESSED_ERROR + "clearWarnings()");
    }
    connection.clearWarnings();
  }

  @Override
  public void commit() throws SQLException {
    if (status == STATUS_IDLE) {
      throw new SQLException(IDLE_CONNECTION_ACCESSED_ERROR + "commit()");
    }
    try {
      status = STATUS_ENDED;
      connection.commit();
    } catch (SQLException ex) {
      markWithError(ex);
      throw ex;
    }
  }

  @Override
  public boolean getAutoCommit() throws SQLException {
    if (status == STATUS_IDLE) {
      throw new SQLException(IDLE_CONNECTION_ACCESSED_ERROR + "getAutoCommit()");
    }
    return connection.getAutoCommit();
  }

  @Override
  public String getCatalog() throws SQLException {
    if (status == STATUS_IDLE) {
      throw new SQLException(IDLE_CONNECTION_ACCESSED_ERROR + "getCatalog()");
    }
    return connection.getCatalog();
  }

  @Override
  public DatabaseMetaData getMetaData() throws SQLException {
    if (status == STATUS_IDLE) {
      throw new SQLException(IDLE_CONNECTION_ACCESSED_ERROR + "getMetaData()");
    }
    return connection.getMetaData();
  }

  @Override
  public int getTransactionIsolation() throws SQLException {
    if (status == STATUS_IDLE) {
      throw new SQLException(IDLE_CONNECTION_ACCESSED_ERROR + "getTransactionIsolation()");
    }
    return connection.getTransactionIsolation();
  }

  @Override
  public Map<String, Class<?>> getTypeMap() throws SQLException {
    if (status == STATUS_IDLE) {
      throw new SQLException(IDLE_CONNECTION_ACCESSED_ERROR + "getTypeMap()");
    }
    return connection.getTypeMap();
  }

  @Override
  public SQLWarning getWarnings() throws SQLException {
    if (status == STATUS_IDLE) {
      throw new SQLException(IDLE_CONNECTION_ACCESSED_ERROR + "getWarnings()");
    }
    return connection.getWarnings();
  }

  @Override
  public boolean isReadOnly() throws SQLException {
    if (status == STATUS_IDLE) {
      throw new SQLException(IDLE_CONNECTION_ACCESSED_ERROR + "isReadOnly()");
    }
    return connection.isReadOnly();
  }

  @Override
  public String nativeSQL(String sql) throws SQLException {
    if (status == STATUS_IDLE) {
      throw new SQLException(IDLE_CONNECTION_ACCESSED_ERROR + "nativeSQL()");
    }
    lastStatement = sql;
    return connection.nativeSQL(sql);
  }

  @Override
  public CallableStatement prepareCall(String sql) throws SQLException {
    if (status == STATUS_IDLE) {
      throw new SQLException(IDLE_CONNECTION_ACCESSED_ERROR + "prepareCall()");
    }
    lastStatement = sql;
    return connection.prepareCall(sql);
  }

  @Override
  public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurreny) throws SQLException {
    if (status == STATUS_IDLE) {
      throw new SQLException(IDLE_CONNECTION_ACCESSED_ERROR + "prepareCall()");
    }
    lastStatement = sql;
    return connection.prepareCall(sql, resultSetType, resultSetConcurreny);
  }

  @Override
  public void rollback() throws SQLException {
    if (status == STATUS_IDLE) {
      throw new SQLException(IDLE_CONNECTION_ACCESSED_ERROR + "rollback()");
    }
    try {
      status = STATUS_ENDED;
      connection.rollback();
    } catch (SQLException ex) {
      markWithError(ex);
      throw ex;
    }
  }

  @Override
  public void setAutoCommit(boolean autoCommit) throws SQLException {
    if (status == STATUS_IDLE) {
      throw new SQLException(IDLE_CONNECTION_ACCESSED_ERROR + "setAutoCommit()");
    }
    try {
      connection.setAutoCommit(autoCommit);
      this.autoCommit = autoCommit;
    } catch (SQLException ex) {
      markWithError(ex);
      throw ex;
    }
  }

  @Override
  public void setSchema(String schema) throws SQLException {
    if (status == STATUS_IDLE) {
      throw new SQLException(IDLE_CONNECTION_ACCESSED_ERROR + "setSchema()");
    }
    if (schemaState == SCHEMA_CATALOG_UNKNOWN) {
      // lazily initialise the originalSchema
      originalSchema = getSchema();
      // state would be KNOWN here
    }
    schemaState = SCHEMA_CATALOG_CHANGED;
    cacheKeySchema = schema;
    connection.setSchema(schema);
  }

  @Override
  public void setCatalog(String catalog) throws SQLException {
    if (status == STATUS_IDLE) {
      throw new SQLException(IDLE_CONNECTION_ACCESSED_ERROR + "setCatalog()");
    }
    if (schemaState == SCHEMA_CATALOG_UNKNOWN) {
      originalCatalog = getCatalog();
    }
    catalogState = SCHEMA_CATALOG_CHANGED;
    cacheKeyCatalog = catalog;
    connection.setCatalog(catalog);
  }

  @Override
  public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
    if (status == STATUS_IDLE) {
      throw new SQLException(IDLE_CONNECTION_ACCESSED_ERROR + "setTypeMap()");
    }
    connection.setTypeMap(map);
  }

  @Override
  public Savepoint setSavepoint() throws SQLException {
    try {
      return connection.setSavepoint();
    } catch (SQLException ex) {
      markWithError(ex);
      throw ex;
    }
  }

  @Override
  public Savepoint setSavepoint(String savepointName) throws SQLException {
    try {
      return connection.setSavepoint(savepointName);
    } catch (SQLException ex) {
      markWithError(ex);
      throw ex;
    }
  }

  @Override
  public void rollback(Savepoint sp) throws SQLException {
    try {
      connection.rollback(sp);
    } catch (SQLException ex) {
      markWithError(ex);
      throw ex;
    }
  }

  @Override
  public void releaseSavepoint(Savepoint sp) throws SQLException {
    try {
      connection.releaseSavepoint(sp);
    } catch (SQLException ex) {
      markWithError(ex);
      throw ex;
    }
  }

  @Override
  public void setHoldability(int i) throws SQLException {
    try {
      connection.setHoldability(i);
    } catch (SQLException ex) {
      markWithError(ex);
      throw ex;
    }
  }

  @Override
  public int getHoldability() throws SQLException {
    try {
      return connection.getHoldability();
    } catch (SQLException ex) {
      markWithError(ex);
      throw ex;
    }
  }

  @Override
  public Statement createStatement(int i, int x, int y) throws SQLException {
    try {
      return connection.createStatement(i, x, y);
    } catch (SQLException ex) {
      markWithError(ex);
      throw ex;
    }
  }

  @Override
  public PreparedStatement prepareStatement(String s, int i, int x, int y) throws SQLException {
    try {
      return connection.prepareStatement(s, i, x, y);
    } catch (SQLException ex) {
      markWithError(ex);
      throw ex;
    }
  }

  @Override
  public PreparedStatement prepareStatement(String s, int[] i) throws SQLException {
    try {
      return connection.prepareStatement(s, i);
    } catch (SQLException ex) {
      markWithError(ex);
      throw ex;
    }
  }

  @Override
  public PreparedStatement prepareStatement(String s, String[] s2) throws SQLException {
    try {
      return connection.prepareStatement(s, s2);
    } catch (SQLException ex) {
      markWithError(ex);
      throw ex;
    }
  }

  @Override
  public CallableStatement prepareCall(String s, int i, int x, int y) throws SQLException {
    try {
      return connection.prepareCall(s, i, x, y);
    } catch (SQLException ex) {
      markWithError(ex);
      throw ex;
    }
  }

  /**
   * Returns the method that created the connection.
   * <p>
   * Used to help finding connection pool leaks.
   * </p>
   */
  private String createdByMethod() {
    if (createdByMethod != null) {
      return createdByMethod;
    }
    if (stackTrace == null) {
      return null;
    }

    for (StackTraceElement stackTraceElement : stackTrace) {
      String methodLine = stackTraceElement.toString();
      if (includeMethodLine(methodLine)) {
        createdByMethod = methodLine;
        return createdByMethod;
      }
    }
    return null;
  }

  private boolean includeMethodLine(String methodLine) {
    if (methodLine.startsWith("java.lang.") || methodLine.startsWith("java.util.")) {
      return false;
    }
    return !methodLine.startsWith("io.ebean");
  }

  /**
   * Set the stack trace to help find connection pool leaks.
   */
  void setStackTrace(StackTraceElement[] stackTrace) {
    this.stackTrace = stackTrace;
  }

  /**
   * Return the stackTrace as a String for logging purposes.
   */
  private String stackTraceAsString(StackTraceElement[] stackTrace) {
    if (stackTrace == null) {
      return "";
    }

    // filter off the top of the stack that we are not interested in
    ArrayList<StackTraceElement> filteredList = new ArrayList<>();
    boolean include = false;
    for (StackTraceElement stackTraceElement : stackTrace) {
      if (!include && includeMethodLine(stackTraceElement.getClassName())) {
        include = true;
      }
      if (include && filteredList.size() < maxStackTrace) {
        filteredList.add(stackTraceElement);
      }
    }
    if (filteredList.isEmpty()) {
      // the list was empty. Because the error was in ebean code.
      for (StackTraceElement stackTraceElement : stackTrace) {
        if (filteredList.size() < maxStackTrace) {
          filteredList.add(stackTraceElement);
        }
      }
    }
    return filteredList.toString();
  }

}
