package io.ebean.datasource;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Configuration information for a DataSource.
 *
 * <pre>{@code
 *
 *   DataSourcePool pool = new DataSourceConfig()
 *     .setName("test")
 *     .setUrl("jdbc:h2:mem:tests")
 *     .setUsername("sa")
 *     .setPassword("")
 *     .build();
 *
 * }</pre>
 */
@SuppressWarnings("removal")
public class DataSourceConfig implements DataSourceBuilder.Settings {

  private static final String POSTGRES = "postgres";

  private String name = "";
  private String readOnlyUrl;
  private String url;
  private String username;
  private String password;
  private String password2;
  private String schema;
  private String driver;
  private InitDatabase initDatabase;
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
  private int minConnections = 2;
  private int maxConnections = 200;
  private int isolationLevel = Connection.TRANSACTION_READ_COMMITTED;
  private boolean autoCommit;
  private boolean readOnly;
  private String heartbeatSql;
  private int heartbeatFreqSecs = 30;
  private int heartbeatTimeoutSeconds = 30;
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
  private Properties clientInfo;
  private String applicationName;

  @Override
  public Settings settings() {
    return this;
  }

  @Override
  public DataSourceBuilder apply(Consumer<Settings> apply) {
    apply.accept(this);
    return this;
  }

  @Override
  public DataSourceConfig copy() {
    DataSourceConfig copy = new DataSourceConfig();
    copy.initDatabase = initDatabase;
    copy.url = url;
    copy.readOnlyUrl = readOnlyUrl;
    copy.username = username;
    copy.password = password;
    copy.password2 = password2;
    copy.schema = schema;
    copy.platform = platform;
    copy.ownerUsername = ownerUsername;
    copy.ownerPassword = ownerPassword;
    copy.driver = driver;
    copy.applicationName = applicationName;
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
    if (clientInfo != null) {
      copy.clientInfo = new Properties(clientInfo);
    }
    copy.initSql = initSql;
    copy.alert = alert;
    copy.listener = listener;
    return copy;
  }

  @Override
  public DataSourceConfig setDefaults(DataSourceBuilder builder) {
    DataSourceBuilder.Settings other = builder.settings();
    if (driver == null) {
      driver = other.getDriver();
    }
    if (url == null) {
      url = other.getUrl();
    }
    if (username == null) {
      username = other.getUsername();
    }
    if (password == null) {
      password = other.getPassword();
    }
    if (password2 == null) {
      password2 = other.getPassword2();
    }
    if (schema == null) {
      schema = other.getSchema();
    }
    if (minConnections == 2 && other.getMinConnections() < 2) {
      minConnections = other.getMinConnections();
    }
    if (customProperties == null) {
      var otherCustomProps = other.getCustomProperties();
      if (otherCustomProps != null) {
        customProperties = new LinkedHashMap<>(otherCustomProps);
      }
    }
    return this;
  }

  @Override
  public boolean isEmpty() {
    return url == null
      && driver == null
      && username == null
      && password == null;
  }

  @Override
  public DataSourcePool build() {
    return DataSourceFactory.create(name, this);
  }

  @Override
  public DataSourceConfig setName(String name) {
    this.name = name;
    return this;
  }

  @Override
  public String getApplicationName() {
    return applicationName;
  }

  @Override
  public DataSourceConfig setApplicationName(String applicationName) {
    this.applicationName = applicationName;
    return this;
  }

  @Override
  public Properties getClientInfo() {
    return clientInfo;
  }

  @Override
  public DataSourceConfig setClientInfo(Properties clientInfo) {
    this.clientInfo = clientInfo;
    return this;
  }

  @Override
  public String getReadOnlyUrl() {
    return readOnlyUrl;
  }

  @Override
  public DataSourceConfig setReadOnlyUrl(String readOnlyUrl) {
    this.readOnlyUrl = readOnlyUrl;
    return this;
  }

  @Override
  public String getUrl() {
    return url;
  }

