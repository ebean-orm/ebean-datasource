# Guide: AWS Aurora with Dual DataSources (Read-Write and Read-Only)

## Purpose

This guide provides step-by-step instructions for configuring ebean-datasource with AWS Aurora using two separate DataSource pools: one for read-write operations (primary endpoint) and one for read-only operations (reader endpoint). This pattern is commonly used to offload read traffic from the primary database.

---

## Prerequisites

- AWS Aurora database cluster (MySQL or PostgreSQL compatible)
- Two Aurora endpoints available:
  - **Write endpoint:** For INSERT, UPDATE, DELETE, SELECT and transaction operations
  - **Reader endpoint:** For SELECT queries (read replica endpoint)
- A Java project with Maven
- `io.ebean:ebean-datasource` dependency already added
- Ebean ORM configured for secondary datasource support
- Database user credentials for both endpoints (can be same user if both endpoints support it)

---

## What is the Aurora Reader Endpoint?

AWS Aurora provides two types of endpoints:

- **Write endpoint (Cluster endpoint):** `mydb-cluster.123456789.us-east-1.rds.amazonaws.com`
  - Routes all write operations to the primary database instance
  - Can handle both reads and writes

- **Reader endpoint (Read-only endpoint):** `mydb-cluster-ro.123456789.us-east-1.rds.amazonaws.com`
  - Automatically distributes read operations across all read replicas
  - Cannot handle write operations
  - Better performance for read-heavy workloads

---

## Scenario: Setup Dual DataSources for Aurora

### Step 1 — Create the read-write (primary) DataSource

This pool connects to Aurora's write endpoint and handles all write operations.

```java
DataSourcePool writePool = DataSourcePool.builder()
  .name("datasource-write")
  .url("jdbc:mysql://mydb-cluster.123456789.us-east-1.rds.amazonaws.com:3306/myapp")
  .username(System.getenv("DB_USERNAME"))
  .password(System.getenv("DB_PASSWORD"))
  .minConnections(3)
  .initialConnections(5)
  .maxConnections(20)
  .build();
```

### Step 2 — Create the read-only DataSource

This pool connects to Aurora's reader endpoint and optimizes for read-only queries.

```java
DataSourcePool readOnlyPool = DataSourcePool.builder()
  .name("datasource-read")
  .url("jdbc:mysql://mydb-cluster-ro.123456789.us-east-1.rds.amazonaws.com:3306/myapp")
  .username(System.getenv("DB_USERNAME"))
  .password(System.getenv("DB_PASSWORD"))
  .readOnly(true)           // Signal read-only mode
  .autoCommit(true)         // Skip transaction overhead
  .minConnections(5)
  .initialConnections(10)
  .maxConnections(50)
  .build();
```

### Step 3 — Configure Ebean with dual datasources

When using Ebean ORM, configure the secondary (read-only) datasource. Ebean will automatically route read queries to the secondary datasource.

```java
Database database = DatabaseBuilder.create()
  .datasource(writePool)          // Primary write datasource
  .secondaryDatasource(readOnlyPool)  // Secondary read-only datasource
  .name("db")
  .build();
```

Or with dependency injection (Avaje):

```java
@Factory
public class DatabaseFactory {

  private DataSourceBuider dataSourceBuider() {
    var username = System.getenv("DB_USERNAME");
    var password = System.getenv("DB_PASSWORD");

    return DataSourcePool.builder()
      .username(username)
      .password(password);
  }

  @Bean
  public Database database() {

    var writePool = dataSourceBuider()
      .name("datasource-write")
      .url("jdbc:mysql://mydb-cluster.123456789.us-east-1.rds.amazonaws.com:3306/myapp")
      .minConnections(3)
      .initialConnections(5)
      .maxConnections(20)
      .build();

    var readOnlyPool = dataSourceBuider()
      .name("datasource-read")
      .url("jdbc:mysql://mydb-cluster-ro.123456789.us-east-1.rds.amazonaws.com:3306/myapp")
      .readOnly(true)
      .autoCommit(true)
      .minConnections(5)
      .initialConnections(10)
      .maxConnections(50)
      .build();

    return DatabaseBuilder.create()
      .name("db")
      .datasource(writePool)
      .readonlyDatasource(readOnlyPool)
      .build();
  }
}
```

---

## How Ebean Routes Queries

Once configured with a secondary datasource, Ebean automatically routes queries:

