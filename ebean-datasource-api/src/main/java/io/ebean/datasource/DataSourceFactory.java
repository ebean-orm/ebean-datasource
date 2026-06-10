package io.ebean.datasource;

/**
 * Factory that creates DataSourcePool's.
 *
 * <pre>{@code
 *
 *     DataSourcePool pool = DataSourcePool.builder()
 *       .name("test")
 *       .url("jdbc:h2:mem:tests2")
 *       .username("sa")
 *       .password("")
 *       .build();
 *
 *     Connection connection = pool.getConnection();
 *
 * }</pre>
 */
public interface DataSourceFactory {

  /**
   * Create the DataSourcePool given the name and configuration.
   *
   * @deprecated Migrate to {@link DataSourcePool#builder()} and {@link DataSourceBuilder#build()}.
   * <pre>{@code
   *
   *     DataSourcePool pool = DataSourcePool.builder()
   *       .name("test")
   *       .url("jdbc:h2:mem:tests2")
   *       .username("sa")
   *       .password("")
   *       .build();
   *
   * }</pre>
   */
  @Deprecated
  static DataSourcePool create(String name, DataSourceConfig config) {
    config.defaultConnectionInitializer(DSManager.defaultInitializer());
    return DSManager.get().createPool(name, config);
  }

  DataSourcePool createPool(String name, DataSourceConfig config);
}
