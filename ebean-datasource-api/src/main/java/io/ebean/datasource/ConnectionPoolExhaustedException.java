package io.ebean.datasource;

import java.sql.SQLException;

/**
 * This exception is thrown, if the connection pool has reached maxSize.
 * @author Roland Praml, Foconis Analytics GmbH
 */
public class ConnectionPoolExhaustedException extends SQLException {
  public ConnectionPoolExhaustedException(String reason) {
    super(reason);
  }
}
