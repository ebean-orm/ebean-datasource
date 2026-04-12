[![Build](https://github.com/ebean-orm/ebean-datasource/actions/workflows/build.yml/badge.svg)](https://github.com/ebean-orm/ebean-datasource/actions/workflows/build.yml)
[![Maven Central : ebean](https://maven-badges.herokuapp.com/maven-central/io.ebean/ebean-datasource/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.ebean/ebean-datasource)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/ebean-orm/ebean-datasource/blob/master/LICENSE)
[![JDK EA](https://github.com/ebean-orm/ebean-datasource/actions/workflows/jdk-ea.yml/badge.svg)](https://github.com/ebean-orm/ebean-datasource/actions/workflows/jdk-ea.yml)

# ebean-datasource
A robust and fast SQL DataSource implementation

### Example use:

```java
DataSourcePool pool = DataSourcePool.builder()
  .name("mypool")
  .url("jdbc:h2:mem:test")
  .username("sa")
  .password("")
  .build();
```

For read-only use cases, configure the pool with `readOnly(true)` and `autoCommit(true)` for optimal performance:

```java
DataSourcePool readOnlyPool = DataSourcePool.builder()
  .name("mypool-readonly")
  .url("jdbc:h2:mem:test")
  .username("sa")
  .password("")
  .readOnly(true)
  .autoCommit(true)
  .build();
```

Use like any java.sql.DataSource obtaining pooled connections
```java
    try (Connection connection = pool.getConnection()) {
      try (PreparedStatement stmt = connection.prepareStatement("create table junk (acol varchar(10))")) {
        stmt.execute();
        connection.commit();
      }
    }

```

For CRaC beforeCheckpoint() we can take the pool offline
closing the connections and stopping heart beat checking
```java
// take it offline
pool.offline();
```

For CRaC afterRestore() we can bring the pool online
creating the min number of connections and re-starting heart beat checking
```java
pool.online();
```

For explicit shutdown of the pool
```java
pool.shutdown();
```

### Robust and fast

This pool is robust in terms of handling loss of connectivity to the database and restoring connectivity.
It will automatically reset itself as needed.

This pool is fast and simple. It uses a strategy of testing connections in the background and when connections
are returned to the pool that have throw SQLException. This makes the connection testing strategy low overhead
but also robust.


### Read-Only Connection Pools

For read-only use cases (analytics, reporting, caching, microservices that only query), create a
separate read-only pool configured with `readOnly(true)` and `autoCommit(true)`. This optimizes
the connection pool specifically for read-only workloads:

```java
DataSourcePool readOnlyPool = DataSourcePool.builder()
  .name("mypool-readonly")
  .url("jdbc:postgresql://read-replica.example.com:5432/myapp")
  .username("readonly_user")
  .password("pass")
  .readOnly(true)
  .autoCommit(true)
  .minConnections(5)
  .maxConnections(30)
  .build();
```

**Benefits of read-only pools:**

- **Database optimization:** Read-only mode signals the JDBC driver and database that no transactions
  will be written. This allows the database to optimize query execution and skip transaction overhead.

- **Reduced resource usage:** `autoCommit(true)` eliminates the overhead of managing explicit transaction
  boundaries for each query. The database doesn't need to maintain transaction state for read-only operations.

- **Lower latency:** Queries execute faster without transaction coordination overhead, improving response
  times for read-heavy workloads.

- **Separation of concerns:** Using a dedicated read-only pool makes the intent of your code clear and
  allows you to configure connection pooling independently from your write pool.

- **Better scaling:** Read-only pools can often use different connection sizing, timeouts, and validation
  strategies optimized specifically for query operations.

**Common read-only scenarios:**

- Analytics and reporting engines querying large datasets
- Microservices that only read from shared databases
- Caching layers (e.g., warm-up queries for distributed caches)
- Read replicas in multi-region deployments
- Background workers that periodically fetch reference data


### Kubernetes / Container Deployment

When deploying to Kubernetes or other container orchestration platforms, configure `initialConnections`
in addition to `minConnections` and `maxConnections`:

```java
DataSourcePool pool = DataSourcePool.builder()
  .name("mypool")
  .url("jdbc:postgresql://db.example.com:5432/myapp")
  .username("user")
  .password("pass")
  .minConnections(5)
  .initialConnections(20)      // Start with sufficient connections for production load
  .maxConnections(50)           // Upper bound during peak usage
  .build();
```

**Why this matters:**

- **Rapid production readiness:** When a new pod is deployed, it immediately serves production traffic.
  Setting `initialConnections` higher than `minConnections` (typically between min and max) ensures
  the pod can handle incoming requests without the cold-start overhead of creating many connections.

- **Automatic scaling down:** The pool continuously trims unused connections in the background
  (default trim frequency is 59 seconds). Over time, the pool naturally shrinks back to a
  sustainable size as demand normalizes, so you don't waste resources.

- **Prevent connection storms:** Without adequate initial connections, new deployments spike
  database load by creating many new connections simultaneously to service incoming requests.

**Configuration strategies:**

**Low-traffic services:**
```java
.minConnections(2)
.initialConnections(5)
.maxConnections(20)
```

**Medium-traffic services:**
```java
.minConnections(5)
.initialConnections(20)
.maxConnections(50)
```

**High-traffic services:**
```java
.minConnections(10)
.initialConnections(40)
.maxConnections(100)
```

Start with these values as a baseline and adjust based on your application's observed connection usage
and deployment patterns. The pool will automatically trim idle connections over time, so starting with
more connections during deployment doesn't permanently increase resource consumption.


### AWS Lambda

The connection pool has built-in support for AWS Lambda. When running in Lambda, the pool
**automatically disables background thread validation** to optimize for cost and
resource efficiency.

**Automatic Detection:**

The pool detects Lambda environments by checking for the `LAMBDA_TASK_ROOT` environment variable (set by
AWS Lambda runtime). When detected, `validateOnHeartbeat` is automatically set to `false`.

**What Changes:**

- **Background heartbeat thread is disabled:** Normally the pool validates connection health every 30 seconds
  in the background. This thread is skipped in Lambda to avoid unnecessary CPU costs.

- **Connection validation still works:** Dead or stale connections are still detected eagerly when they are
  returned to the pool or when you attempt to use them. This ensures the pool remains robust.

- **Why this matters:** Lambda functions are charged per millisecond of execution. Background threads consume
  CPU time even when the function is idle, directly increasing your Lambda costs. Serverless functions are
  ephemeral and short-lived, making background threads less useful anyway.

**Configuration for Lambda:**

Keep connection pools lean in Lambda since functions are short-lived and scale horizontally:

```java
DataSourcePool pool = DataSourcePool.builder()
  .name("mypool")
  .url("jdbc:postgresql://db.example.com:5432/myapp")
  .username("user")
  .password("pass")
  .minConnections(1)           // Minimal baseline
  .initialConnections(2)       // Only what's needed for one invocation
  .maxConnections(10)          // Rarely needed; horizontal scaling handles load
  .build();
```



**Important notes:**

- Connection pooling in Lambda is **per-Lambda instance**, not per-invocation. A connection pool
  persists across warm invocations of the same Lambda container.

- Always minimize `minConnections` to reduce startup time and resource usage during cold starts.

- The pool is still fully robust and reliable in Lambda despite the disabled background heartbeat.


### Mature

This pool has been is heavy use for more that 10 years and stable since April 2010 (the last major refactor to use  `java.util.concurrent.locks`).

This pool was previously part of Ebean ORM with prior history in sourceforge.

There are other good DataSource pools out there but this pool has proven to be fast, simple and robust and maintains it's status as the preferred pool for use with Ebean ORM.


### Java modules use

```java
module my.example {

  requires io.ebean.datasource;
  ...

}

```
