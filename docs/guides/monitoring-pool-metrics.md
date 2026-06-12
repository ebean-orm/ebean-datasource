# Guide: Monitoring Pool Metrics

## Purpose

This guide explains how to observe the health of a `DataSourcePool` at runtime: reading pool metrics
via `PoolStatus`, checking online/up state, and receiving outage notifications via `DataSourceAlert`.

---

## Reading pool metrics with `PoolStatus`

Call `status(reset)` on the pool to get a point-in-time snapshot:

```java
PoolStatus status = pool.status(false);   // false = read without resetting counters

System.out.printf(
  "min=%d max=%d free=%d busy=%d waiting=%d highWaterMark=%d hitCount=%d waitCount=%d%n",
  status.minSize(), status.maxSize(), status.free(), status.busy(),
  status.waiting(), status.highWaterMark(), status.hitCount(), status.waitCount());
```

### Metric meanings

| Metric | Meaning |
|--------|---------|
| `minSize()` | Configured minimum connections. |
| `maxSize()` | Configured maximum connections. |
| `free()` | Connections currently idle and available. |
| `busy()` | Connections currently checked out (in use). |
| `size()` | `free() + busy()` — total connections in the pool. |
| `waiting()` | Threads currently blocked waiting for a connection. |
| `highWaterMark()` | Peak `busy()` value seen since the last reset. |
| `hitCount()` | Number of times a connection was requested from the pool. |
| `waitCount()` | Number of times a thread had to wait (pool was full). |
| `totalAcquireMicros()` | Total time spent acquiring connections (micros). |
| `totalWaitMicros()` | Total time threads spent waiting when the pool was full (micros). |
| `maxAcquireMicros()` | Slowest single acquire (micros). |
| `meanAcquireNanos()` | Mean acquire time (nanos) — typically in the ballpark of ~150 nanos. |

### What to watch for

- **`highWaterMark()` approaching `maxSize()`** — the pool is close to exhaustion; consider raising
  `maxConnections` or investigate a leak (see
  [Troubleshooting Connection Leaks & Pool Exhaustion](troubleshooting-connection-leaks.md)).
- **Non-zero / rising `waitCount()` and `waiting()`** — threads are blocking on the pool; demand
  exceeds capacity.
- **`busy()` not dropping when traffic falls** — a likely connection leak.
- **High `maxAcquireMicros()` / `meanAcquireNanos()`** — acquiring is slow, often a sign of
  contention or repeated validation/recreation.

### Resetting counters for periodic sampling

Pass `true` to reset the cumulative counters (`highWaterMark`, `hitCount`, `waitCount`, acquire/wait
times) after reading. This is ideal for emitting metrics on a fixed interval:

```java
// every 60s, e.g. from a scheduled task
PoolStatus status = pool.status(true);   // read and reset, so the next window starts clean
meterRegistry.gauge("db.pool.busy", status.busy());
meterRegistry.gauge("db.pool.free", status.free());
meterRegistry.gauge("db.pool.highWaterMark", status.highWaterMark());
meterRegistry.gauge("db.pool.waitCount", status.waitCount());
```

---

## Checking pool state

```java
pool.isOnline();        // true once the pool has been brought online (connections created)
pool.isDataSourceUp();  // false if the pool has detected the database is down
pool.size();            // current number of connections (free + busy)

SQLException reason = pool.dataSourceDownReason();  // non-null when the pool is down
```

- `isOnline()` reflects the lifecycle state (`online()` / `offline()`).
- `isDataSourceUp()` reflects connectivity health detected by the heartbeat.

---

## Outage notifications with `DataSourceAlert`

Register a `DataSourceAlert` to be notified when the pool detects the database has gone down and when
it recovers. This is useful for wiring pool health into your alerting/paging system.

```java
DataSourceAlert alert = new DataSourceAlert() {
  @Override
  public void dataSourceDown(DataSource dataSource, SQLException reason) {
    // e.g. send to your alerting system / increment a metric
    log.error("Database pool is DOWN", reason);
  }

  @Override
  public void dataSourceUp(DataSource dataSource) {
    log.warn("Database pool is back UP");
  }
};

DataSourcePool pool = DataSourcePool.builder()
  .name("mypool")
  // ... url/username/password ...
  .alert(alert)
  .build();
```

The pool also logs these transitions itself:

```
FATAL: DataSource [mypool] is down or has network error!!!
RESOLVED FATAL: DataSource [mypool] is back up!
```

---

## Borrow/return hooks with `DataSourcePoolListener`

For per-connection observation (e.g. timing, tracing, auditing), register a listener:

```java
DataSourcePoolListener listener = new DataSourcePoolListener() {
  @Override
  public void onAfterBorrowConnection(Connection connection) {
    // called when a connection is handed to the application
  }

  @Override
  public void onBeforeReturnConnection(Connection connection) {
    // called just before a connection is returned to the pool
  }
};

DataSourcePool pool = DataSourcePool.builder()
  .name("mypool")
  // ...
  .listener(listener)
  .build();
```

> Keep listener callbacks fast — they run on the borrow/return path of every connection.

---

## Putting it together: a simple health check

```java
PoolStatus status = pool.status(false);
boolean healthy =
  pool.isDataSourceUp()
  && status.waiting() == 0
  && status.busy() < status.maxSize();

if (!healthy) {
  log.warn("DB pool degraded: up={} busy={}/{} waiting={}",
    pool.isDataSourceUp(), status.busy(), status.maxSize(), status.waiting());
}
```

---

## Next Steps

- [Troubleshooting Connection Leaks & Pool Exhaustion](troubleshooting-connection-leaks.md)
- [Configuration Reference](configuration-reference.md)
- [Connection Validation Best Practices](connection-validation-best-practices.md)
