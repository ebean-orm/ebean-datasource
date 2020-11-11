package io.ebean.datasource;

/**
 * Service factory for creating DataSourceAlert implementation.
 */
public interface DataSourceAlertFactory {

  /**
   * Create a DataSourceAlert for notifications when DataSource down and up are detected.
   */
  DataSourceAlert createAlert();
}
