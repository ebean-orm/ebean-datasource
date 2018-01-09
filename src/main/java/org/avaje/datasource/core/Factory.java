package org.avaje.datasource.core;

import org.avaje.datasource.DataSourceConfig;
import org.avaje.datasource.DataSourceFactory;
import org.avaje.datasource.DataSourcePool;
import org.avaje.datasource.pool.ConnectionPool;

/**
 * Service factory implementation.
 */
public class Factory implements DataSourceFactory {

  @Override
  public DataSourcePool createPool(String name, DataSourceConfig config) {
    return new ConnectionPool(name, config);
  }
}
