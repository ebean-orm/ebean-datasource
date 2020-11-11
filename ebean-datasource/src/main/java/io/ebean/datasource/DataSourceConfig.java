package io.ebean.datasource;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Configuration information for a DataSource.
 */
public class DataSourceConfig {

  private static final String POSTGRES = "postgres";

  private InitDatabase initDatabase;

  private String readOnlyUrl;

  private String url;

  private String username;

  private String password;

  private String schema;

  /**
   * The name of the database platform (for use with ownerUsername and InitDatabase).
   */
  private String platform;

  /**
   * The optional database owner username (for running InitDatabase).
   */
  private String ownerUsername;

  /**
   * The optional database owner password (for running InitDatabase).
   */
  private String ownerPassword;

  private String driver;

  private int minConnections = 2;

  private int maxConnections = 200;

  private int isolationLevel = Connection.TRANSACTION_READ_COMMITTED;

  private boolean autoCommit;

  private boolean readOnly;

  private String heartbeatSql;

  private int heartbeatFreqSecs = 30;

  private int heartbeatTimeoutSeconds = 3;

  private boolean captureStackTrace;

  private int maxStackTraceSize = 5;

  private int leakTimeMinutes = 30;

  private int maxInactiveTimeSecs = 300;

  private int maxAgeMinutes = 0;

  private int trimPoolFreqSecs = 59;

  private int pstmtCacheSize = 50;

  private int cstmtCacheSize = 20;

  private int waitTimeoutMillis = 1000;

  private String poolListener;

  private boolean offline;

  private boolean failOnStart = true;

  private Map<String, String> customProperties;

  private List<String> initSql;


  private DataSourceAlert alert;

  private DataSourcePoolListener listener;

  /**
   * Return a copy of the DataSourceConfig.
   */
  public DataSourceConfig copy() {

    DataSourceConfig copy = new DataSourceConfig();
    copy.initDatabase = initDatabase;
    copy.url = url;
    copy.readOnlyUrl = readOnlyUrl;
    copy.username = username;
    copy.password = password;
    copy.schema = schema;
    copy.platform = platform;
    copy.ownerUsername = ownerUsername;
    copy.ownerPassword = ownerPassword;
    copy.driver = driver;
    copy.minConnections = minConnections;
    copy.maxConnections = maxConnections;
    copy.isolationLevel = isolationLevel;
    copy.autoCommit = autoCommit;
    copy.readOnly = readOnly;
    copy.heartbeatSql = heartbeatSql;
    copy.heartbeatFreqSecs = heartbeatFreqSecs;
    copy.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
    copy.captureStackTrace = captureStackTrace;
    copy.maxStackTraceSize = maxStackTraceSize;
    copy.leakTimeMinutes = leakTimeMinutes;
    copy.maxInactiveTimeSecs = maxInactiveTimeSecs;
    copy.maxAgeMinutes = maxAgeMinutes;
    copy.trimPoolFreqSecs = trimPoolFreqSecs;
    copy.pstmtCacheSize = pstmtCacheSize;
    copy.cstmtCacheSize = cstmtCacheSize;
    copy.waitTimeoutMillis = waitTimeoutMillis;
    copy.poolListener = poolListener;
    copy.offline = offline;
    copy.failOnStart = failOnStart;
    if (customProperties != null) {
      copy.customProperties = new LinkedHashMap<>(customProperties);
    }
    copy.initSql = initSql;
    copy.alert = alert;
    copy.listener = listener;

    return copy;
  }

  /**
   * Default the values for driver, url, username and password from another config if
   * they have not been set.
   */
  public DataSourceConfig setDefaults(DataSourceConfig other) {
    if (driver == null) {
      driver = other.driver;
    }
    if (url == null) {
      url = other.url;
    }
    if (username == null) {
      username = other.username;
    }
    if (password == null) {
      password = other.password;
    }
    if (schema == null) {
      schema = other.schema;
    }
    if (customProperties == null && other.customProperties != null) {
      customProperties = new LinkedHashMap<>(other.customProperties);
    }
    return this;
  }

