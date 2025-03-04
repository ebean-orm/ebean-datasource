package io.ebean.datasource.pool;

/**
 * Used to return a stale connection, so that it can be closed outside a lock.
 *
 * @author Roland Praml, Foconis Analytics GmbH
 */
class StaleConnectionException extends Exception {
  private final PooledConnection connection;

  StaleConnectionException(PooledConnection connection) {
    this.connection = connection;
  }

  public PooledConnection getConnection() {
    return connection;
  }
}
