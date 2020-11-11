package io.ebean.datasource;

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
public interface DataSourceFactory {

  /**
   * Create the DataSourcePool given the name and configuration.
   */
  static DataSourcePool create(String name, DataSourceConfig config) {
    return DSManager.get().createPool(name, config);
  }

  DataSourcePool createPool(String name, DataSourceConfig config);
}
