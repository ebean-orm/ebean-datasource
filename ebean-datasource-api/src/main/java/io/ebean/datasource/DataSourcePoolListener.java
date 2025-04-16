package io.ebean.datasource;

import java.sql.Connection;


/**
 * A {@link DataSourcePool} listener which allows you to hook on the
 * borrow/return process of getting or returning connections from the pool.
 * <p>
 * In the configuration use the poolListener key to configure which listener to
 * use.
 * <p>
 * Example: datasource.ora10.poolListener=my.very.fancy.PoolListener
 */
public interface DataSourcePoolListener {

  /**
   * Called before a connection has been created
   */
  default void onBeforeCreateConnection(DataSourcePool pool) {}

  /**
   * Called after a connection has been created
   */
  default void onAfterCreateConnection(DataSourcePool pool, Connection connection) {}

  /**
   * Called before a connection has been retrieved from the connection pool
   */
  default void onBeforeBorrowConnection(DataSourcePool pool) {}

  /**
   * Called after a connection has been retrieved from the connection pool
   */
  default void onAfterBorrowConnection(DataSourcePool pool, Connection connection) {onAfterBorrowConnection(connection);}

  /**
   * Called after a connection has been retrieved from the connection pool.
   *
   * Migrate to <code>onAfterBorrowConnection(DataSourcePool pool, Connection connection)</code>
   */
  @Deprecated
  default void onAfterBorrowConnection(Connection connection) {}

  /**
   * Called before a connection will be put back to the connection pool
   */
  default void onBeforeReturnConnection(DataSourcePool pool, Connection connection) {onBeforeReturnConnection(connection);}

  /**
   * Called before a connection will be put back to the connection pool.
   *
   * Migrate to <code>onBeforeReturnConnection(DataSourcePool pool, Connection connection)</code>
   */
  @Deprecated
  default void onBeforeReturnConnection(Connection connection) {}

  /**
   * Called after a connection will be put back to the connection pool
   */
  default void onAfterReturnConnection(DataSourcePool pool) {}

  /**
   * Called before a connection has been closed
   */
  default void onBeforeCloseConnection(DataSourcePool pool, Connection connection) {}

  /**
   * Called after a connection had been closed
   */
  default void onAfterCloseConnection(DataSourcePool pool) {}
}