  /**
   * Return true if there are no values set for any of url, driver, username and password.
   */
  public boolean isEmpty() {
    return url == null
      && driver == null
      && username == null
      && password == null;
  }

  /**
   * Return the read-only URL to use for creating a matching read only DataSource..
   */
  public String getReadOnlyUrl() {
    return readOnlyUrl;
  }

  /**
   * Set the connection URL to use for a matching read-only connection pool.
   */
  public DataSourceConfig setReadOnlyUrl(String readOnlyUrl) {
    this.readOnlyUrl = readOnlyUrl;
    return this;
  }

  /**
   * Return the connection URL.
   */
  public String getUrl() {
    return url;
  }

  /**
   * Set the connection URL.
   */
  public DataSourceConfig setUrl(String url) {
    this.url = url;
    return this;
  }

  /**
   * Return the database username.
   */
  public String getUsername() {
    return username;
  }

  /**
   * Set the database username.
   */
  public DataSourceConfig setUsername(String username) {
    this.username = username;
    return this;
  }

  /**
   * Return the database password.
   */
  public String getPassword() {
    return password;
  }

  /**
   * Set the database password.
   */
  public DataSourceConfig setPassword(String password) {
    this.password = password;
    return this;
  }

  /**
   * Return the database username.
   */
  public String getSchema() {
    return schema;
  }

  /**
   * Set the default database schema to use.
   */
  public DataSourceConfig setSchema(String schema) {
    this.schema = schema;
    return this;
  }

  /**
   * Return the database driver.
   */
  public String getDriver() {
    return driver;
  }

  /**
   * Set the database driver.
   */
  public DataSourceConfig setDriver(String driver) {
    this.driver = driver;
    return this;
  }

  /**
   * Return the transaction isolation level.
   */
  public int getIsolationLevel() {
    return isolationLevel;
  }

  /**
   * Set the transaction isolation level.
   */
  public DataSourceConfig setIsolationLevel(int isolationLevel) {
    this.isolationLevel = isolationLevel;
    return this;
  }

  /**
   * Return autoCommit setting.
   */
  public boolean isAutoCommit() {
    return autoCommit;
  }

  /**
   * Set to true to turn on autoCommit.
   */
  public DataSourceConfig setAutoCommit(boolean autoCommit) {
    this.autoCommit = autoCommit;
    return this;
  }

  /**
   * Return the read only setting.
   */
  public boolean isReadOnly() {
    return readOnly;
  }

  /**
   * Set to true to for read only.
   */
  public DataSourceConfig setReadOnly(boolean readOnly) {
    this.readOnly = readOnly;
    return this;
  }

  /**
   * Return the minimum number of connections the pool should maintain.
   */
  public int getMinConnections() {
    return minConnections;
  }

  /**
   * Set the minimum number of connections the pool should maintain.
   */
  public DataSourceConfig setMinConnections(int minConnections) {
    this.minConnections = minConnections;
    return this;
  }

  /**
   * Return the maximum number of connections the pool can reach.
   */
  public int getMaxConnections() {
    return maxConnections;
  }

  /**
   * Set the maximum number of connections the pool can reach.
   */
  public DataSourceConfig setMaxConnections(int maxConnections) {
    this.maxConnections = maxConnections;
    return this;
  }

  /**
   * Return the alert implementation to use.
   */
  public DataSourceAlert getAlert() {
    return alert;
  }

  /**
   * Set the alert implementation to use.
   */
  public DataSourceConfig setAlert(DataSourceAlert alert) {
    this.alert = alert;
    return this;
  }

  /**
   * Return the listener to use.
   */
  public DataSourcePoolListener getListener() {
    return listener;
  }

  /**
   * Set the listener to use.
   */
  public DataSourceConfig setListener(DataSourcePoolListener listener) {
    this.listener = listener;
    return this;
  }

  /**
   * Return a SQL statement used to test the database is accessible.
   * <p>
   * Note that if this is not set then it can get defaulted from the
   * DatabasePlatform.
   * </p>
   */
  public String getHeartbeatSql() {
    return heartbeatSql;
  }

