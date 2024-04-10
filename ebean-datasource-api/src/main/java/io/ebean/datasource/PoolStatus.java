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
   * Deprecated migrate to minSize()
   */
  @Deprecated
  default int getMinSize() {
    return minSize();
  }

  /**
   * Return the pools maximum size.
   */
  int maxSize();

  /**
   * Deprecated migrate to maxSize()
   */
  @Deprecated
  default int getMaxSize() {
    return maxSize();
  }

  /**
   * Return number of free connections.
   */
  int free();

  /**
   * Deprecated migrate to free()
   */
  @Deprecated
  default int getFree() {
    return free();
  }

  /**
   * Return number of busy connections.
   */
  int busy();

  /**
   * Deprecated migrate to busy()
   */
  @Deprecated
  default int getBusy() {
    return busy();
  }

  /**
   * Return the number of threads waiting for connections.
   */
  int waiting();

  /**
   * Deprecated migrate to waiting()
   */
  @Deprecated
  default int getWaiting() {
    return waiting();
  }

  /**
   * Return the busy connection high water mark.
   */
  int highWaterMark();

  /**
   * Deprecated migrate to waiting()
   */
  @Deprecated
  default int getHighWaterMark() {
    return highWaterMark();
  }

  /**
   * Return the number of times threads had to wait for connections.
   */
  int waitCount();

  /**
   * Deprecated migrate to waitCount()
   */
  @Deprecated
  default int getWaitCount() {
    return waitCount();
  }

  /**
   * Return the hit count against the pool.
   */
  int hitCount();

  /**
   * Deprecated migrate to hitCount()
   */
  @Deprecated
  default int getHitCount() {
    return hitCount();
  }

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
