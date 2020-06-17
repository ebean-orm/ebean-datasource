package io.ebean.datasource;

/**
 * Exception that can be thrown when the datasource fails to be created.
 */
public class DataSourceInitialiseException extends RuntimeException {

  public DataSourceInitialiseException(String message) {
    super(message);
  }

  public DataSourceInitialiseException(String message, Throwable e) {
    super(message, e);
  }

}
