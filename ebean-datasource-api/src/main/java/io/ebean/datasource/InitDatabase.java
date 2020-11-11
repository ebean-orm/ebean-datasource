package io.ebean.datasource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Run database initialisation when a pool can't create the initial connections (user may not exist).
 */
public interface InitDatabase {

  /**
   * Execute some database initialisation statements on a database where the user may not exist.
   *
   * @param connection Connection obtained using the ownerUsername and ownerPassword.
   * @param config     The datasource configuration.
   */
  void run(Connection connection, DataSourceConfig config) throws SQLException;
}
