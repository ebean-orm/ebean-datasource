# Guide: Connection Validation Best Practices

## Purpose

This guide explains how connection pool validation works in ebean-datasource, the recommended approach for validating connections, and when (if ever) you need explicit configuration. Understanding this helps you optimize connection pool performance without unnecessary overhead.

---

## Overview: Connection Validation

Connection validation ensures that connections in the pool are healthy and can communicate with the database. Without validation, your application might try to use a "dead" connection (network timeout, database restart, etc.) and get errors.

The ebean-datasource connection pool has a background heartbeat thread that periodically validates connections are still alive. This guide explains how to configure it correctly.

---

## Best Practice: Use Connection.isValid() (Default)

The **best practice is to NOT set an explicit `heartbeatSql`**. Instead, let the JDBC driver handle connection validation using the standard `Connection.isValid()` method.

### Why Connection.isValid() is Best Practice

```java
DataSourcePool pool = DataSourcePool.builder()
  .name("mypool")
  .url("jdbc:postgresql://localhost:5432/myapp")
  .username("user")
  .password("pass")
  .minConnections(5)
  .maxConnections(50)
  // No heartbeatSql() - use default Connection.isValid()
  .build();
```

**Advantages:**

- ✅ **JDBC driver optimization:** The driver picks the most efficient validation method for your specific database
- ✅ **No query overhead:** Modern JDBC drivers use lightweight protocol-level pings instead of SQL queries
- ✅ **Database-specific:** PostgreSQL, MySQL, Oracle, etc. each have optimal validation mechanisms
- ✅ **Less resource usage:** Network ping is cheaper than executing a query
- ✅ **Automatically configured:** No manual setup needed per database platform

### How Connection.isValid() Works

When `Connection.isValid()` is called:

1. **PostgreSQL:** Uses lightweight protocol ping
2. **MySQL:** Uses lightweight protocol ping
3. **Oracle:** Uses lightweight connection check
4. **SQL Server:** Uses lightweight connection check
5. **H2/Other DBs:** Falls back to simple query or protocol check

Your JDBC driver handles all of this automatically.

---

## Configuration: Heartbeat Settings

These are the only heartbeat settings you typically need:

### Disable Heartbeat (if needed)

```java
DataSourcePool pool = DataSourcePool.builder()
  .name("mypool")
  .url("jdbc:postgresql://localhost:5432/myapp")
  .username("user")
  .password("pass")
  .validateOnHeartbeat(false)   // Disable heartbeat validation
  .build();
```

**When to disable:**
- AWS Lambda (automatically disabled)
- Very short-lived applications
- Environments where background threads should be avoided

### Adjust Heartbeat Frequency

```java
DataSourcePool pool = DataSourcePool.builder()
  .name("mypool")
  .url("jdbc:postgresql://localhost:5432/myapp")
  .username("user")
  .password("pass")
  .validateOnHeartbeat(true)     // Enable (default in non-Lambda)
  .heartbeatFreqSecs(30)         // Check every 30 seconds (default)
  .build();
```

**Typical values:**
- `30` seconds - Default, suitable for most applications
- `60` seconds - Longer interval, fewer validations (less resource usage)
- `15` seconds - Shorter interval, more validations (detects stale connections faster)

**Adjust based on:**
- Network reliability (unstable network → shorter interval)
- Database availability (frequently restarted → shorter interval)
- Resource constraints (minimal resources → longer interval)

### Heartbeat Timeout

```java
DataSourcePool pool = DataSourcePool.builder()
  .name("mypool")
  .url("jdbc:postgresql://localhost:5432/myapp")
  .username("user")
  .password("pass")
  .heartbeatTimeoutSeconds(30)   // Validation must complete in 30 seconds
  .build();
```

If a heartbeat validation takes longer than this timeout, the connection is marked as dead and will be removed from the pool.

---

## When (Rarely) You Need Explicit heartbeatSql()

In almost all modern scenarios, you should NOT set explicit `heartbeatSql()`. Only in these edge cases:

### Case 1: Database requires specific validation query

Some databases or custom setups might require a specific query:

```java
DataSourcePool pool = DataSourcePool.builder()
  .name("mypool")
  .url("jdbc:postgresql://localhost:5432/myapp")
  .username("user")
  .password("pass")
  .heartbeatSql("SELECT 1")      // Only if JDBC driver doesn't support isValid()
  .build();
```

**When:**
- Using a very old database driver (pre-2010s)
- Custom database with unusual requirements
- Explicit requirement from database administrator

### Case 2: Custom validation logic needed

If you need to validate application state, not just connection state:

```java
DataSourcePool pool = DataSourcePool.builder()
  .name("mypool")
  .url("jdbc:postgresql://localhost:5432/myapp")
  .username("user")
  .password("pass")
  .heartbeatSql("SELECT COUNT(*) FROM health_check_table")  // App-specific check
  .build();
```

This is rare and should be considered carefully - it adds query overhead to every heartbeat check.

---

## Connection Validation in Different Scenarios

### Standard Server Application

```java
// Recommended: Use default Connection.isValid()
DataSourcePool pool = DataSourcePool.builder()
  .name("mypool")
  .url("jdbc:postgresql://localhost:5432/myapp")
  .username("user")
  .password("pass")
  .minConnections(5)
  .maxConnections(50)
  // No heartbeatSql() - Connection.isValid() used by default
  .build();
```

### Kubernetes Deployment

