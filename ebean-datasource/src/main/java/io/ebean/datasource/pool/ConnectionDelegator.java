package io.ebean.datasource.pool;

import java.sql.*;
import java.util.Properties;
import java.util.concurrent.Executor;

abstract class ConnectionDelegator implements Connection {

  private final Connection delegate;

  ConnectionDelegator(Connection delegate) {
    this.delegate = delegate;
  }

  /**
   * Return the underlying connection.
   */
  Connection delegate() {
    return delegate;
  }

  @Override
  public final String getSchema() throws SQLException {
    return delegate.getSchema();
  }

  @Override
  public final void abort(Executor executor) throws SQLException {
    delegate.abort(executor);
  }

  @Override
  public final void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
    delegate.setNetworkTimeout(executor, milliseconds);
  }

  @Override
  public final int getNetworkTimeout() throws SQLException {
    return delegate.getNetworkTimeout();
  }

  @Override
  public final Clob createClob() throws SQLException {
    return delegate.createClob();
  }

  @Override
  public final Blob createBlob() throws SQLException {
    return delegate.createBlob();
  }

  @Override
  public final NClob createNClob() throws SQLException {
    return delegate.createNClob();
  }

  @Override
  public final SQLXML createSQLXML() throws SQLException {
    return delegate.createSQLXML();
  }

  @Override
  public final boolean isValid(int timeout) throws SQLException {
    return delegate.isValid(timeout);
  }

  @Override
  public final void setClientInfo(String name, String value) throws SQLClientInfoException {
    delegate.setClientInfo(name, value);
  }

  @Override
  public final void setClientInfo(Properties properties) throws SQLClientInfoException {
    delegate.setClientInfo(properties);
  }

  @Override
  public final String getClientInfo(String name) throws SQLException {
    return delegate.getClientInfo(name);
  }

  @Override
  public final Properties getClientInfo() throws SQLException {
    return delegate.getClientInfo();
  }

  @Override
  public final Array createArrayOf(String typeName, Object[] elements) throws SQLException {
    return delegate.createArrayOf(typeName, elements);
  }

  @Override
  public final Struct createStruct(String typeName, Object[] attributes) throws SQLException {
    return delegate.createStruct(typeName, attributes);
  }

  @SuppressWarnings("unchecked")
  @Override
  public final <T> T unwrap(Class<T> iface) throws SQLException {
    if (iface.equals(java.sql.Connection.class)) {
      return (T) delegate;
    }
    return delegate.unwrap(iface);
  }

  @Override
  public final boolean isWrapperFor(Class<?> iface) throws SQLException {
    return delegate.isWrapperFor(iface);
  }
}
