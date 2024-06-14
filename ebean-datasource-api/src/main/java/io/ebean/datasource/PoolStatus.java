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
   * Return the busy connection high water mark.
   */
  int highWaterMark();

  /**
   * Return the number of times threads had to wait for connections.
   */
  int waitCount();

  /**
   * Return the hit count against the pool.
   */
  int hitCount();

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
