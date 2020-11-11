package io.ebean.datasource.pool;

import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourceFactory;
import io.ebean.datasource.DataSourcePool;

/**
 * DataSourceFactory implementation that is service loaded.
 */
public class ConnectionPoolFactory implements DataSourceFactory {

  @Override
  public DataSourcePool createPool(String name, DataSourceConfig config) {
    return new ConnectionPool(name, config);
  }
}