  @Override
  public DataSourceConfig setUrl(String url) {
    this.url = url;
    return this;
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public DataSourceConfig setUsername(String username) {
    this.username = username;
    return this;
  }

  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public DataSourceConfig setPassword(String password) {
    this.password = password;
    return this;
  }

  @Override
  public String getPassword2() {
    return password2;
  }

  @Override
  public DataSourceConfig setPassword2(String password2) {
    this.password2 = password2;
    return this;
  }

  @Override
  public String getSchema() {
    return schema;
  }

  @Override
  public DataSourceConfig setSchema(String schema) {
    this.schema = schema;
    return this;
  }

  @Override
  public String getDriver() {
    return driver;
  }

  @Override
  public DataSourceConfig setDriver(String driver) {
    this.driver = driver;
    return this;
  }

  @Override
  public int getIsolationLevel() {
    return isolationLevel;
  }

  @Override
  public DataSourceConfig setIsolationLevel(int isolationLevel) {
    this.isolationLevel = isolationLevel;
    return this;
  }

  @Override
  public boolean isAutoCommit() {
    return autoCommit;
  }

  @Override
  public DataSourceConfig setAutoCommit(boolean autoCommit) {
    this.autoCommit = autoCommit;
    return this;
  }

  @Override
  public boolean isReadOnly() {
    return readOnly;
  }

  @Override
  public DataSourceConfig setReadOnly(boolean readOnly) {
    this.readOnly = readOnly;
    return this;
  }

  @Override
  public int getMinConnections() {
    return minConnections;
  }

  @Override
  public DataSourceConfig setMinConnections(int minConnections) {
    this.minConnections = minConnections;
    return this;
  }

  @Override
  public int getMaxConnections() {
    return maxConnections;
  }

  @Override
  public DataSourceConfig setMaxConnections(int maxConnections) {
    this.maxConnections = maxConnections;
    return this;
  }

  @Override
  public DataSourceAlert getAlert() {
    return alert;
  }

  @Override
  public DataSourceConfig setAlert(DataSourceAlert alert) {
    this.alert = alert;
    return this;
  }

  @Override
  public DataSourcePoolListener getListener() {
    return listener;
  }

  @Override
  public DataSourceConfig setListener(DataSourcePoolListener listener) {
    this.listener = listener;
    return this;
  }

  @Override
  public String getHeartbeatSql() {
    return heartbeatSql;
  }

  @Override
  public DataSourceConfig setHeartbeatSql(String heartbeatSql) {
    this.heartbeatSql = heartbeatSql;
    return this;
  }

  @Override
  public int getHeartbeatFreqSecs() {
    return heartbeatFreqSecs;
  }

  @Override
  public DataSourceConfig setHeartbeatFreqSecs(int heartbeatFreqSecs) {
    this.heartbeatFreqSecs = heartbeatFreqSecs;
    return this;
  }

  @Override
  public int getHeartbeatTimeoutSeconds() {
    return heartbeatTimeoutSeconds;
  }

  @Override
  public DataSourceConfig setHeartbeatTimeoutSeconds(int heartbeatTimeoutSeconds) {
    this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
    return this;
  }

  @Override
  public boolean isCaptureStackTrace() {
    return captureStackTrace;
  }

  @Override
  public DataSourceConfig setCaptureStackTrace(boolean captureStackTrace) {
    this.captureStackTrace = captureStackTrace;
    return this;
  }

  @Override
  public int getMaxStackTraceSize() {
    return maxStackTraceSize;
  }

  @Override
  public DataSourceConfig setMaxStackTraceSize(int maxStackTraceSize) {
    this.maxStackTraceSize = maxStackTraceSize;
    return this;
  }

  @Override
  public int getLeakTimeMinutes() {
    return leakTimeMinutes;
  }

  @Override
  public DataSourceConfig setLeakTimeMinutes(int leakTimeMinutes) {
    this.leakTimeMinutes = leakTimeMinutes;
    return this;
  }

  @Override
  public int getPstmtCacheSize() {
    return pstmtCacheSize;
  }

  @Override
  public DataSourceConfig setPstmtCacheSize(int pstmtCacheSize) {
    this.pstmtCacheSize = pstmtCacheSize;
    return this;
  }

  @Override
  public int getCstmtCacheSize() {
    return cstmtCacheSize;
  }

  @Override
  public DataSourceConfig setCstmtCacheSize(int cstmtCacheSize) {
    this.cstmtCacheSize = cstmtCacheSize;
    return this;
  }

  @Override
  public int getWaitTimeoutMillis() {
    return waitTimeoutMillis;
  }

  @Override
  public DataSourceConfig setWaitTimeoutMillis(int waitTimeoutMillis) {
    this.waitTimeoutMillis = waitTimeoutMillis;
    return this;
  }

  @Override
  public int getMaxInactiveTimeSecs() {
    return maxInactiveTimeSecs;
  }

  @Override
  public int getMaxAgeMinutes() {
    return maxAgeMinutes;
  }

  @Override
  public DataSourceConfig setMaxAgeMinutes(int maxAgeMinutes) {
    this.maxAgeMinutes = maxAgeMinutes;
    return this;
  }

  @Override
  public DataSourceConfig setMaxInactiveTimeSecs(int maxInactiveTimeSecs) {
    this.maxInactiveTimeSecs = maxInactiveTimeSecs;
    return this;
  }


  @Override
  public int getTrimPoolFreqSecs() {
    return trimPoolFreqSecs;
  }

  @Override
  public DataSourceConfig setTrimPoolFreqSecs(int trimPoolFreqSecs) {
    this.trimPoolFreqSecs = trimPoolFreqSecs;
    return this;
  }

  @Override
  public String getPoolListener() {
    return poolListener;
  }

  @Override
  public DataSourceConfig setPoolListener(String poolListener) {
    this.poolListener = poolListener;
    return this;
  }

  @Override
  public boolean isOffline() {
    return offline;
  }

  @Override
  public boolean isFailOnStart() {
    return failOnStart;
  }

  @Override
  public DataSourceConfig setFailOnStart(boolean failOnStart) {
    this.failOnStart = failOnStart;
    return this;
  }

  @Override
  public DataSourceConfig setOffline(boolean offline) {
    this.offline = offline;
    return this;
  }

  @Override
  public Map<String, String> getCustomProperties() {
    return customProperties;
  }

  @Override
  public List<String> getInitSql() {
    return initSql;
  }

  @Override
  public DataSourceConfig setInitSql(List<String> initSql) {
    this.initSql = initSql;
    return this;
  }

  @Override
  public DataSourceConfig setCustomProperties(Map<String, String> customProperties) {
    this.customProperties = customProperties;
    return this;
  }

  @Override
  public DataSourceConfig addProperty(String key, String value) {
    if (customProperties == null) {
      customProperties = new LinkedHashMap<>();
    }
    customProperties.put(key, value);
    return this;
  }

  @Override
  public DataSourceConfig addProperty(String key, boolean value) {
    return addProperty(key, Boolean.toString(value));
  }

  @Override
  public DataSourceConfig addProperty(String key, int value) {
    return addProperty(key, Integer.toString(value));
  }

  @Override
  public String getOwnerUsername() {
    return ownerUsername;
  }

  @Override
  public DataSourceConfig setOwnerUsername(String ownerUsername) {
    this.ownerUsername = ownerUsername;
    return this;
  }

  @Override
  public String getOwnerPassword() {
    return ownerPassword;
  }

  @Override
  public DataSourceConfig setOwnerPassword(String ownerPassword) {
    this.ownerPassword = ownerPassword;
    return this;
  }

  @Override
  public String getPlatform() {
    return platform;
  }

  @Override
  public DataSourceConfig setPlatform(String platform) {
    this.platform = platform;
    if (initDatabase != null) {
      setInitDatabaseForPlatform(platform);
    }
    return this;
  }

  @Override
  public InitDatabase getInitDatabase() {
    return initDatabase;
  }

  @Override
  public DataSourceConfig setInitDatabase(InitDatabase initDatabase) {
    this.initDatabase = initDatabase;
    return this;
  }

  @Override
  public DataSourceConfig setInitDatabaseForPlatform(String platform) {
    if (platform != null) {
      if (POSTGRES.equalsIgnoreCase(platform)) {
        initDatabase = new PostgresInitDatabase();
      }
    }
    return this;
  }

  @Override
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

  @Override
  public DataSourceConfig load(Properties properties) {
    return load(properties, null);
  }

  @Override
  public DataSourceConfig load(Properties properties, String prefix) {
    loadSettings(new ConfigPropertiesHelper(prefix, null, properties));
    return this;
  }

  @Override
  public DataSourceConfig loadSettings(Properties properties, String poolName) {
    loadSettings(new ConfigPropertiesHelper("datasource", poolName, properties));
    return this;
  }

  /**
   * Load the settings from the PropertiesWrapper.
   */
  private void loadSettings(ConfigPropertiesHelper properties) {
    username = properties.get("username", username);
    password = properties.get("password", password);
    password2 = properties.get("password2", password2);
    schema = properties.get("schema", schema);
    platform = properties.get("platform", platform);
    ownerUsername = properties.get("ownerUsername", ownerUsername);
    ownerPassword = properties.get("ownerPassword", ownerPassword);
    if (initDatabase == null && platform != null) {
      setInitDatabaseForPlatform(platform);
    }
    applicationName = properties.get("applicationName", applicationName);
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

    String isoLevel = properties.get("isolationLevel", _isolationLevel(isolationLevel));
    this.isolationLevel = _isolationLevel(isoLevel);
    this.initSql = parseSql(properties.get("initSql", null));
    this.failOnStart = properties.getBoolean("failOnStart", failOnStart);

    String customProperties = properties.get("customProperties", null);
    if (customProperties != null && !customProperties.isEmpty()) {
      this.customProperties = parseCustom(customProperties);
    }
    String infoProperties = properties.get("clientInfo", null);
    if (infoProperties != null && !infoProperties.isEmpty()) {
      Map<String, String> pairs = parseCustom(infoProperties);
      if (!pairs.isEmpty()) {
        this.clientInfo = new Properties();
        for (Map.Entry<String, String> entry : pairs.entrySet()) {
          this.clientInfo.setProperty(entry.getKey(), entry.getValue());
        }
      }
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
    Map<String, String> propertyMap = new LinkedHashMap<>();
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
  private String _isolationLevel(int level) {
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
  private int _isolationLevel(String level) {
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

  /**
   * Return the connection properties.
   */
  public Properties connectionProperties() {
    if (username == null) {
      throw new DataSourceConfigurationException("DataSource user is not set?");
    }
    if (password == null) {
      throw new DataSourceConfigurationException("DataSource password is null?");
    }
    Properties connectionProps = new Properties();
    connectionProps.setProperty("user", username);
    connectionProps.setProperty("password", password);
    if (customProperties != null) {
      for (Map.Entry<String, String> entry : customProperties.entrySet()) {
        connectionProps.setProperty(entry.getKey(), entry.getValue());
      }
    }
    return connectionProps;
  }
}
