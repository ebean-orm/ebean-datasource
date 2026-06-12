# Guide: Troubleshooting Connection Leaks & Pool Exhaustion

## Purpose

This guide helps you diagnose and fix the most common connection pool problems:

- `ConnectionPoolExhaustedException` — no free connections available
- Connection leaks — connections obtained but never returned (not closed)
- Related warnings in the logs and how to interpret them

---

## Symptom 1: `ConnectionPoolExhaustedException`

You see an exception like:

```
Unsuccessfully waited [1000] millis for a connection to be returned. No connections are free.
You need to Increase the max connections of [50] or look for a connection pool leak using
datasource.xxx.capturestacktrace=true
```

### What it means

Every connection in the pool is **busy** (checked out), the pool is already at `maxConnections`, and
a thread waited `waitTimeoutMillis` (default 1000ms) without one being returned.

There are two root causes:

1. **Undersized pool** — legitimate concurrent demand exceeds `maxConnections`.
2. **Connection leak** — code obtains connections but does not close them, so they are never
   returned to the pool. This is the more common cause and the one to rule out first.

### Step 1 — Confirm whether it is a leak or genuine load

Check the pool metrics (see [Monitoring Pool Metrics](monitoring-pool-metrics.md)):

```java
PoolStatus status = pool.status(false);
System.out.printf("busy=%d free=%d waiting=%d highWaterMark=%d max=%d%n",
  status.busy(), status.free(), status.waiting(), status.highWaterMark(), status.maxSize());
```

- If `busy` is pinned at `maxSize` and stays there even when traffic drops → likely a **leak**.
- If `busy` only spikes to max under load and recovers afterwards → likely **undersized**.

### Step 2 — If it's a leak, capture stack traces

Enable `captureStackTrace` so the pool records where each connection was obtained. When the pool is
exhausted, it dumps the busy connections including their acquiring stack trace:

```java
DataSourcePool pool = DataSourcePool.builder()
  .name("mypool")
  // ... url/username/password ...
  .captureStackTrace(true)        // records the obtain stack trace
  .maxStackTraceSize(20)          // number of frames to report (default 5)
  .build();
```

Or via properties:

```properties
datasource.mypool.captureStackTrace=true
datasource.mypool.maxStackTraceSize=20
```

The log will then contain entries like:

```
Dumping [50] busy connections: (Use datasource.xxx.captureStackTrace=true ... to get stackTraces)
Busy Connection - name[mypool1] startTime[...] busySeconds[742] stackTrace[...] stmt[select ...]
```

The `stackTrace` points at the code path that obtained the connection and never closed it, and
`stmt` shows the last statement it ran. `busySeconds` shows how long it has been checked out.

> `captureStackTrace` has a performance cost (it captures a stack trace on every `getConnection()`),
> so enable it to diagnose, then turn it off again.

### Step 3 — Fix the leak

Always close connections (and statements / result sets) in a `try-with-resources` block:

```java
try (Connection connection = pool.getConnection()) {
  try (PreparedStatement stmt = connection.prepareStatement("select ...")) {
    // ...
  }
} // connection is returned to the pool here, even on exception
```

Common leak causes:

- A `Connection` stored in a field or returned from a method and never closed.
- An exception thrown between `getConnection()` and `close()` without a `try`/finally or
  try-with-resources.
- Closing the `PreparedStatement` / `ResultSet` but forgetting the `Connection`.

### Step 4 — If it's genuine load, tune sizing

```java
.maxConnections(100)        // raise the ceiling
.waitTimeoutMillis(3000)    // allow longer waits before failing (optional)
```

Confirm the database can support the connection count, and review `highWaterMark` over time to size
appropriately. See [Configuration Reference](configuration-reference.md).

---

## Symptom 2: leak self-recovery and `leakTimeMinutes`

A busy connection that has been checked out longer than `leakTimeMinutes` (default 30) is treated as
a leak. Leaked busy connections are **force-closed when the pool resets**.

The recovery flow is automatic:

1. Leaks accumulate and the pool eventually exhausts.
2. The background heartbeat fails to obtain a connection. After
   `heartbeatMaxPoolExhaustedCount` (default 10) consecutive failures, the pool is **reset**.
3. On reset, busy connections older than `leakTimeMinutes` are force-closed and recreated, restoring
   service.

You will see logs similar to:

```
Closing busy connections using leakTimeMinutes 30
DataSource closing busy connection? name[mypool1] startTime[...] busySeconds[2105] stmt[...]
```

This is a safety net, **not** a substitute for fixing the leak. Tune `leakTimeMinutes` to be safely
longer than your longest legitimate transaction so that genuinely-busy connections are never
force-closed:

```java
.leakTimeMinutes(30)   // must exceed your longest expected query/transaction
```

---

## Symptom 3: `Connection [...] not found in BusyList?`

```
Connection [name[mypool1] startTime[...] busySeconds[0] ...] not found in BusyList?
```

### What it means

A connection was returned to the pool but was no longer registered in the busy list. This usually
indicates the connection had already been removed — for example it was force-closed as a suspected
leak (or during a pool reset) while still checked out, and is then returned by the application
afterwards.

### What to do

- If you also see leak/reset logs (Symptom 2), the underlying issue is a **leak** or a
  `leakTimeMinutes` that is shorter than your longest transaction — address those.
- A low `busySeconds` value in the warning indicates the connection was returned very soon after
  being checked out, which points at a reset closing in-use connections rather than a real leak —
  ensure `leakTimeMinutes` is not set too low.

---

## Symptom 4: `Tried to close a dirty connection`

```
Tried to close a dirty connection at <stacktrace>. See https://github.com/ebean-orm/ebean-datasource/issues/116 for details.
```

### What it means

A connection with `autoCommit=false` was closed while it still had uncommitted changes (no `commit()`
or `rollback()` was called). The pool rolls it back for you, but this is a programming error — the
intended transaction outcome is ambiguous.

### What to do

Always end transactions explicitly before closing:

```java
try (Connection connection = pool.getConnection()) {
  try {
    // ... do work ...
    connection.commit();
  } catch (Exception e) {
    connection.rollback();
    throw e;
  }
}
```

To turn this into a hard failure in tests/CI so leaks of transaction discipline are caught early:

```java
.enforceCleanClose(true)   // throws instead of just warning
```

This option has no effect on read-only or auto-commit connections.

---

## Quick reference

| Setting | Default | Use when |
|---------|---------|----------|
| `captureStackTrace` | `false` | Diagnosing where leaked connections were obtained. |
| `maxStackTraceSize` | `5` | Need more stack frames in the leak dump. |
| `leakTimeMinutes` | `30` | Tuning the leak force-close threshold (keep > longest transaction). |
| `waitTimeoutMillis` | `1000` | Tuning how long a thread waits before `ConnectionPoolExhaustedException`. |
| `maxConnections` | `200` | Pool genuinely undersized for concurrent load. |
| `enforceCleanClose` | `false` | Catch dirty-close programming errors (recommended in tests). |

---

## Next Steps

- [Monitoring Pool Metrics](monitoring-pool-metrics.md)
- [Configuration Reference](configuration-reference.md)
- [Connection Validation Best Practices](connection-validation-best-practices.md)
