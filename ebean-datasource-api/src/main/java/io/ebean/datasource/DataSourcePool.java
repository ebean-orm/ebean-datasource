package io.ebean.datasource;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * DataSource pool API.
 *
 * <pre>{@code
 *
 *    DataSourcePool pool = DataSourcePool.builder()
 *      .setName("test")
 *      .setUrl("jdbc:h2:mem:tests")
 *      .setUsername("sa")
 *      .setPassword("")
 *      .build();
 *
 *   Connection connection = pool.getConnection();
 *
 * }</pre>
 */
public interface DataSourcePool extends DataSource {

  /**
   * Return a builder for the DataSourcePool.
   *
   * <pre>{@code
   *
   *    DataSourcePool pool = DataSourcePool.builder()
   *      .setName("test")
   *      .setUrl("jdbc:h2:mem:tests")
   *      .setUsername("sa")
   *      .setPassword("")
   *      .build();
   *
   *   Connection connection = pool.getConnection();
   *
   * }</pre>
   *
   */
  static DataSourceBuilder builder() {
    return new DataSourceConfig();
  }

  /**
   * Return the dataSource name.
   */
  String name();

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
   * This will close all the free connections, and then go into a wait loop,
   * waiting for the busy connections to be freed.
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
   * Returns the reason, why the dataSource is down.
   */
  SQLException dataSourceDownReason();

  /**
   * Set a new maximum size.
   * <p>
   * The pool will apply the new maximum and not require a restart.
   */
  void setMaxSize(int max);

}
