package io.ebean.datasource.pool;

import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourceFactory;
import io.ebean.datasource.DataSourcePool;

/**
 * DataSourceFactory implementation that is service loaded.
 */
public final class ConnectionPoolFactory implements DataSourceFactory {

  @Override
  public DataSourcePool createPool(String name, DataSourceConfig config) {
    var pool = new ConnectionPool(name, config);
    return config.useLambdaCheck() ? new LambdaPool(pool) : pool;
  }
}
