package io.ebean.datasource;

import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 *
 * Builder for DataSourcePool.
 *
 * <pre>{@code
 *
 *    DataSourcePool pool = DataSourcePool.builder()
 *      .setName("test")
 *      .setUrl("jdbc:h2:mem:tests")
 *      .setUsername("sa")
 *      .setPassword("")
 *      .build();
 *
 *   Connection connection = pool.getConnection();
 *
 * }</pre>
 */
public interface DataSourceBuilder {

  /**
   * Return a new builder of DataSourcePool.
   */
  static DataSourceBuilder create() {
    return new DataSourceConfig();
  }

  /**
   * Return a new builder loading from the given properties.
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
   * @param prefix The key prefix when reading the properties
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
   * Return a copy of the DataSourceBuilder.
   */
  DataSourceBuilder copy();

  /**
   * Default the values for driver, url, username and password from another builder if
   * they have not been set.
   */
  DataSourceBuilder setDefaults(DataSourceBuilder other);

  /**
   * Set the data source pool name.
   */
  DataSourceBuilder setName(String name);

  /**
   * Set the ClientInfo ApplicationName property.
   * <p>
   * Refer to {@link java.sql.Connection#setClientInfo(String, String)}.
   *
   * @param applicationName The ApplicationName property to set as clientInfo.
   */
  DataSourceBuilder setApplicationName(String applicationName);

  /**
   * Set the ClientInfo as properties.
   * <p>
   * Refer to {@link java.sql.Connection#setClientInfo(Properties)}
   * <p>
   * Note that for Postgres currently only the ApplicationName property is used.
   *
   * @param clientInfo The client info properties to set on connections in the DataSource.
   */
  DataSourceBuilder setClientInfo(Properties clientInfo);

  /**
   * Set the connection URL to use for a matching read-only connection pool.
   */
  DataSourceBuilder setReadOnlyUrl(String readOnlyUrl);

  /**
   * Set the connection URL.
   */
  DataSourceBuilder setUrl(String url);

  /**
   * Set the database username.
   */
  DataSourceBuilder setUsername(String username);

  /**
   * Set the database password.
   */
  DataSourceBuilder setPassword(String password);

  /**
   * Set the database alternate password2.
   */
  DataSourceBuilder setPassword2(String password2);

  /**
   * Set the default database schema to use.
   */
  DataSourceBuilder setSchema(String schema);

  /**
   * Set the database driver.
   */
  DataSourceBuilder setDriver(String driver);

  /**
   * Set the transaction isolation level.
   */
  DataSourceBuilder setIsolationLevel(int isolationLevel);

  /**
   * Set to true to turn on autoCommit.
   */
  DataSourceBuilder setAutoCommit(boolean autoCommit);

  /**
   * Set to true to for read only.
   */
  DataSourceBuilder setReadOnly(boolean readOnly);

  /**
   * Set the minimum number of connections the pool should maintain.
   */
  DataSourceBuilder setMinConnections(int minConnections);

  /**
   * Set the maximum number of connections the pool can reach.
   */
  DataSourceBuilder setMaxConnections(int maxConnections);

  /**
   * Set the alert implementation to use.
   */
  DataSourceBuilder setAlert(DataSourceAlert alert);

  /**
   * Set the listener to use.
   */
  DataSourceBuilder setListener(DataSourcePoolListener listener);

  /**
   * Set a SQL statement used to test the database is accessible.
   * <p>
   * Note that if this is not set then it can get defaulted from the DatabasePlatform.
   */
  DataSourceBuilder setHeartbeatSql(String heartbeatSql);

  /**
   * Set the expected heartbeat frequency in seconds.
   */
  DataSourceBuilder setHeartbeatFreqSecs(int heartbeatFreqSecs);

  /**
   * Set the heart beat timeout in seconds.
   */
  DataSourceBuilder setHeartbeatTimeoutSeconds(int heartbeatTimeoutSeconds);