  /**
   * Set a SQL statement used to test the database is accessible.
   * <p>
   * Note that if this is not set then it can get defaulted from the
   * DatabasePlatform.
   * </p>
   */
  public DataSourceConfig setHeartbeatSql(String heartbeatSql) {
    this.heartbeatSql = heartbeatSql;
    return this;
  }

  /**
   * Return the heartbeat frequency in seconds.
   * <p>
   * This is the expected frequency in which the DataSource should be checked to
   * make sure it is healthy and trim idle connections.
   * </p>
   */
  public int getHeartbeatFreqSecs() {
    return heartbeatFreqSecs;
  }

  /**
   * Set the expected heartbeat frequency in seconds.
   */
  public DataSourceConfig setHeartbeatFreqSecs(int heartbeatFreqSecs) {
    this.heartbeatFreqSecs = heartbeatFreqSecs;
    return this;
  }

  /**
   * Return the heart beat timeout in seconds.
   */
  public int getHeartbeatTimeoutSeconds() {
    return heartbeatTimeoutSeconds;
  }

  /**
   * Set the heart beat timeout in seconds.
   */
  public DataSourceConfig setHeartbeatTimeoutSeconds(int heartbeatTimeoutSeconds) {
    this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
    return this;
  }

  /**
   * Return true if a stack trace should be captured when obtaining a connection
   * from the pool.
   * <p>
   * This can be used to diagnose a suspected connection pool leak.
   * </p>
   * <p>
   * Obviously this has a performance overhead.
   * </p>
   */
  public boolean isCaptureStackTrace() {
    return captureStackTrace;
  }

  /**
   * Set to true if a stack trace should be captured when obtaining a connection
   * from the pool.
   * <p>
   * This can be used to diagnose a suspected connection pool leak.
   * </p>
   * <p>
   * Obviously this has a performance overhead.
   * </p>
   */
  public DataSourceConfig setCaptureStackTrace(boolean captureStackTrace) {
    this.captureStackTrace = captureStackTrace;
    return this;
  }

  /**
   * Return the max size for reporting stack traces on busy connections.
   */
  public int getMaxStackTraceSize() {
    return maxStackTraceSize;
  }

  /**
   * Set the max size for reporting stack traces on busy connections.
   */
  public DataSourceConfig setMaxStackTraceSize(int maxStackTraceSize) {
    this.maxStackTraceSize = maxStackTraceSize;
    return this;
  }

  /**
   * Return the time in minutes after which a connection could be considered to
   * have leaked.
   */
  public int getLeakTimeMinutes() {
    return leakTimeMinutes;
  }

  /**
   * Set the time in minutes after which a connection could be considered to
   * have leaked.
   */
  public DataSourceConfig setLeakTimeMinutes(int leakTimeMinutes) {
    this.leakTimeMinutes = leakTimeMinutes;
    return this;
  }

  /**
   * Return the size of the PreparedStatement cache (per connection).
   */
  public int getPstmtCacheSize() {
    return pstmtCacheSize;
  }

  /**
   * Set the size of the PreparedStatement cache (per connection).
   */
  public DataSourceConfig setPstmtCacheSize(int pstmtCacheSize) {
    this.pstmtCacheSize = pstmtCacheSize;
    return this;
  }

  /**
   * Return the size of the CallableStatement cache (per connection).
   */
  public int getCstmtCacheSize() {
    return cstmtCacheSize;
  }

  /**
   * Set the size of the CallableStatement cache (per connection).
   */
  public DataSourceConfig setCstmtCacheSize(int cstmtCacheSize) {
    this.cstmtCacheSize = cstmtCacheSize;
    return this;
  }

  /**
   * Return the time in millis to wait for a connection before timing out once
   * the pool has reached its maximum size.
   */
  public int getWaitTimeoutMillis() {
    return waitTimeoutMillis;
  }

