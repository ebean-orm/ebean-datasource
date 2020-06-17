package io.ebean.datasource;

import io.ebean.datasource.pool.ConnectionPool;

/**
 * Factory that creates DataSourcePool's.
 *
 * <pre>{@code
 *
 *     DataSourceConfig config = new DataSourceConfig();
 *     config.setDriver("org.h2.Driver");
 *     config.setUrl("jdbc:h2:mem:tests2");
 *     config.setUsername("sa");
 *     config.setPassword("");
 *
 *     DataSourcePool pool = DataSourceFactory.create("test", config);
 *
 *     Connection connection = pool.getConnection();
 *
 * }</pre>
 */
public class DataSourceFactory {

  /**
   * Create the DataSourcePool given the name and configuration.
   */
  public static DataSourcePool create(String name, DataSourceConfig config) {
    return new ConnectionPool(name, config);
  }
}
