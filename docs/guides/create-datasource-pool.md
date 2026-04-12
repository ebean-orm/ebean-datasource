# Guide: Create a DataSource Pool

## Purpose

This guide provides step-by-step instructions for creating and configuring DataSource pools for different deployment scenarios. Follow the steps for the scenario that matches your use case.

---

## Prerequisites

- A Java project with Maven
- `io.ebean:ebean-datasource` dependency already added to `pom.xml`
- Database connection details (URL, username, password)
- Understanding of your deployment environment (standard server, Kubernetes, AWS Lambda, etc.)

---

## Scenario 1: Basic DataSource Pool (Standard Server/VM)

Use this configuration for traditional server deployments.

### Step 1 — Create the pool builder

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

### Step 2 — Use the pool in your application

```java
try (Connection connection = pool.getConnection()) {
  try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM users WHERE id = ?")) {
    stmt.setInt(1, 123);
    try (ResultSet rs = stmt.executeQuery()) {
      while (rs.next()) {
        // Process results
      }
    }
  }
}
```

---

## Scenario 2: Read-Only Pool

Use this configuration for read-only workloads (analytics, reporting, caching layers).
This adds readOnly(true) and autoCommit(true) options when building the pool.

### Step 1 — Create a read-only pool

```java
DataSourcePool readOnlyPool = DataSourcePool.builder()
  .name("mypool-readonly")
  .url("jdbc:postgresql://read-replica.example.com:5432/myapp")
  .username("readonly_user")
  .password("password")
  .readOnly(true)           // Signal no writes will occur
  .autoCommit(true)         // Skip transaction overhead
  .minConnections(5)
  .maxConnections(30)
  .build();
```

### Step 2 — Use the read-only pool

```java
try (Connection connection = readOnlyPool.getConnection()) {
  try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM large_table")) {
    try (ResultSet rs = stmt.executeQuery()) {
      while (rs.next()) {
        // Process results - queries will be faster with read-only optimization
      }
    }
  }
}
```

---

## Scenario 3: Kubernetes Deployment

Use this configuration when deploying to Kubernetes clusters.

### Step 1 — Create a pool with warm-up configuration

When pods start, they immediately receive production traffic. Start with `initialConnections`
set higher than `minConnections` to avoid cold-start connection creation overhead.

```java
DataSourcePool pool = DataSourcePool.builder()
  .name("mypool")
  .url("jdbc:postgresql://postgres.default.svc.cluster.local:5432/myapp")
  .username("app_user")
  .password(System.getenv("DB_PASSWORD"))  // Use environment variables
  .minConnections(5)
  .initialConnections(20)     // Start with 20 connections ready
  .maxConnections(50)
  .build();
```

### Step 2 — Use environment variables for configuration

For Kubernetes deployments, externalize database credentials:

```java
String dbUrl = System.getenv("DATABASE_URL");
String dbUser = System.getenv("DATABASE_USER");
String dbPass = System.getenv("DATABASE_PASSWORD");

DataSourcePool pool = DataSourcePool.builder()
  .name("mypool")
  .url(dbUrl)
  .username(dbUser)
  .password(dbPass)
  .minConnections(5)
  .initialConnections(20)
  .maxConnections(50)
  .build();
```


---

## Scenario 4: AWS Lambda

Use this configuration for AWS Lambda functions.

### Step 1 — Create a minimal pool

The ebean datasource automatically detects when it is running in Lambda via the `LAMBDA_TASK_ROOT` environment variable
and disables background heartbeat validation to optimize for cost and lambda suspend behaviour.

```java
DataSourcePool pool = DataSourcePool.builder()
  .name("mypool")
  .url("jdbc:postgresql://mydb.region.rds.amazonaws.com:5432/myapp")
  .username("app_user")
  .password(System.getenv("DB_PASSWORD"))
  .minConnections(1)
  .initialConnections(2)
  .maxConnections(10)
  .build();
```

### Step 2 — Understanding Lambda connection pooling

**Important:** Connection pooling in Lambda is **per-Lambda instance**, not per-invocation:

- A single Lambda container can be reused across many warm invocations
- Connection pool persists across warm invocations
- Cold starts get a fresh pool
- Connections are trimmed automatically if unused


---

## Configuration Reference

### Common Settings

| Setting | Default | Purpose |
|---------|---------|---------|
| `minConnections` | 2 | Minimum connections to maintain in the pool |
| `initialConnections` | Same as minConnections | Connections to create when pool starts (useful for Kubernetes/warm-up) |
| `maxConnections` | 200 | Maximum connections to allow |
| `readOnly` | false | Set to true for read-only workloads |
| `autoCommit` | false | Set to true to skip transaction boundaries |
| `validateOnHeartbeat` | true (false in Lambda) | Enable background connection validation |
| `heartbeatFreqSecs` | 30 | How often to validate connections (seconds) |

### Typical Sizing

**Development/Testing:**
```java
.minConnections(1)
.initialConnections(1)
.maxConnections(5)
```

**Production - Standard Server:**
```java
.minConnections(5)
.initialConnections(10)
.maxConnections(50)
```

**Production - High Traffic:**
```java
.minConnections(10)
.initialConnections(40)
.maxConnections(100)
```

**Lambda - Standard:**
```java
.minConnections(1)
.initialConnections(2)
.maxConnections(10)
```

**Lambda - Provisioned Concurrency:**
```java
.minConnections(2)
.initialConnections(10)
.maxConnections(20)
```

---

## Next Steps

- Read the [README](../../README.md) for more information about the connection pool
- Check the [ebean-datasource GitHub repository](https://github.com/ebean-orm/ebean-datasource) for latest updates
- Consult the [DataSourceBuilder API documentation](https://javadoc.io/doc/io.ebean/ebean-datasource) for all available configuration options