  /**
   * Set the time in millis to wait for a connection before timing out once the
   * pool has reached its maximum size.
   */
  public DataSourceConfig setWaitTimeoutMillis(int waitTimeoutMillis) {
    this.waitTimeoutMillis = waitTimeoutMillis;
    return this;
  }

  /**
   * Return the time in seconds a connection can be idle after which it can be
   * trimmed from the pool.
   * <p>
   * This is so that the pool after a busy period can trend over time back
   * towards the minimum connections.
   * </p>
   */
  public int getMaxInactiveTimeSecs() {
    return maxInactiveTimeSecs;
  }

  /**
   * Return the maximum age a connection is allowed to be before it is closed.
   * <p>
   * This can be used to close really old connections.
   * </p>
   */
  public int getMaxAgeMinutes() {
    return maxAgeMinutes;
  }

  /**
   * Set the maximum age a connection can be in minutes.
   */
  public DataSourceConfig setMaxAgeMinutes(int maxAgeMinutes) {
    this.maxAgeMinutes = maxAgeMinutes;
    return this;
  }

  /**
   * Set the time in seconds a connection can be idle after which it can be
   * trimmed from the pool.
   * <p>
   * This is so that the pool after a busy period can trend over time back
   * towards the minimum connections.
   * </p>
   */
  public DataSourceConfig setMaxInactiveTimeSecs(int maxInactiveTimeSecs) {
    this.maxInactiveTimeSecs = maxInactiveTimeSecs;
    return this;
  }


  /**
   * Return the minimum time gap between pool trim checks.
   * <p>
   * This defaults to 59 seconds meaning that the pool trim check will run every
   * minute assuming the heart beat check runs every 30 seconds.
   * </p>
   */
  public int getTrimPoolFreqSecs() {
    return trimPoolFreqSecs;
  }

  /**
   * Set the minimum trim gap between pool trim checks.
   */
  public DataSourceConfig setTrimPoolFreqSecs(int trimPoolFreqSecs) {
    this.trimPoolFreqSecs = trimPoolFreqSecs;
    return this;
  }

  /**
   * Return the pool listener.
   */
  public String getPoolListener() {
    return poolListener;
  }

  /**
   * Set a pool listener.
   */
  public DataSourceConfig setPoolListener(String poolListener) {
    this.poolListener = poolListener;
    return this;
  }

  /**
   * Return true if the DataSource should be left offline.
   * <p>
   * This is to support DDL generation etc without having a real database.
   * </p>
   */
  public boolean isOffline() {
    return offline;
  }

  /**
   * Return true (default) if the DataSource should be fail on start.
   * <p>
   * This enables to initialize the Ebean-Server if the db-server is not yet up.
   * ({@link DataSourceAlert#dataSourceUp(javax.sql.DataSource)} is fired when DS gets up later.)
   * </p>
   */
  public boolean isFailOnStart() {
    return failOnStart;
  }

  /**
   * Set to false, if DataSource should not fail on start. (e.g. DataSource is not available)
   */
  public DataSourceConfig setFailOnStart(boolean failOnStart) {
    this.failOnStart = failOnStart;
    return this;
  }

  /**
   * Set to true if the DataSource should be left offline.
   */
  public DataSourceConfig setOffline(boolean offline) {
    this.offline = offline;
    return this;
  }

  /**
   * Return a map of custom properties for the jdbc driver connection.
   */
  public Map<String, String> getCustomProperties() {
    return customProperties;
  }

  /**
   * Return a list of init queries, that are executed after a connection is opened.
   */
  public List<String> getInitSql() {
    return initSql;
  }

  /**
   * Set custom init queries for each query.
   */
  public DataSourceConfig setInitSql(List<String> initSql) {
    this.initSql = initSql;
    return this;
  }

  /**
   * Set custom properties for the jdbc driver connection.
   */
  public DataSourceConfig setCustomProperties(Map<String, String> customProperties) {
    this.customProperties = customProperties;
    return this;
  }

  /**
   * Add an additional driver property.
   * <pre>{@code
   *
   *   config.addProperty("useSSL", false);
   *
   * }</pre>
   */
  public DataSourceConfig addProperty(String key, String value) {
    if (customProperties == null) {
      customProperties = new LinkedHashMap<>();
    }
    customProperties.put(key, value);
    return this;
  }

