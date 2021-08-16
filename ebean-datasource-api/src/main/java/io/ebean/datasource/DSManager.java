package io.ebean.datasource;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Internal helper to obtain and hold the DataSourceFactory implementation.
 */
final class DSManager {

  private static final DataSourceFactory factory = init();

  private static DataSourceFactory init() {
    Iterator<DataSourceFactory> loader = ServiceLoader.load(DataSourceFactory.class).iterator();
    if (loader.hasNext()) {
      return loader.next();
    }
    throw new IllegalStateException("No service implementation found for DataSourceFactory in the classpath, please add ebean-datasource to the classpath.");
  }

  static DataSourceFactory get() {
    return factory;
  }
}
