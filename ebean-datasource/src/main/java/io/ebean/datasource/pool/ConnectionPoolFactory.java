package io.ebean.datasource.pool;

import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourceFactory;
import io.ebean.datasource.DataSourcePool;

import static java.util.Objects.requireNonNullElse;

/**
 * DataSourceFactory implementation that is service loaded.
 */
public final class ConnectionPoolFactory implements DataSourceFactory {

  @Override
  public DataSourcePool createPool(String name, DataSourceConfig config) {
    return new ConnectionPool(requireNonNullElse(name, ""), config);
  }
}