  /**
   * Add an additional driver property.
   * <pre>{@code
   *
   *   config.addProperty("useSSL", false);
   *
   * }</pre>
   */
  public DataSourceConfig addProperty(String key, boolean value) {
    return addProperty(key, Boolean.toString(value));
  }

  /**
   * Add an additional driver property.
   * <pre>{@code
   *
   *   config.addProperty("useSSL", false);
   *
   * }</pre>
   */
  public DataSourceConfig addProperty(String key, int value) {
    return addProperty(key, Integer.toString(value));
  }

  /**
   * Return the database owner username.
   */
  public String getOwnerUsername() {
    return ownerUsername;
  }

  /**
   * Set the database owner username (used to create connection for use with InitDatabase).
   */
  public DataSourceConfig setOwnerUsername(String ownerUsername) {
    this.ownerUsername = ownerUsername;
    return this;
  }

  /**
   * Return the database owner password.
   */
  public String getOwnerPassword() {
    return ownerPassword;
  }

  /**
   * Set the database owner password (used to create connection for use with InitDatabase).
   */
  public DataSourceConfig setOwnerPassword(String ownerPassword) {
    this.ownerPassword = ownerPassword;
    return this;
  }

  /**
   * Return the database platform.
   */
  public String getPlatform() {
    return platform;
  }

  /**
   * Set the database platform (for use with ownerUsername and InitDatabase.
   */
  public DataSourceConfig setPlatform(String platform) {
    this.platform = platform;
    if (initDatabase != null) {
      setInitDatabaseForPlatform(platform);
    }
    return this;
  }

  /**
   * Return the InitDatabase to use with ownerUsername.
   */
  public InitDatabase getInitDatabase() {
    return initDatabase;
  }

  /**
   * Set the InitDatabase to use with ownerUsername.
   */
  public DataSourceConfig setInitDatabase(InitDatabase initDatabase) {
    this.initDatabase = initDatabase;
    return this;
  }

  /**
   * Set InitDatabase based on the database platform.
   */
  public DataSourceConfig setInitDatabaseForPlatform(String platform) {
    if (platform != null) {
      switch (platform.toLowerCase()) {
        case POSTGRES:
          initDatabase = new PostgresInitDatabase();
          break;
      }
    }
    return this;
  }

  /**
   * Return true if InitDatabase should be used (when the pool initialises and a connection can't be obtained).
   *
   * @return True to obtain a connection using ownerUsername and run InitDatabase.
   */
  public boolean useInitDatabase() {
    if (ownerUsername != null && ownerPassword != null) {
      if (initDatabase == null) {
        // default to postgres
        initDatabase = new PostgresInitDatabase();
      }
      return true;
    }
    return false;
  }

  /**
   * Load the settings from the properties supplied.
   * <p>
   * You can use this when you have your own properties to use for configuration.
   * </p>
   *
   * @param properties the properties to configure the dataSource
   * @param serverName the name of the specific dataSource (optional)
   */
  public DataSourceConfig loadSettings(Properties properties, String serverName) {
    ConfigPropertiesHelper dbProps = new ConfigPropertiesHelper("datasource", serverName, properties);
    loadSettings(dbProps);
    return this;
  }

