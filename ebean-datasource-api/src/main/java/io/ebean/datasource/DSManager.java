package io.ebean.datasource;

import java.util.ServiceLoader;

/**
 * Internal helper to obtain and hold the DataSourceFactory implementation.
 */
final class DSManager {

  private static final DataSourceFactory factory = init();

  private static DataSourceFactory init() {
    return ServiceLoader.load(DataSourceFactory.class)
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("No DataSourceFactory, add ebean-datasource to the classpath."));
  }

  static DataSourceFactory get() {
    return factory;
  }
}
