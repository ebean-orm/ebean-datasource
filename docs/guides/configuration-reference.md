# Guide: Configuration Reference

## Purpose

A complete reference of every `DataSourceBuilder` setting, its default value, and the equivalent
property key for file-based / external configuration. Use this alongside the task-focused guides
(pool creation, read-only pools, validation, Aurora) when you need to know exactly what a setting
does or what it defaults to.

---

## Two ways to configure

### 1. Programmatic (builder)

```java
DataSourcePool pool = DataSourcePool.builder()
  .name("mypool")
  .url("jdbc:postgresql://localhost:5432/myapp")
  .username("app_user")
  .password("password")
  .minConnections(5)
  .maxConnections(50)
  .build();
```

### 2. Properties (external configuration)

The builder can load settings from `java.util.Properties`. There are three entry points:

```java
Properties props = ...; // loaded from file, env, avaje-config, Spring, etc.

// (a) no prefix: keys are "username", "url", ...
DataSourcePool pool = DataSourcePool.builder().load(props).build();

// (b) custom prefix: keys are "my-db.username", "my-db.url", ...
DataSourcePool pool = DataSourcePool.builder().load(props, "my-db").build();

// (c) "datasource.<poolName>." prefix: keys are "datasource.hr.username", ...
DataSourcePool pool = DataSourcePool.builder().loadSettings(props, "hr").build();
```

Example properties file using the `loadSettings` convention with pool name `hr`:

```properties
datasource.hr.username=app_user
datasource.hr.password=password
datasource.hr.url=jdbc:postgresql://localhost:5432/myapp
datasource.hr.minConnections=5
datasource.hr.maxConnections=50
datasource.hr.leakTimeMinutes=30
```

> When used through **Ebean ORM**, these `datasource.<name>.*` properties are typically placed in
> `application.yaml` / `application.properties` and loaded for you via avaje-config.

Property keys are matched case-insensitively.

---

## Connection settings

| Builder method | Property key | Default | Description |
|----------------|--------------|---------|-------------|
| `url(String)` | `url` (or `databaseUrl`) | – | JDBC URL. |
| `username(String)` | `username` | – | Database username. |
| `password(String)` | `password` | – | Database password. |
| `readOnlyUrl(String)` | `readOnlyUrl` | – | Optional separate URL for read-only connections. |
| `driver(...)` / `driver(String)` | `driver` (or `databaseDriver`) | auto from URL | JDBC driver class / instance. |
| `schema(String)` | `schema` | driver default | Default schema applied to connections. |
| `catalog(String)` | `catalog` | driver default | Default catalog applied to connections. |
| `isolationLevel(int)` | `isolationLevel` | `READ_COMMITTED` | Transaction isolation level. Property accepts names e.g. `READ_COMMITTED`. |
| `autoCommit(boolean)` | `autoCommit` | `false` | Auto-commit mode for pooled connections. |
| `readOnly(boolean)` | `readOnly` | `false` | Mark connections read-only (optimises read workloads). |
| `applicationName(String)` | `applicationName` | – | Application name reported to the driver where supported. |
| `clientInfo(Properties)` | `clientInfo` | – | Client info properties (semicolon separated `key=value` in properties form). |
| `customProperties(Map)` / `addProperty(...)` | `customProperties` | – | Extra JDBC driver connection properties (semicolon separated `key=value` in properties form). |
| `initSql(List<String>)` | `initSql` | – | SQL run on each new connection (semicolon separated statements in properties form). See per-connection init below. |

## Pool sizing

| Builder method | Property key | Default | Description |
|----------------|--------------|---------|-------------|
| `minConnections(int)` | `minConnections` | `2` | Minimum connections maintained in the pool. |
| `initialConnections(int)` | `initialConnections` | = `minConnections` | Connections created on startup. Set higher than min for smooth warm-up (Kubernetes). |
| `maxConnections(int)` | `maxConnections` | `200` | Maximum connections. Threads block (up to `waitTimeout`) when this is reached. |

## Timeouts, trimming and ageing

| Builder method | Property key | Default | Description |
|----------------|--------------|---------|-------------|
| `waitTimeoutMillis(int)` | `waitTimeout` | `1000` | Millis a thread waits for a free connection once the pool is at max before throwing `ConnectionPoolExhaustedException`. |
| `maxInactiveTimeSecs(int)` | `maxInactiveTimeSecs` | `300` | Idle seconds after which a free connection can be trimmed back towards `minConnections`. |
| `maxAgeMinutes(int)` | `maxAgeMinutes` | `0` (unlimited) | Maximum age of a connection before it is trimmed regardless of activity. |
| `trimPoolFreqSecs(int)` | `trimPoolFreqSecs` | `59` | How often the background trim check runs. |

## Health checks / heartbeat