  /**
   * Load the settings from the PropertiesWrapper.
   */
  private void loadSettings(ConfigPropertiesHelper properties) {

    username = properties.get("username", username);
    password = properties.get("password", password);
    schema = properties.get("schema", schema);
    platform = properties.get("platform", platform);
    ownerUsername = properties.get("ownerUsername", ownerUsername);
    ownerPassword = properties.get("ownerPassword", ownerPassword);
    if (initDatabase == null && platform != null) {
      setInitDatabaseForPlatform(platform);
    }

    driver = properties.get("driver", properties.get("databaseDriver", driver));
    readOnlyUrl = properties.get("readOnlyUrl", readOnlyUrl);
    url = properties.get("url", properties.get("databaseUrl", url));
    autoCommit = properties.getBoolean("autoCommit", autoCommit);
    readOnly = properties.getBoolean("readOnly", readOnly);
    captureStackTrace = properties.getBoolean("captureStackTrace", captureStackTrace);
    maxStackTraceSize = properties.getInt("maxStackTraceSize", maxStackTraceSize);
    leakTimeMinutes = properties.getInt("leakTimeMinutes", leakTimeMinutes);
    maxInactiveTimeSecs = properties.getInt("maxInactiveTimeSecs", maxInactiveTimeSecs);
    trimPoolFreqSecs = properties.getInt("trimPoolFreqSecs", trimPoolFreqSecs);
    maxAgeMinutes = properties.getInt("maxAgeMinutes", maxAgeMinutes);

    minConnections = properties.getInt("minConnections", minConnections);
    maxConnections = properties.getInt("maxConnections", maxConnections);
    pstmtCacheSize = properties.getInt("pstmtCacheSize", pstmtCacheSize);
    cstmtCacheSize = properties.getInt("cstmtCacheSize", cstmtCacheSize);

    waitTimeoutMillis = properties.getInt("waitTimeout", waitTimeoutMillis);

    heartbeatSql = properties.get("heartbeatSql", heartbeatSql);
    heartbeatTimeoutSeconds = properties.getInt("heartbeatTimeoutSeconds", heartbeatTimeoutSeconds);
    poolListener = properties.get("poolListener", poolListener);
    offline = properties.getBoolean("offline", offline);

    String isoLevel = properties.get("isolationLevel", getTransactionIsolationLevel(isolationLevel));
    this.isolationLevel = getTransactionIsolationLevel(isoLevel);

    this.initSql = parseSql(properties.get("initSql", null));
    this.failOnStart = properties.getBoolean("failOnStart", failOnStart);

    String customProperties = properties.get("customProperties", null);
    if (customProperties != null && customProperties.length() > 0) {
      this.customProperties = parseCustom(customProperties);
    }
  }

  private List<String> parseSql(String sql) {
    List<String> ret = new ArrayList<>();
    if (sql != null) {
      String[] queries = sql.split(";");
      for (String query : queries) {
        query = query.trim();
        if (!query.isEmpty()) {
          ret.add(query);
        }
      }
    }
    return ret;
  }

  Map<String, String> parseCustom(String customProperties) {

    Map<String, String> propertyMap = new LinkedHashMap<String, String>();
    String[] pairs = customProperties.split(";");
    for (String pair : pairs) {
      String[] split = pair.split("=");
      if (split.length == 2) {
        propertyMap.put(split[0], split[1]);
      }
    }
    return propertyMap;
  }

  /**
   * Return the isolation level description from the associated Connection int value.
   */
  private String getTransactionIsolationLevel(int level) {
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
      default:
        throw new RuntimeException("Transaction Isolation level [" + level + "] is not known.");
    }
  }

  /**
   * Return the isolation level for a given string description.
   */
  private int getTransactionIsolationLevel(String level) {
    level = level.toUpperCase();
    if (level.startsWith("TRANSACTION")) {
      level = level.substring("TRANSACTION".length());
    }
    level = level.replace("_", "");
    if ("NONE".equalsIgnoreCase(level)) {
      return Connection.TRANSACTION_NONE;
    }
    if ("READCOMMITTED".equalsIgnoreCase(level)) {
      return Connection.TRANSACTION_READ_COMMITTED;
    }
    if ("READUNCOMMITTED".equalsIgnoreCase(level)) {
      return Connection.TRANSACTION_READ_UNCOMMITTED;
    }
    if ("REPEATABLEREAD".equalsIgnoreCase(level)) {
      return Connection.TRANSACTION_REPEATABLE_READ;
    }
    if ("SERIALIZABLE".equalsIgnoreCase(level)) {
      return Connection.TRANSACTION_SERIALIZABLE;
    }

    throw new RuntimeException("Transaction Isolation level [" + level + "] is not known.");
  }
}
