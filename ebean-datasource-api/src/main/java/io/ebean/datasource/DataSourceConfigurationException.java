package io.ebean.datasource;

/**
 * Exception that can be thrown when the datasource is not correctly configured.
 */
public class DataSourceConfigurationException extends RuntimeException {

  public DataSourceConfigurationException(String message) {
    super(message);
  }

  public DataSourceConfigurationException(String message, Throwable e) {
    super(message, e);
  }

}
