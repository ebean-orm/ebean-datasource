package org.avaje.datasource;

import com.avaje.ebean.config.DataSourceConfig;
import com.avaje.ebeaninternal.server.lib.sql.DDataSourcePool;
import com.avaje.ebeaninternal.server.lib.sql.DataSourceAlert;
import com.avaje.ebeaninternal.server.lib.sql.DataSourcePool;
import com.avaje.ebeaninternal.server.lib.sql.DataSourcePoolListener;

/**
 * Service factory implementation.
 */
public class Factory implements DataSourceFactory {

  @Override
  public DataSourcePool createPool(String name, DataSourceConfig config, DataSourceAlert alert, DataSourcePoolListener listener) {
    return new DDataSourcePool(name, config, alert, listener);
  }
}