```java
// Same as standard - Connection.isValid() is ideal for Kubernetes
DataSourcePool pool = DataSourcePool.builder()
  .name("mypool")
  .url(System.getenv("DATABASE_URL"))
  .username(System.getenv("DB_USER"))
  .password(System.getenv("DB_PASSWORD"))
  .minConnections(5)
  .initialConnections(20)
  .maxConnections(50)
  // No heartbeatSql() - Connection.isValid() detects pod failures
  .build();
```

### AWS Lambda

```java
// Heartbeat automatically disabled in Lambda
DataSourcePool pool = DataSourcePool.builder()
  .name("mypool")
  .url("jdbc:postgresql://mydb.region.rds.amazonaws.com:5432/myapp")
  .username(System.getenv("DB_USER"))
  .password(System.getenv("DB_PASSWORD"))
  .minConnections(1)
  .initialConnections(2)
  .maxConnections(10)
  // validateOnHeartbeat = false (auto-detected in Lambda)
  // No heartbeatSql() needed
  .build();
```

### Read-Only Pool (Aurora / Replicas)

```java
// Read-only pools also use Connection.isValid() by default
DataSourcePool readPool = DataSourcePool.builder()
  .name("datasource-read")
  .url("jdbc:postgresql://read-replica.example.com:5432/myapp")
  .username("user")
  .password("pass")
  .readOnly(true)
  .autoCommit(true)
  .minConnections(5)
  .maxConnections(50)
  // No heartbeatSql() - Connection.isValid() works for read replicas too
  .build();
```

---

## Connection Validation Timeline

Here's what happens with the default heartbeat configuration:

```
Application startup
  ├─ Create connection pool
  ├─ Create minConnections
  └─ Start background heartbeat thread

Every 30 seconds (heartbeatFreqSecs default)
  ├─ Heartbeat thread wakes up
  ├─ For each connection: Call Connection.isValid()
  │  ├─ JDBC driver sends lightweight protocol ping
  │  ├─ Database responds
  │  └─ Connection marked as alive
  └─ Back to sleep for 30 seconds

Connection returned to pool after use
  ├─ If an SQLException was thrown
  │  ├─ Connection tested eagerly
  │  └─ Dead connection removed from pool
  └─ If no error, connection stays in pool

Application shutdown
  ├─ Stop heartbeat thread
  └─ Close all connections
```

---

## Monitoring: Checking Heartbeat Health

If you want to understand what your heartbeat is doing:

```java
DataSourcePool pool = DataSourcePool.builder()
  .name("mypool")
  .url("jdbc:postgresql://localhost:5432/myapp")
  .username("user")
  .password("pass")
  .build();

// Later, check pool status
ConnectionPoolStatistics stats = pool.getStatus();
System.out.println("Pool size: " + stats.size());
System.out.println("Available: " + stats.free());
System.out.println("Busy: " + stats.busy());
```

This tells you the current state of connections validated by heartbeat.

---

## Performance Impact

### With Default Connection.isValid()

- **Per heartbeat cycle:** Lightweight protocol ping (~1-5ms per connection)
- **Total overhead:** Minimal - only a few milliseconds every 30 seconds
- **Network traffic:** Low - simple protocol handshake

### With Explicit heartbeatSql("SELECT 1")

- **Per heartbeat cycle:** Full SQL query parsing and execution (~10-50ms per connection)
- **Total overhead:** Higher - noticeable CPU and database load
- **Network traffic:** Higher - full query round-trip

**Recommendation:** Stick with default `Connection.isValid()` for best performance.

---

## Troubleshooting

### Problem: "Connection reset by peer" errors

**Cause:** Network connection died, heartbeat didn't catch it in time

**Solutions:**
1. Reduce `heartbeatFreqSecs` (e.g., from 30 to 15 seconds)
2. Use Kubernetes liveness probes to restart unhealthy pods
3. Configure database network security to detect failures faster

### Problem: Heartbeat taking too long

**Cause:** Database under heavy load, `Connection.isValid()` slow to respond

**Solutions:**
1. Increase `heartbeatTimeoutSeconds` if connections are legitimately slow
2. Reduce `heartbeatFreqSecs` to spread load (check less frequently)
3. Optimize database performance

### Problem: Too many validation queries in database logs

**Cause:** Explicit `heartbeatSql()` set, logging all queries

**Solutions:**
1. Remove explicit `heartbeatSql()` - use default `Connection.isValid()`
2. If you must use heartbeatSql, adjust database query logging to exclude it

---

## Summary: Best Practices

✅ **DO:**
- Let the JDBC driver handle validation with default `Connection.isValid()`
- Use `validateOnHeartbeat(true)` for all applications except Lambda
- Use default `heartbeatFreqSecs(30)` unless you have specific reasons otherwise
- Let ebean-datasource auto-disable heartbeat in Lambda

❌ **DON'T:**
- Set explicit `heartbeatSql("SELECT 1")` unless required for your database driver
- Disable heartbeat validation in standard applications
- Use expensive queries for heartbeat validation
- Assume connections are valid without validation

---

## Next Steps

- Read [Creating a DataSource Pool](create-datasource-pool.md) for basic setup
- See [AWS Aurora with Dual DataSources](aws-aurora-read-write-split.md) for Aurora-specific configuration
- Check [JDBC Driver Documentation](https://jdbc.postgresql.org/) for your database's Connection.isValid() implementation
- Review the [ebean-datasource API](https://javadoc.io/doc/io.ebean/ebean-datasource) for all available configuration options
