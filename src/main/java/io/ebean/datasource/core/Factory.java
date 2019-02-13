package io.ebean.datasource.core;

import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourceFactory;
import io.ebean.datasource.DataSourcePool;
import io.ebean.datasource.pool.ConnectionPool;

/**
 * Service factory implementation.
 */
public class Factory implements DataSourceFactory {

  @Override
  public DataSourcePool createPool(String name, DataSourceConfig config) {
    return new ConnectionPool(name, config);
  }
}
