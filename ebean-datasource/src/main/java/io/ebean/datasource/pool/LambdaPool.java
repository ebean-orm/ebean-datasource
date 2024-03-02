package io.ebean.datasource.pool;

import io.ebean.datasource.DataSourcePool;
import io.ebean.datasource.PoolStatus;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * Pool designed for use with AWS Lambda that suspends.
 * The suspend stops background trimming of idle connections in the pool and
 * this can result in old idle connections when the Lambda is restored.
 * <p>
 * This pool performs an additional fast check that is used with every getConnection()
 * that checks if the pool has not been trimmed recently by the background timer. If
 * so that suggests the lambda has just being invoked after a restore and any old
 * idle connections need to be trimmed out of the pool first.
 */
final class LambdaPool implements DataSourcePool {

  private final ConnectionPool pool;

  LambdaPool(ConnectionPool pool) {
    this.pool = pool;
  }

  @Override
  public Connection getConnection() throws SQLException {
    pool.checkLambdaIdle();
    return pool.getConnection();
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    pool.checkLambdaIdle();
    return pool.getConnection(username, password);
  }

  // delegate only methods

  @Override
  public String name() {
    return pool.name();
  }

  @Override
  public int size() {
    return pool.size();
  }

  @Override
  public boolean isAutoCommit() {
    return pool.isAutoCommit();
  }

  @Override
  public boolean isOnline() {
    return pool.isOnline();
  }

  @Override
  public boolean isDataSourceUp() {
    return pool.isDataSourceUp();
  }

  @Override
  public void online() throws SQLException {
    pool.online();
  }

  @Override
  public void offline() {
    pool.offline();
  }

  @Override
  public void shutdown() {
    pool.shutdown();
  }

  @Override
  public PoolStatus status(boolean reset) {
    return pool.status(reset);
  }

  @Override
  public SQLException dataSourceDownReason() {
    return pool.dataSourceDownReason();
  }

  @Override
  public void setMaxSize(int max) {
    pool.setMaxSize(max);
  }

  @Override
  public void setWarningSize(int warningSize) {
    pool.setWarningSize(warningSize);
  }

  @Override
  public int getWarningSize() {
    return pool.getWarningSize();
  }

  @Override
  public PrintWriter getLogWriter() throws SQLException {
    return pool.getLogWriter();
  }

  @Override
  public void setLogWriter(PrintWriter out) throws SQLException {
    pool.setLogWriter(out);
  }

  @Override
  public void setLoginTimeout(int seconds) throws SQLException {
    pool.setLoginTimeout(seconds);
  }

  @Override
  public int getLoginTimeout() throws SQLException {
    return pool.getLoginTimeout();
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return pool.getParentLogger();
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    return pool.unwrap(iface);
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return pool.isWrapperFor(iface);
  }
}
