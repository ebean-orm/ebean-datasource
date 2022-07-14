package io.ebean.datasource;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * DataSource pool API.
 */
public interface DataSourcePool extends DataSource {

  /**
   * Return the dataSource name.
   */
  String name();

  /**
   * Deprecated migate to name().
   */
  @Deprecated
  default String getName() {
    return name();
  }

  /**
   * Return the current size of the pool. This includes both busy and idle connections.
   */
  int size();

  /**
   * Return true if the pool defaults to using autocommit.
   */
  boolean isAutoCommit();

  /**
   * Return true if the DataSource is online.
   * <p>
   * Effectively the same as (synonym for) {@link #isDataSourceUp()}.
   */
  boolean isOnline();

  /**
   * Returns false when the dataSource is down.
   * <p>
   * Effectively the same as (synonym for) {@link #isOnline()}.
   */
  boolean isDataSourceUp();

  /**
   * Bring the DataSource online ensuring min connections and start heart beat checking.
   */
  void online() throws SQLException;

  /**
   * Take the DataSource offline closing all connections and stopping heart beat checking.
   */
  void offline();

  /**
   * Shutdown the pool.
   * <p>
   * This is functionally the same as {@link #offline()} but generally we expect to only
   * shutdown the pool once whereas we can expect to make many calls to offline() and
   * online().
   */
  void shutdown();

  /**
   * Return the current status of the connection pool.
   * <p>
   * This is cheaper than getStatistics() in that it just the counts of free, busy,
   * wait etc and does not included times (total connection time etc).
   * <p>
   * If you pass reset = true then the counters are reset.
   */
  PoolStatus status(boolean reset);

  /**
   * Deprecated migrate to status().
   */
  @Deprecated
  default PoolStatus getStatus(boolean reset) {
    return status(reset);
  }

  /**
   * Returns the reason, why the dataSource is down.
   */
  SQLException dataSourceDownReason();

  /**
   * Deprecated migrate to dataSourceDownReason().
   */
  @Deprecated
  default SQLException getDataSourceDownReason() {
    return dataSourceDownReason();
  }

  /**
   * Set a new maximum size. The pool should respect this new maximum
   * immediately and not require a restart. We may want to increase the
   * maxConnections if the pool gets large and hits the warning level.
   */
  void setMaxSize(int max);

  /**
   * Set a new maximum size. The pool should respect this new warning level immediately
   * and not require a restart. We may want to increase the maxConnections if the
   * pool gets large and hits the warning levels.
   */
  void setWarningSize(int warningSize);

  /**
   * Return the warning size. When the pool hits this size it can send a
   * warning message to an administrator.
   */
  int getWarningSize();

}
