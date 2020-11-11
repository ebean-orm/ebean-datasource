package io.ebean.datasource;

/**
 * Current status of the DataSourcePool.
 */
public interface PoolStatus {

  /**
   * Return the pools minimum size.
   */
  int getMinSize();

  /**
   * Return the pools maximum size.
   */
  int getMaxSize();

  /**
   * Return number of free connections.
   */
  int getFree();

  /**
   * Return number of busy connections.
   */
  int getBusy();

  /**
   * Return the number of threads waiting for connections.
   */
  int getWaiting();

  /**
   * Return the busy connection high water mark.
   */
  int getHighWaterMark();

  /**
   * Return the number of times threads had to wait for connections.
   */
  int getWaitCount();

  /**
   * Return the hit count against the pool.
   */
  int getHitCount();
}