| Builder method | Property key | Default | Description |
|----------------|--------------|---------|-------------|
| `validateOnHeartbeat(boolean)` | `validateOnHeartbeat` | `true` (`false` in AWS Lambda) | Enable the background heartbeat that validates the pool. |
| `heartbeatFreqSecs(int)` | *(builder only)* | `30` | How often the heartbeat runs. |
| `heartbeatTimeoutSeconds(int)` | `heartbeatTimeoutSeconds` | `30` | Query timeout for the heartbeat validation. |
| `heartbeatSql(String)` | `heartbeatSql` | `Connection.isValid()` / platform default | Explicit validation SQL. Rarely needed — see the validation guide. |
| `heartbeatMaxPoolExhaustedCount(int)` | *(builder only)* | `10` | Consecutive heartbeat pool-exhaustion detections before the pool is reset (leak recovery). |

See [Connection Validation Best Practices](connection-validation-best-practices.md) for details.

## Leak detection / diagnostics

| Builder method | Property key | Default | Description |
|----------------|--------------|---------|-------------|
| `leakTimeMinutes(int)` | `leakTimeMinutes` | `30` | A busy (checked-out) connection older than this is treated as a leak and force-closed during a pool reset. |
| `captureStackTrace(boolean)` | `captureStackTrace` | `false` | Capture the stack trace when a connection is obtained, to locate leaks. Has a performance cost. |
| `maxStackTraceSize(int)` | `maxStackTraceSize` | `5` | Number of stack frames reported for busy connections. |

See [Troubleshooting Connection Leaks & Pool Exhaustion](troubleshooting-connection-leaks.md).

## Statement caching

| Builder method | Property key | Default | Description |
|----------------|--------------|---------|-------------|
| `pstmtCacheSize(int)` | `pstmtCacheSize` | `300` | `PreparedStatement` cache size, per connection. |
| `cstmtCacheSize(int)` | `cstmtCacheSize` | `20` | `CallableStatement` cache size, per connection. |

## Lifecycle / startup

| Builder method | Property key | Default | Description |
|----------------|--------------|---------|-------------|
| `failOnStart(boolean)` | `failOnStart` | `true` | When `false`, the pool starts even if the database is unavailable (it recovers later via heartbeat). |
| `offline(boolean)` | `offline` | `false` | Start the pool offline (no connections created until `online()`). |
| `shutdownOnJvmExit(boolean)` | `shutdownOnJvmExit` | `false` | Register a JVM shutdown hook to close the pool on exit. |
| `enforceCleanClose(boolean)` | `enforceCleanClose` | `false` | Throw if a dirty (uncommitted) connection is closed. Recommended in tests. See [issue #116](https://github.com/ebean-orm/ebean-datasource/issues/116). |

## Hooks and extension points

| Builder method | Property key | Description |
|----------------|--------------|-------------|
| `connectionInitializer(NewConnectionInitializer)` | *(builder only)* | Hook called when each new connection is created (`preInitialize` / `postInitialize`). |
| `defaultConnectionInitializer(NewConnectionInitializer)` | *(builder only)* | Fallback initializer used only if one is not otherwise set. |
| `listener(DataSourcePoolListener)` | *(builder only)* | Callbacks on borrow (`onAfterBorrowConnection`) and return (`onBeforeReturnConnection`). |
| `poolListener(String)` | `poolListener` | Class name of a `DataSourcePoolListener` to instantiate. |
| `alert(DataSourceAlert)` | *(builder only)* | Callbacks for `dataSourceUp` / `dataSourceDown` (outage alerting). See [Monitoring](monitoring-pool-metrics.md). |

## Per-connection initialization

Use `initSql` for simple statements, or a `NewConnectionInitializer` for programmatic control. This
is the right place to set things like a Postgres `search_path` or a per-session `statement_timeout`.

```java
DataSourcePool pool = DataSourcePool.builder()
  .name("mypool")
  .url("jdbc:postgresql://localhost:5432/myapp")
  .username("app_user")
  .password("password")
  .initSql(List.of("set search_path to app, public"))
  .connectionInitializer(new NewConnectionInitializer() {
    @Override
    public void postInitialize(Connection connection) {
      try (Statement st = connection.createStatement()) {
        st.execute("set statement_timeout to '30s'");
      } catch (SQLException e) {
        throw new IllegalStateException(e);
      }
    }
  })
  .build();
```

---

## Deprecated `setXxx` methods

Many settings historically used a `setXxx` name (e.g. `setMinConnections`). These remain for
backwards compatibility but are deprecated — prefer the fluent forms shown above
(`minConnections`, `maxConnections`, `heartbeatFreqSecs`, etc.).

---

## Next Steps

- [Create a DataSource Pool](create-datasource-pool.md)
- [Connection Validation Best Practices](connection-validation-best-practices.md)
- [Troubleshooting Connection Leaks & Pool Exhaustion](troubleshooting-connection-leaks.md)
- [Monitoring Pool Metrics](monitoring-pool-metrics.md)
- Full Javadoc: [io.ebean:ebean-datasource](https://javadoc.io/doc/io.ebean/ebean-datasource)
