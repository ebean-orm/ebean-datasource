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
   * Deprecated migrate to name().
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
   * shut down the pool once whereas we can expect to make many calls to offline() and
   * online().
   */
  void shutdown();

  /**
   * Return the current status of the connection pool.
   * <p>
   * With reset true, the counters are reset.
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
   * Set a new maximum size.
   * <p>
   * The pool will apply the new maximum and not require a restart.
   */
  void setMaxSize(int max);

  /**
   * Deprecated - looking to remove.
   * <p>
   * Set a new maximum size. The pool should respect this new warning level immediately
   * and not require a restart. We may want to increase the maxConnections if the
   * pool gets large and hits the warning levels.
   */
  @Deprecated
  void setWarningSize(int warningSize);

  /**
   * Deprecated - looking to remove.
   * <p>
   * Return the warning size. When the pool hits this size it can send a
   * warning message to an administrator.
   */
  @Deprecated
  int getWarningSize();

}