- **Write operations** → Primary DataSource (write endpoint):
  - `db.insert(...)`
  - `db.update(...)`
  - `db.delete(...)`
  - `db.execute()` (for arbitrary SQL updates)
  - Transactions with writes

- **Read operations** → Secondary DataSource (read endpoint):
  - `db.find(User.class).findList()`
  - `db.find(User.class).findOne()`
  - `db.sqlQuery("SELECT ...").findList()`
  - Read-only queries

---

## Configuration Best Practices

### Connection Sizing for Dual DataSources

**Write Pool (Primary Endpoint):**
```java
.minConnections(3)              // Fewer needed - mostly from read pool
.initialConnections(5)
.maxConnections(20)
```

**Read Pool (Reader Endpoint):**
```java
.minConnections(5)              // More connections for read traffic
.initialConnections(10)
.maxConnections(50)
```

The read pool can have more connections because the reader endpoint is designed to handle distributed read load.

### Using Environment Variables or external configuration

Store database endpoints in environment variables or external configuration (like avaje config):

```java
String writeUrl = System.getenv("DB_WRITE_ENDPOINT");
String readUrl = System.getenv("DB_READ_ENDPOINT");
String user = System.getenv("DB_USERNAME");
String pass = System.getenv("DB_PASSWORD");

DataSourcePool writePool = DataSourcePool.builder()
  .name("datasource-write")
  .url(writeUrl)
  .username(user)
  .password(pass)
  .minConnections(3)
  .initialConnections(5)
  .maxConnections(20)
  .build();

DataSourcePool readOnlyPool = DataSourcePool.builder()
  .name("datasource-read")
  .url(readUrl)
  .username(user)
  .password(pass)
  .readOnly(true)
  .autoCommit(true)
  .minConnections(5)
  .initialConnections(10)
  .maxConnections(50)
  .build();
```


---

## Aurora Failover Behavior

### Write Endpoint Failover

If the primary instance fails, Aurora automatically promotes a read replica to be the new primary:

- Aurora updates the write endpoint to point to the new primary
- The reader endpoint continues to distribute reads across remaining read replicas
- Your application's connection pools will eventually connect to the new primary
- ebean-datasource will reset when it detects the failover


### Reader Endpoint Failover

If a read replica fails:

- Aurora automatically removes it from the reader endpoint pool
- Other read replicas continue serving read traffic
- No action needed from your application

---

## Monitoring and Metrics

### Connection Pool Metrics

Use the connection pool's built-in metrics to monitor pool health:

```java
ConnectionPoolStatistics stats = writePool.getStatus();
System.out.println("Write Pool - Size: " + stats.size());

ConnectionPoolStatistics readStats = readOnlyPool.getStatus();
System.out.println("Read Pool - Size: " + readStats.size());
```

### AWS CloudWatch Metrics

Monitor Aurora metrics in CloudWatch:
- `DatabaseConnections` - Current connections
- `DatabaseCPUUtilization` - CPU usage
- `ReadLatency` / `WriteLatency` - Query latency
- `Connections` - Connection count per instance

---

## Common Issues and Solutions

### Issue: Read queries still hitting the write endpoint

**Cause:** Explicit transactions or transaction context from the write pool.

**Solution:** Ensure read-only queries are executed outside of transaction context:

```java
// This uses the read pool
List<User> users = db.find(User.class).findList();

// This uses the write pool (even for reads within a transaction)
try (var transaction = db.beginTransaction()) {
  List<User> users = db.find(User.class).findList();  // Still uses write pool
  // ... write operations
  transaction.commit();
}
```

### Issue: Latency between write and read

**Cause:** Aurora replication lag - read replicas may not have the latest data immediately.

**Solution:** For critical reads after writes, use the write pool:

```java
// Write on primary
db.insert(user);

// Read on primary to ensure latest data
User updated = db.find(User.class)
  .usingMaster(true)  // Force primary datasource
  .where().idEq(user.getId())
  .findOne();
```

### Issue: Too many connections on read pool

**Cause:** Pool not trimming idle connections or application creating excessive load.

**Solution:** Monitor connection usage and adjust `maxConnections` down:

```java
.maxConnections(30)  // Reduce from 50
.trimPoolFreqSecs(30)  // Trim more aggressively if needed
```


---

## Next Steps

- Read the main [README](../../README.md) for general pool configuration options
- See [Creating a DataSource Pool](create-datasource-pool.md) for basic pool setup
- Check the [Ebean ORM documentation](https://ebean.io) for secondary datasource configuration
- Review [AWS Aurora documentation](https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/) for cluster and endpoint details
