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
   * Called after a connection has been retrieved from the connection pool
   */
  default void onAfterBorrowConnection(Connection connection) {}

  /**
   * Called before a connection will be put back to the connection pool
   */
  default void onBeforeReturnConnection(Connection connection) {}


}
