package io.ebean.datasource;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * Listener for notifications about the DataSource such as when the DataSource
 * goes down, up or gets close to it's maximum size.
 * <p>
 * The intention is to send email notifications to an administrator (or similar)
 * when these events occur on the DataSource.
 * </p>
 */
public interface DataSourceAlert {

  /**
   * Send an alert to say the dataSource is back up.
   */
  void dataSourceUp(DataSource dataSource);

  /**
   * Send an alert to say the dataSource is down.
   */
  void dataSourceDown(DataSource dataSource, SQLException reason);

}
