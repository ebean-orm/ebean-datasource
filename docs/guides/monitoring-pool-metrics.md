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
| `free()` | Connections **currently** idle and available (point-in-time snapshot). |
| `busy()` | Connections **currently** checked out, at the instant `status()` is called (point-in-time snapshot — see note below). |
| `size()` | `free() + busy()` — total connections in the pool right now. |
| `waiting()` | Threads currently blocked waiting for a connection (point-in-time snapshot). |
| `highWaterMark()` | **Peak** `busy()` value seen since the last reset — the metric to use for "how busy was the pool". |
| `hitCount()` | Number of times a connection was requested from the pool. |
| `waitCount()` | Number of times a thread had to wait (pool was full). |
| `totalAcquireMicros()` | Total time spent acquiring connections (micros). |
| `totalWaitMicros()` | Total time threads spent waiting when the pool was full (micros). |
| `maxAcquireMicros()` | Slowest single acquire (micros). |
| `meanAcquireNanos()` | Mean acquire time (nanos) — typically in the ballpark of ~150 nanos. |

### Important: `busy()` is instantaneous, not a peak or average

`busy()`, `free()`, `size()` and `waiting()` are **point-in-time snapshots** — they reflect the pool
state at the exact moment you call `status()`, nothing more.

Most applications borrow a connection, run a quick query, and return it immediately, so the pool sits
idle between requests. If you sample on a fixed interval (say every 30–60s), those samples will
almost always land on idle moments and **`busy()` will read 0 even under real load**. This is
expected behaviour, not a bug.

> **To measure how busy the pool actually gets, use `highWaterMark()`** — the peak number of busy
> connections since the last reset. Sample it once per interval with `status(true)` (read-and-reset)
> and each window reports that window's true peak concurrency.

### Which metric should you actually monitor?

For most applications, **`size()` (`free() + busy()`) is the single most useful metric** — and it is
*not* misleading the way instantaneous `busy()` is. Here's why:

- The pool **grows on demand**: when a connection is requested and none is free (and `size < maxConnections`),
  it creates a new one. So `size()` rises immediately to meet real concurrent demand.
- The pool **trims slowly**: idle connections are only retired after `maxInactiveTimeSecs` (default 300s),
  checked on the `trimPoolFreqSecs` cycle, and never below `minConnections`. This deliberate laziness
  avoids connection churn.

The net effect is that **`size()` behaves like a slow-decaying high-water mark of demand**: it jumps up
with concurrency spikes and lingers, decaying gently back toward `minConnections` only after sustained
low usage. Sampled periodically, it gives a stable, representative picture of how large the pool needs
to be — without the "reads 0 most of the time" problem that instantaneous `busy()` / `free()` suffer
from.

Practical guidance:

- **Start with just `size()`** sampled at report time, watched against `maxConnections`. For many
  services this is all you need.
- Add **`highWaterMark()`** only if you want to catch *short concurrency spikes* that come and go faster
  than the pool trims, or want a crisp "peak busy vs `maxConnections`" exhaustion-margin number.
- **`busy()` / `free()` instantaneous** are of limited value for periodic sampling (they usually read
  near 0); treat them as debugging/ad-hoc values rather than dashboard staples.

### What to watch for

- **`size()` sitting at (or `highWaterMark()` approaching) `maxSize()`** — the pool is close to
  exhaustion; consider raising `maxConnections` or investigate a leak (see
  [Troubleshooting Connection Leaks & Pool Exhaustion](troubleshooting-connection-leaks.md)).
- **Non-zero / rising `waitCount()` and `waiting()`** — threads are blocking on the pool; demand
  exceeds capacity.
- **`size()` not decaying back toward `minConnections` long after traffic falls** — connections are
  not being returned to be trimmed; a likely connection leak.
- **High `maxAcquireMicros()` / `meanAcquireNanos()`** — acquiring is slow, often a sign of
  contention or repeated validation/recreation.

### Resetting counters for periodic sampling

Pass `true` to reset the cumulative counters (`highWaterMark`, `hitCount`, `waitCount`, acquire/wait
times) after reading. This is ideal for emitting metrics on a fixed interval:

```java
// every 60s, e.g. from a scheduled task
PoolStatus status = pool.status(true);   // read and reset, so the next window starts clean

// primary signal: total pool size (sticky high-water mark of demand) vs the ceiling
meterRegistry.gauge("db.pool.size", status.size());
meterRegistry.gauge("db.pool.max", status.maxSize());
// optional: crisp per-interval peak concurrency (spike detection / exhaustion margin)
meterRegistry.gauge("db.pool.peakBusy", status.highWaterMark());
// pressure indicators over the interval
meterRegistry.gauge("db.pool.waitCount", status.waitCount());
meterRegistry.gauge("db.pool.hitCount", status.hitCount());
// instantaneous values — usually near 0 when sampled; debugging only (see notes above)
// meterRegistry.gauge("db.pool.busyNow", status.busy());
// meterRegistry.gauge("db.pool.freeNow", status.free());
```

> Because `status(true)` resets the cumulative counters, call it from a **single** sampler. If
> multiple callers reset the same pool, each only sees the slice since the previous reset. Use
> `status(false)` for ad-hoc reads that must not disturb the interval counters.

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
FATAL: DataSource [mypool] is down or has network error!!!   (logged at ERROR)
Resolved Fatal: DataSource [mypool] is back up!              (logged at INFO)
```

The "down" transition is logged at `ERROR` and the recovery at `INFO` — deliberately, so that
returning to a healthy state does not itself trigger ERROR-level alerting. If you want to alert on
recovery, do it via the `dataSourceUp` callback above rather than by matching the log line.

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
