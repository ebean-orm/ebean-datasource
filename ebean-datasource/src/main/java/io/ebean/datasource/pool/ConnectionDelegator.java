package io.ebean.datasource.pool;

import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

abstract class ConnectionDelegator implements Connection {

  private final Connection delegate;
  protected String currentSchema;

  ConnectionDelegator(Connection delegate) {
    this.delegate = delegate;
  }

  /**
   * Return the underlying connection.
   */
  Connection getDelegate() {
    return delegate;
  }

  @Override
  public final void setSchema(String schema) throws SQLException {
    delegate.setSchema(schema);
    currentSchema = schema;
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

  public final Clob createClob() throws SQLException {
    return delegate.createClob();
  }

  public final Blob createBlob() throws SQLException {
    return delegate.createBlob();
  }

  public final NClob createNClob() throws SQLException {
    return delegate.createNClob();
  }

  public final SQLXML createSQLXML() throws SQLException {
    return delegate.createSQLXML();
  }

  public final boolean isValid(int timeout) throws SQLException {
    return delegate.isValid(timeout);
  }

  public final void setClientInfo(String name, String value) throws SQLClientInfoException {
    delegate.setClientInfo(name, value);
  }

  public final void setClientInfo(Properties properties) throws SQLClientInfoException {
    delegate.setClientInfo(properties);
  }

  public final String getClientInfo(String name) throws SQLException {
    return delegate.getClientInfo(name);
  }

  public final Properties getClientInfo() throws SQLException {
    return delegate.getClientInfo();
  }

  public final Array createArrayOf(String typeName, Object[] elements) throws SQLException {
    return delegate.createArrayOf(typeName, elements);
  }

  public final Struct createStruct(String typeName, Object[] attributes) throws SQLException {
    return delegate.createStruct(typeName, attributes);
  }

  @SuppressWarnings("unchecked")
  public final <T> T unwrap(Class<T> iface) throws SQLException {
    if (iface.equals(java.sql.Connection.class)) {
      return (T) delegate;
    }
    return delegate.unwrap(iface);
  }

  public final boolean isWrapperFor(Class<?> iface) throws SQLException {
    return delegate.isWrapperFor(iface);
  }
}
