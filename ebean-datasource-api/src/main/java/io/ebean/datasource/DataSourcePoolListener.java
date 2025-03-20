package io.ebean.datasource;

import java.sql.Connection;
import java.sql.SQLException;


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
  default void onAfterBorrowConnection(DataSourcePool pool, DataSourceConnection connection) {
    onAfterBorrowConnection(connection);
  }

  /**
   * @deprecated implement {@link #onAfterBorrowConnection(DataSourcePool, DataSourceConnection)}
   */
  @Deprecated
  default void onAfterBorrowConnection(Connection connection) {}

  /**
   * Called before a connection will be put back to the connection pool
   */
  default void onBeforeReturnConnection(DataSourcePool pool, DataSourceConnection connection) {
    onBeforeReturnConnection(connection);
  }

  /**
   * @deprecated implement {@link #onBeforeReturnConnection(DataSourcePool, DataSourceConnection)}
   */
  @Deprecated
  default void onBeforeReturnConnection(Connection connection) {}


}