  /**
   * Set to true if a stack trace should be captured when obtaining a connection from the pool.
   * <p>
   * This can be used to diagnose a suspected connection pool leak.
   * <p>
   * Obviously this has a performance overhead.
   */
  DataSourceBuilder setCaptureStackTrace(boolean captureStackTrace);

  /**
   * Set the max size for reporting stack traces on busy connections.
   */
  DataSourceBuilder setMaxStackTraceSize(int maxStackTraceSize);

  /**
   * Set the time in minutes after which a connection could be considered to have leaked.
   */
  DataSourceBuilder setLeakTimeMinutes(int leakTimeMinutes);

  /**
   * Set the size of the PreparedStatement cache (per connection).
   */
  DataSourceBuilder setPstmtCacheSize(int pstmtCacheSize);

  /**
   * Set the size of the CallableStatement cache (per connection).
   */
  DataSourceBuilder setCstmtCacheSize(int cstmtCacheSize);

  /**
   * Set the time in millis to wait for a connection before timing out once the
   * pool has reached its maximum size.
   */
  DataSourceBuilder setWaitTimeoutMillis(int waitTimeoutMillis);

  /**
   * Set the maximum age a connection can be in minutes.
   */
  DataSourceBuilder setMaxAgeMinutes(int maxAgeMinutes);

  /**
   * Set the time in seconds a connection can be idle after which it can be
   * trimmed from the pool.
   * <p>
   * This is so that the pool after a busy period can trend over time back
   * towards the minimum connections.
   */
  DataSourceBuilder setMaxInactiveTimeSecs(int maxInactiveTimeSecs);

  /**
   * Set the minimum trim gap between pool trim checks.
   */
  DataSourceBuilder setTrimPoolFreqSecs(int trimPoolFreqSecs);

  /**
   * Set a pool listener.
   */
  DataSourceBuilder setPoolListener(String poolListener);

  /**
   * Set to false, if DataSource should not fail on start. (e.g. DataSource is not available)
   */
  DataSourceBuilder setFailOnStart(boolean failOnStart);

  /**
   * Set to true if the DataSource should be started offline (without any connections).
   */
  DataSourceBuilder setOffline(boolean offline);

  /**
   * Set custom init queries for each query.
   */
  DataSourceBuilder setInitSql(List<String> initSql);

  /**
   * Set custom properties for the jdbc driver connection.
   */
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
  DataSourceBuilder setOwnerUsername(String ownerUsername);

  /**
   * Set the database owner password (used to create connection for use with InitDatabase).
   */
  DataSourceBuilder setOwnerPassword(String ownerPassword);

  /**
   * Set the database platform (for use with ownerUsername and InitDatabase.
   */
  DataSourceBuilder setPlatform(String platform);

  /**
   * Set the InitDatabase to use with ownerUsername.
   */
  DataSourceBuilder setInitDatabase(InitDatabase initDatabase);

  /**
   * Set InitDatabase based on the database platform.
   */
  DataSourceBuilder setInitDatabaseForPlatform(String platform);

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
   * Return the builder with access to the settings.
   */
  Settings settings();

  /**
   * The settings of the DataSourceBuilder.
   */
  interface Settings extends DataSourceBuilder {

    /**
     * Return true if there are no values set for any of url, driver, username and password.
     */
    boolean isEmpty();

    /**
     * Return the clientInfo ApplicationName property.
     */
    String getApplicationName();

    /**
     * Return the clientInfo ApplicationName property.
     */
    Properties getClientInfo();

    /**
     * Return the read-only URL to use for creating a matching read only DataSource..
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
     * Return the database driver.
     */
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
     * Return the minimum number of connections the pool should maintain.
     */
    int getMinConnections();

    /**
     * Return the maximum number of connections the pool can reach.
     */
    int getMaxConnections();

    /**
     * Return the alert implementation to use.
     */
    DataSourceAlert getAlert();

    /**
     * Return the listener to use.
     */
    DataSourcePoolListener getListener();

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
