package io.ebean.datasource;

import java.sql.Connection;

/**
 * A {@link DataSourcePool} listener which allows you to hook on the create connections process of the pool.
 */
public interface NewConnectionInitializer {

  /**
   * Called after a connection has been created, before any initialization.
   *
   * @param connection the created connection
   */
  default void preInitialize(Connection connection) {
  }

  /**
   * Called after a connection has been initialized (after onCreatedConnection) and all settings applied.
   *
   * @param connection the created connection
   */
  default void postInitialize(Connection connection) {
  }

}
