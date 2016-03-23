package org.avaje.datasource;

import org.avaje.datasource.pool.ConnectionPool;

/**
 * Service factory implementation.
 */
public class Factory implements DataSourceFactory {

  @Override
  public DataSourcePool createPool(String name, DataSourceConfig config, DataSourceAlert alert, DataSourcePoolListener listener) {
    return new ConnectionPool(name, config, alert, listener);
  }
}
