package io.ebean.datasource;

import java.util.ServiceLoader;

/**
 * Internal helper to obtain and hold the DataSourceFactory implementation.
 */
final class DSManager {

  private static final DataSourceFactory factory = init();
  private static final NewConnectionInitializer defaultInitializer = initConnectionInitializer();

  private static DataSourceFactory init() {
    return ServiceLoader.load(DataSourceFactory.class)
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("No DataSourceFactory, add ebean-datasource to the classpath."));
  }

  private static NewConnectionInitializer initConnectionInitializer() {
    return ServiceLoader.load(NewConnectionInitializer.class)
      .findFirst()
      .orElse(null);
  }

  static DataSourceFactory get() {
    return factory;
  }

  static NewConnectionInitializer defaultInitializer() {
    return defaultInitializer;
  }
}
