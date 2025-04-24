package io.ebean.datasource;

/**
 * Current status of the DataSourcePool.
 */
public interface PoolStatus {

  /**
   * Return the pools minimum size.
   */
  int minSize();

  /**
   * Return the pools maximum size.
   */
  int maxSize();

  /**
   * Return number of free connections.
   */
  int free();

  /**
   * Return number of busy connections.
   */
  int busy();

  /**
   * Return the number of threads waiting for connections.
   */
  int waiting();

  /**
   * Return the busy connection highwater mark.
   */
  int highWaterMark();

  /**
   * Return the number of times threads had to wait for connections.
   * <p>
   * This occurs when the pool is full and threads are waiting for a connection.
   */
  int waitCount();

  /**
   * Return the hit count against the pool.
   */
  int hitCount();

  /**
   * Return the total time acquiring a connection from the pool.
   */
  long totalAcquireMicros();

  /**
   * Return the total time waiting in micros for a free connection when the pool has hit maxConnections.
   * <p>
   * When the pool is full and threads are waiting for a connection, this is the total time spent waiting.
   */
  long totalWaitMicros();

  /**
   * Return the max acquire time in micros.
   */
  long maxAcquireMicros();

  /**
   * Return the mean acquire time in nanos.
   * <p>
   * This should be in the ballpark of 150 nanos.
   */
  long meanAcquireNanos();
}
