package io.ebean.datasource;

import javax.sql.DataSource;
import java.sql.Driver;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Builder for DataSourcePool.
 * <p>
 * Use {@link #settings()} for getter access to read the configuration
 * set on the builder via {@link Settings}. That is, {@code DataSourceBuilder} has
 * the setters only and {@code DataSourceBuilder.Settings} has both the getters and
 * setters for the builder.
 *
 * <pre>{@code
 *
 *    DataSourcePool pool = DataSourcePool.builder()
 *      .name("test")
 *      .url("jdbc:h2:mem:tests")
 *      .username("sa")
 *      .password("")
 *      .build();
 *
 *   try (Connection connection = pool.getConnection()) {
 *     // do something with the connection ...
 *   }
 *
 * }</pre>
 */
public interface DataSourceBuilder {

  /**
   * Return a new builder of DataSourcePool.
   */
  @SuppressWarnings("all")
  static DataSourceBuilder create() {
    return new DataSourceConfig();
  }

  /**
   * Return a new builder loading from the given properties.
   *
   * @param properties Configuration of the DataSourceBuilder via properties.
   */
  static DataSourceBuilder from(Properties properties) {
    return create().load(properties);
  }

  /**
   * Return a new builder loading from the given properties using a given prefix.
   * <p>
   * For example, using a prefix of "myDataSource" then the username property key would be
   * "myDataSource.username".
   *
   * @param properties Configuration of the DataSourceBuilder via properties.
   * @param prefix     The key prefix when reading the properties
   */
  static DataSourceBuilder from(Properties properties, String prefix) {
    return create().load(properties, prefix);
  }

  /**
   * Build and return the DataSourcePool.
   * <pre>{@code
   *
   *   DataSourcePool pool = DataSourcePool.builder()
   *     .setName("test")
   *     .setUrl("jdbc:h2:mem:tests")
   *     .setUsername("sa")
   *     .setPassword("")
   *     .build();
   *
   * }</pre>
   */
  DataSourcePool build();

  /**
   * Return the builder with access to the settings. Provides getters/accessors
   * to read the configured properties of this DataSourceBuilder.
   */
  Settings settings();

  /**
   * Apply configuration to the builder via a lambda.
   */
  DataSourceBuilder apply(Consumer<DataSourceBuilder.Settings> apply);

  /**
   * Conditionally apply configuration to the builder via a lambda.
   *
   * @param predicate The condition to apply configuration when true.
   * @param apply     The configuration apply function.
   */
  default DataSourceBuilder alsoIf(BooleanSupplier predicate, Consumer<DataSourceBuilder.Settings> apply) {
    if (predicate.getAsBoolean()) {
      apply(apply);
    }
    return this;
  }

  /**
   * Return a copy of the DataSourceBuilder.
   */
  DataSourceBuilder copy();

  /**
   * Default the values for driver, url, username, password and minConnections
   * from another builder if these properties not already been set.
   */
  DataSourceBuilder setDefaults(DataSourceBuilder other);

  /**
   * @deprecated - migrate to {@link #name(String)}
   */
  @Deprecated(forRemoval = true)
  DataSourceBuilder setName(String name);

  /**
   * Set the data source pool name.
   */
  default DataSourceBuilder name(String name) {
    return setName(name);
  }

  /**
   * Set a DataSource that will be used to provide new connections.
   * <p>
   * When provided, then it needs to implement {@link DataSource#getConnection()} and
   * {@link DataSource#getConnection(String, String)} returning new connections that
   * will be pooled.
   */
  DataSourceBuilder dataSource(DataSource dataSource);

  /**
   * @deprecated - migrate to {@link #applicationName(String)}
   */
  @Deprecated(forRemoval = true)
  DataSourceBuilder setApplicationName(String applicationName);

  /**
   * Set the ClientInfo ApplicationName property.
   * <p>
   * Refer to {@link java.sql.Connection#setClientInfo(String, String)}.
   *
   * @param applicationName The ApplicationName property to set as clientInfo.
   */
  default DataSourceBuilder applicationName(String applicationName) {
    return setApplicationName(applicationName);
  }

  /**
   * @deprecated - migrate to {@link #clientInfo(Properties)}.
   */
  @Deprecated(forRemoval = true)
  DataSourceBuilder setClientInfo(Properties clientInfo);

  /**
   * Set the ClientInfo as properties.
   * <p>
   * Refer to {@link java.sql.Connection#setClientInfo(Properties)}
   * <p>
   * Note that for Postgres currently only the ApplicationName property is used.
   *
   * @param clientInfo The client info properties to set on connections in the DataSource.
   */
  default DataSourceBuilder clientInfo(Properties clientInfo) {
    return setClientInfo(clientInfo);
  }

  /**
   * @deprecated - migrate to {@link #readOnlyUrl(String)}.
   */
  @Deprecated(forRemoval = true)
  DataSourceBuilder setReadOnlyUrl(String readOnlyUrl);

  /**
   * Set the connection URL to use for a matching read-only connection pool.
   */
  default DataSourceBuilder readOnlyUrl(String readOnlyUrl) {
    return setReadOnlyUrl(readOnlyUrl);
  }

  /**
   * @deprecated - migrate to {@link #url(String)}.
   */
  @Deprecated(forRemoval = true)
  DataSourceBuilder setUrl(String url);

  /**
   * Set the connection URL.
   */
  default DataSourceBuilder url(String url) {
    return setUrl(url);
  }

  /**
   * @deprecated - migrate to {@link #username(String)}.
   */
  @Deprecated(forRemoval = true)
  DataSourceBuilder setUsername(String username);

  /**
   * Set the database username.
   */
  default DataSourceBuilder username(String username) {
    return setUsername(username);
  }

  /**
   * @deprecated - migrate to {@link #password(String)}.
   */
  @Deprecated
  DataSourceBuilder setPassword(String password);

  /**
   * Set the database password.
   */
  default DataSourceBuilder password(String password) {
    return setPassword(password);
  }

  /**
   * @deprecated - migrate to {@link #password2(String)}.
   */
  @Deprecated
  DataSourceBuilder setPassword2(String password2);

  /**
   * Set the database alternate password2.
   */
  default DataSourceBuilder password2(String password2) {
    return setPassword2(password2);
  }

  /**
   * @deprecated - migrate to {@link #schema(String)}.
   */
  @Deprecated(forRemoval = true)
  DataSourceBuilder setSchema(String schema);

  /**
   * Set the default database schema to use.
   */
  default DataSourceBuilder schema(String schema) {
    return setSchema(schema);
  }

  /**
   * Set the default database catalog to use.
   */
  DataSourceBuilder catalog(String catalog);

  /**
   * @deprecated - migrate to {@link #driver(String)}.
   */
  @Deprecated(forRemoval = true)
  DataSourceBuilder setDriver(String driverClassName);

  /**
   * Set the database driver className.
   */
  default DataSourceBuilder driver(String driverClassName) {
    return setDriver(driverClassName);
  }

  /**
   * Set the driver class to use.
   */
  DataSourceBuilder driver(Class<? extends Driver> driver);

  /**
   * Set the driver to use.
   */
  DataSourceBuilder driver(Driver driver);

  /**
   * Set the transaction isolation level.
   */
  default DataSourceBuilder isolationLevel(int isolationLevel) {
    return setIsolationLevel(isolationLevel);
  }

  /**
   * @deprecated - migrate to {@link #isolationLevel(int)}.
   */
  @Deprecated(forRemoval = true)
  DataSourceBuilder setIsolationLevel(int isolationLevel);

  /**
   * Set to true to turn on autoCommit.
   */
  default DataSourceBuilder autoCommit(boolean autoCommit) {
    return setAutoCommit(autoCommit);
  }

  /**
   * @deprecated - migrate to {@link #autoCommit(boolean)}.
   */
  @Deprecated(forRemoval = true)
  DataSourceBuilder setAutoCommit(boolean autoCommit);

  /**
   * Set to true to for read only.
   */
  default DataSourceBuilder readOnly(boolean readOnly) {
    return setReadOnly(readOnly);
  }

  /**
   * @deprecated - migrate to {@link #readOnly(boolean)}.
   */
  @Deprecated(forRemoval = true)
  DataSourceBuilder setReadOnly(boolean readOnly);

  /**
   * Set the minimum number of connections the pool should maintain. Defaults to 2 when not set.
   */
  default DataSourceBuilder minConnections(int minConnections) {
    return setMinConnections(minConnections);
  }

  /**
   * @deprecated - migrate to {@link #minConnections(int)}.
   */
  @Deprecated(forRemoval = true)
  DataSourceBuilder setMinConnections(int minConnections);

  /**
   * Set the number of initial connections to create when starting.
   * <p>
   * When not set the initial number of connections will be min connections.
   * <p>
   * The benefit of setting an initial number of connections is for smoother
   * deployment into an active production system where an application will get
   * assigned production load.
   */
  DataSourceBuilder initialConnections(int initialConnections);

  /**
   * Set the maximum number of connections the pool can reach. Defaults to 200 when not set.
   */
  default DataSourceBuilder maxConnections(int maxConnections) {
    return setMaxConnections(maxConnections);
  }

  /**
   * @deprecated - migrate to {@link #maxConnections(int)}.
   */
  @Deprecated(forRemoval = true)
  DataSourceBuilder setMaxConnections(int maxConnections);

  /**
   * Set the alert implementation to use.
   */
  default DataSourceBuilder alert(DataSourceAlert alert) {
    return setAlert(alert);
  }

  /**
   * @deprecated - migrate to {@link #alert(DataSourceAlert)}.
   */
  @Deprecated(forRemoval = true)
  DataSourceBuilder setAlert(DataSourceAlert alert);

  /**
   * Set the listener to use.
   */
  default DataSourceBuilder listener(DataSourcePoolListener listener) {
    return setListener(listener);
  }

  /**
   * @deprecated - migrate to {@link #listener(DataSourcePoolListener)}.
   */
  @Deprecated(forRemoval = true)
  DataSourceBuilder setListener(DataSourcePoolListener listener);

  /**
   * Set the connection listener to use.
   */
  DataSourceBuilder connectionListener(DataSourcePoolNewConnectionListener connectionListener);

  /**
   * Set a SQL statement used to test the database is accessible.
   * <p>
   * Note that if this is not set then it can get defaulted from the DatabasePlatform.
   */
  default DataSourceBuilder heartbeatSql(String heartbeatSql) {
    return setHeartbeatSql(heartbeatSql);
  }

  /**
   * @deprecated - migrate to {@link #heartbeatSql(String)}.
   */
  @Deprecated(forRemoval = true)
  DataSourceBuilder setHeartbeatSql(String heartbeatSql);

  /**
   * Set the expected heartbeat frequency in seconds.
   */
  default DataSourceBuilder heartbeatFreqSecs(int heartbeatFreqSecs) {
    return setHeartbeatFreqSecs(heartbeatFreqSecs);
  }

  /**
   * @deprecated - migrate to {@link #heartbeatFreqSecs(int)}.
   */
  @Deprecated(forRemoval = true)
  DataSourceBuilder setHeartbeatFreqSecs(int heartbeatFreqSecs);

  /**
   * Set the heart beat timeout in seconds.
   */
  default DataSourceBuilder heartbeatTimeoutSeconds(int heartbeatTimeoutSeconds) {
    return setHeartbeatTimeoutSeconds(heartbeatTimeoutSeconds);
  }

  /**
   * @deprecated - migrate to {@link #heartbeatTimeoutSeconds(int)}.
   */
  @Deprecated(forRemoval = true)
  DataSourceBuilder setHeartbeatTimeoutSeconds(int heartbeatTimeoutSeconds);

  /**
   * Sets the coun how often the heartbeat has to detect pool exhaustion in succession.
   * in succession before an error is raised and the pool will be reset.
   * <p>
   * By default, this value must be multiplied with the sum of heartbeatfreq + waitTimeoutMillis to
   * estimate the time, when the pool will be restarted, because all connections were leaked.
   */
  DataSourceBuilder heartbeatMaxPoolExhaustedCount(int count);

  /**
   * Set to true if a stack trace should be captured when obtaining a connection from the pool.
   * <p>
   * This can be used to diagnose a suspected connection pool leak.
   * <p>
   * Obviously this has a performance overhead.
   */
  default DataSourceBuilder captureStackTrace(boolean captureStackTrace) {
    return setCaptureStackTrace(captureStackTrace);
  }

  /**
   * @deprecated - migrate to {@link #captureStackTrace(boolean)}.
   */
  @Deprecated(forRemoval = true)
  DataSourceBuilder setCaptureStackTrace(boolean captureStackTrace);

  /**
   * Set the max size for reporting stack traces on busy connections.
   */
  default DataSourceBuilder maxStackTraceSize(int maxStackTraceSize) {
    return setMaxStackTraceSize(maxStackTraceSize);
  }

  /**
   * @deprecated - migrate to {@link #maxStackTraceSize(int)}.
   */
  @Deprecated(forRemoval = true)
  DataSourceBuilder setMaxStackTraceSize(int maxStackTraceSize);

  /**
   * Set the time in minutes after which a connection could be considered to have leaked.
   */
  default DataSourceBuilder leakTimeMinutes(int leakTimeMinutes) {
    return setLeakTimeMinutes(leakTimeMinutes);
  }

  /**
   * @deprecated - migrate to {@link #leakTimeMinutes(int)}.
   */
  @Deprecated(forRemoval = true)
  DataSourceBuilder setLeakTimeMinutes(int leakTimeMinutes);

  /**
   * Set the size of the PreparedStatement cache (per connection).
   * <p>
   * Defaults to 100.
   */
  default DataSourceBuilder pstmtCacheSize(int pstmtCacheSize) {
    return setPstmtCacheSize(pstmtCacheSize);
  }

  /**
   * @deprecated - migrate to {@link #pstmtCacheSize(int)}.
   */
  @Deprecated
  DataSourceBuilder setPstmtCacheSize(int pstmtCacheSize);

  /**
   * Set the size of the CallableStatement cache (per connection).
   */
  default DataSourceBuilder cstmtCacheSize(int cstmtCacheSize) {
    return setCstmtCacheSize(cstmtCacheSize);
  }

  /**
   * @deprecated - migrate to {@link #cstmtCacheSize(int)}.
   */
  @Deprecated
  DataSourceBuilder setCstmtCacheSize(int cstmtCacheSize);

  /**
   * Set the time in millis to wait for a connection before timing out once the
   * pool has reached its maximum size.
   * <p>
   * Defaults to 1000 millis (1 second).
   */
  default DataSourceBuilder waitTimeoutMillis(int waitTimeoutMillis) {
    return setWaitTimeoutMillis(waitTimeoutMillis);
  }

  /**
   * @deprecated - migrate to {@link #waitTimeoutMillis(int)}.
   */
  @Deprecated
  DataSourceBuilder setWaitTimeoutMillis(int waitTimeoutMillis);

  /**
   * Set the maximum age a connection can be in minutes.
   * <p>
   * Defaults to unlimited age, no connections are trimmed based on age.
   */
  default DataSourceBuilder maxAgeMinutes(int maxAgeMinutes) {
    return setMaxAgeMinutes(maxAgeMinutes);
  }

  /**
   * @deprecated - migrate to {@link #maxAgeMinutes(int)}.
   */
  @Deprecated
  DataSourceBuilder setMaxAgeMinutes(int maxAgeMinutes);

  /**
   * Set the time in seconds a connection can be idle after which it can be
   * trimmed from the pool.
   * <p>
   * This is so that the pool after a busy period can trend over time back
   * towards the minimum connections.
   */
  default DataSourceBuilder maxInactiveTimeSecs(int maxInactiveTimeSecs) {
    return setMaxInactiveTimeSecs(maxInactiveTimeSecs);
  }

  /**
   * @deprecated - migrate to {@link #maxInactiveTimeSecs(int)}.
   */
  @Deprecated
  DataSourceBuilder setMaxInactiveTimeSecs(int maxInactiveTimeSecs);

  /**
   * Set the minimum trim gap between pool trim checks.
   */
  default DataSourceBuilder trimPoolFreqSecs(int trimPoolFreqSecs) {
    return setTrimPoolFreqSecs(trimPoolFreqSecs);
  }

  /**
   * @deprecated - migrate to {@link #trimPoolFreqSecs(int)}.
   */
  @Deprecated
  DataSourceBuilder setTrimPoolFreqSecs(int trimPoolFreqSecs);

  /**
   * Set a pool listener.
   */
  default DataSourceBuilder poolListener(String poolListener) {
    return setPoolListener(poolListener);
  }

  /**
   * @deprecated - migrate to {@link #poolListener(String)}.
   */
  @Deprecated
  DataSourceBuilder setPoolListener(String poolListener);

  /**
   * Set to false, if DataSource should not fail on start. (e.g. DataSource is not available)
   */
  default DataSourceBuilder failOnStart(boolean failOnStart) {
    return setFailOnStart(failOnStart);
  }

  /**
   * @deprecated - migrate to {@link #failOnStart(boolean)}.
   */
  @Deprecated
  DataSourceBuilder setFailOnStart(boolean failOnStart);

  /**
   * Set to true if the DataSource should be started offline (without any connections).
   */
  default DataSourceBuilder offline(boolean offline) {
    return setOffline(offline);
  }

  /**
   * @deprecated - migrate to {@link #offline(boolean)}.
   */
  @Deprecated
  DataSourceBuilder setOffline(boolean offline);

  /**
   * Set custom init queries for each query.
   */
  default DataSourceBuilder initSql(List<String> initSql) {
    return setInitSql(initSql);
  }

  /**
   * @deprecated - migrate to {@link #initSql(List)}.
   */
  @Deprecated
  DataSourceBuilder setInitSql(List<String> initSql);

  /**
   * Set custom properties for the jdbc driver connection.
   */
  default DataSourceBuilder customProperties(Map<String, String> customProperties) {
    return setCustomProperties(customProperties);
  }

  /**
   * @deprecated - migrate to {@link #customProperties(Map)}.
   */
  @Deprecated
  DataSourceBuilder setCustomProperties(Map<String, String> customProperties);

  /**
   * Add a driver property.
   * <pre>{@code
   *
   *   config.addProperty("useSSL", false);
   *
   * }</pre>
   */
  DataSourceBuilder addProperty(String key, String value);

  /**
   * Add a driver property.
   * <pre>{@code
   *
   *   config.addProperty("useSSL", false);
   *
   * }</pre>
   */
  DataSourceBuilder addProperty(String key, boolean value);

  /**
   * Add a driver property.
   * <pre>{@code
   *
   *   config.addProperty("useSSL", false);
   *
   * }</pre>
   */
  DataSourceBuilder addProperty(String key, int value);

  /**
   * Set the database owner username (used to create connection for use with InitDatabase).
   */
  default DataSourceBuilder ownerUsername(String ownerUsername) {
    return setOwnerUsername(ownerUsername);
  }

  /**
   * @deprecated - migrate to {@link #ownerUsername(String)}.
   */
  @Deprecated
  DataSourceBuilder setOwnerUsername(String ownerUsername);

  /**
   * Set the database owner password (used to create connection for use with InitDatabase).
   */
  default DataSourceBuilder ownerPassword(String ownerPassword) {
    return setOwnerPassword(ownerPassword);
  }

  /**
   * @deprecated - migrate to {@link #ownerPassword(String)}.
   */
  @Deprecated
  DataSourceBuilder setOwnerPassword(String ownerPassword);

  /**
   * Set the database platform (for use with ownerUsername and InitDatabase.
   */
  default DataSourceBuilder platform(String platform) {
    return setPlatform(platform);
  }

  /**
   * @deprecated - migrate to {@link #platform(String)}.
   */
  @Deprecated
  DataSourceBuilder setPlatform(String platform);

  /**
   * Set the InitDatabase to use with ownerUsername.
   */
  default DataSourceBuilder initDatabase(InitDatabase initDatabase) {
    return setInitDatabase(initDatabase);
  }

  /**
   * @deprecated - migrate to {@link #initDatabase(InitDatabase)}.
   */
  @Deprecated
  DataSourceBuilder setInitDatabase(InitDatabase initDatabase);

  /**
   * Set InitDatabase based on the database platform.
   */
  default DataSourceBuilder initDatabaseForPlatform(String platform) {
    return setInitDatabaseForPlatform(platform);
  }

  /**
   * @deprecated - migrate to {@link #initDatabaseForPlatform(String)}.
   */
  @Deprecated
  DataSourceBuilder setInitDatabaseForPlatform(String platform);

  /**
   * When set true a JVM shutdown hook is registered that will shutdown the
   * connection pool on JVM exit if it has not already been shutdown.
   */
  DataSourceBuilder shutdownOnJvmExit(boolean shutdownOnJvmExit);

  /**
   * Set whether the connection pool should be validated periodically.
   * <p>
   * This is enabled by default. Generally we only want to turn this
   * off when using the pool with a Lambda function.
   *
   * @param validateOnHeartbeat Use false to disable heartbeat validation.
   */
  DataSourceBuilder validateOnHeartbeat(boolean validateOnHeartbeat);

  /**
   * Load the settings from the properties with no prefix on the property names.
   *
   * @param properties the properties to configure the dataSource
   */
  DataSourceBuilder load(Properties properties);

  /**
   * Load the settings from the properties with the given prefix on the property names.
   * <p>
   * For example, using a prefix of "my-db" then the username property key would be
   * "my-db.username".
   *
   * @param properties the properties to configure the dataSource
   * @param prefix     the prefix of the property names.
   */
  DataSourceBuilder load(Properties properties, String prefix);

  /**
   * Load the settings from the properties with "datasource" prefix on the property names.
   * <p>
   * For example, if the poolName is "hr" then the username property key would be:
   * "datasource.hr.username".
   *
   * @param properties the properties to configure the dataSource
   * @param poolName   the name of the specific dataSource pool (optional)
   */
  DataSourceBuilder loadSettings(Properties properties, String poolName);


  /**
   * When enabled, the datasource enforces a clean close. This means, if you close a possible dirty
   * connection, that was not committed or rolled back, an exception is thrown.
   * <p>
   * When disabled, the situation is logged as warning.
   * <p>
   * This option has no effect on readonly or autocommit connections.
   * <p>
   * Note: It is recommended to enable this option in tests/test systems to find possible
   * programming errors. See https://github.com/ebean-orm/ebean-datasource/issues/116 for details.
   */
  DataSourceBuilder enforceCleanClose(boolean enforceCleanClose);

  /**
   * When <code>true</code>, an exception is thrown when a dirty connection is closed.
   * <p>
   * See {@link #enforceCleanClose(boolean)}.
   */
  boolean enforceCleanClose();

  /**
   * The settings of the DataSourceBuilder. Provides getters/accessors
   * to read the configured properties of this DataSourceBuilder.
   */
  interface Settings extends DataSourceBuilder {

    /**
     * Return true if there are no values set for any of url, username, password or driver.
     */
    boolean isEmpty();

    /**
     * Return a DataSource that will be used to provide new connections or null.
     */
    DataSource dataSource();

    /**
     * Shut down pool on JVM exit.
     */
    boolean isShutdownOnJvmExit();

    /**
     * When true validate the pool when the heartbeat runs.
     */
    boolean isValidateOnHeartbeat();

    /**
     * Return the connection properties including credentials and custom parameters.
     */
    Properties connectionProperties();

    /**
     * Return the clientInfo ApplicationName property.
     */
    String getApplicationName();

    /**
     * Return the clientInfo ApplicationName property.
     */
    Properties getClientInfo();

    /**
     * Return the read-only URL to use for creating a matching read only DataSource.
     */
    String getReadOnlyUrl();

    /**
     * Return the maximum age a connection is allowed to be before it is closed.
     * <p>
     * This can be used to close old connections.
     */
    int getMaxAgeMinutes();

    /**
     * Return the connection URL.
     */
    String getUrl();

    /**
     * Return the database username.
     */
    String getUsername();

    /**
     * Return the database password.
     */
    String getPassword();

    /**
     * Return the database alternate password2.
     */
    String getPassword2();

    /**
     * Return the database username.
     */
    String getSchema();

    /**
     * Return the database catalog.
     */
    String catalog();

    /**
     * Return the driver instance to use.
     */
    Driver driver();

    /**
     * Return the driver class to use (if an instance is not provided).
     */
    Class<? extends Driver> driverClass();

    /**
     * Return the database driver className to use (if an driver instance or class is not provided).
     */
    default String driverClassName() {
      return getDriver();
    }

    /**
     * @deprecated migrate to {@link #driverClassName()}.
     */
    @Deprecated(forRemoval = true)
    String getDriver();

    /**
     * Return the transaction isolation level.
     */
    int getIsolationLevel();

    /**
     * Return autoCommit setting.
     */
    boolean isAutoCommit();

    /**
     * Return the read only setting.
     */
    boolean isReadOnly();

    /**
     * Return the minimum number of connections the pool should maintain. Defaults to 2.
     */
    int getMinConnections();

    /**
     * Return the maximum number of connections the pool can reach. Defaults to 200.
     */
    int getMaxConnections();

    /**
     * Return the number of initial connections to create on startup.
     */
    int getInitialConnections();

    /**
     * Return the alert implementation to use.
     */
    DataSourceAlert getAlert();

    /**
     * Return the listener to use.
     */
    DataSourcePoolListener getListener();

    /**
     * Return the new connection listener to use.
     */
    DataSourcePoolNewConnectionListener getConnectionListener();

    /**
     * Return a SQL statement used to test the database is accessible.
     * <p>
     * Note that if this is not set then it can get defaulted from the DatabasePlatform.
     */
    String getHeartbeatSql();

    /**
     * Return the heartbeat frequency in seconds.
     * <p>
     * This is the expected frequency in which the DataSource should be checked to
     * make sure it is healthy and trim idle connections.
     */
    int getHeartbeatFreqSecs();

    /**
     * Return the heart beat timeout in seconds.
     */
    int getHeartbeatTimeoutSeconds();

    /**
     * Return the number, how often the heartbeat has to detect pool exhaustion in succession.
     */
    int getHeartbeatMaxPoolExhaustedCount();

    /**
     * Return true if a stack trace should be captured when obtaining a connection from the pool.
     * <p>
     * This can be used to diagnose a suspected connection pool leak.
     * <p>
     * Obviously this has a performance overhead.
     */
    boolean isCaptureStackTrace();

    /**
     * Return the max size for reporting stack traces on busy connections.
     */
    int getMaxStackTraceSize();

    /**
     * Return the time in minutes after which a connection could be considered to have leaked.
     */
    int getLeakTimeMinutes();

    /**
     * Return the size of the PreparedStatement cache (per connection).
     */
    int getPstmtCacheSize();

    /**
     * Return the size of the CallableStatement cache (per connection).
     */
    int getCstmtCacheSize();

    /**
     * Return the time in millis to wait for a connection before timing out once
     * the pool has reached its maximum size.
     */
    int getWaitTimeoutMillis();

    /**
     * Return the time in seconds a connection can be idle after which it can be
     * trimmed from the pool.
     * <p>
     * This is so that the pool after a busy period can trend over time back
     * towards the minimum connections.
     */
    int getMaxInactiveTimeSecs();

    /**
     * Return true (default) if the DataSource should be fail on start.
     * <p>
     * If this is disabled, it allows to create a connection pool, even if the
     * datasource is not available. (e.g. parallel start up of docker containers).
     * It enables to initialize the Ebean-Server if the db-server is not yet up. In
     * this case, a ({@link DataSourceAlert#dataSourceUp(javax.sql.DataSource)} is
     * fired when DS gets up either immediately at start-up or later.)
     */
    boolean isFailOnStart();

    /**
     * Return the minimum time gap between pool trim checks.
     * <p>
     * This defaults to 59 seconds meaning that the pool trim check will run every
     * minute assuming the heart beat check runs every 30 seconds.
     */
    int getTrimPoolFreqSecs();

    /**
     * Return the pool listener.
     */
    String getPoolListener();

    /**
     * Return true if the DataSource should be left offline.
     * <p>
     * This is to support DDL generation etc without having a real database.
     */
    boolean isOffline();

    /**
     * Return a map of custom properties for the jdbc driver connection.
     */
    Map<String, String> getCustomProperties();

    /**
     * Return a list of init queries, that are executed after a connection is opened.
     */
    List<String> getInitSql();

    /**
     * Return the database owner username.
     */
    String getOwnerUsername();

    /**
     * Return the database owner password.
     */
    String getOwnerPassword();

    /**
     * Return the database platform.
     */
    String getPlatform();

    /**
     * Return the InitDatabase to use with ownerUsername.
     */
    InitDatabase getInitDatabase();

    /**
     * Return true if InitDatabase should be used (when the pool initialises and a connection can't be obtained).
     *
     * @return True to obtain a connection using ownerUsername and run InitDatabase.
     */
    boolean useInitDatabase();
  }
}
