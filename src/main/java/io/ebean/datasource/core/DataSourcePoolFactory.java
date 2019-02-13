package io.ebean.datasource.core;

import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourcePool;
import io.ebean.datasource.pool.ConnectionPool;

/**
 * Provides a static factory method to create a DataSourcePool.
 */
public class DataSourcePoolFactory {

  /**
   * Create a DataSourcePool given the name and configuration.
   */
  public static DataSourcePool create(String name, DataSourceConfig config) {
    return new ConnectionPool(name, config);
  }

  /**
   * Not allowed.
   */
  private DataSourcePoolFactory() {
  }
}
