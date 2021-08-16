package io.ebean.datasource.pool;

import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

class ConnectionDelegator implements Connection {

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

  public Statement createStatement() throws SQLException {
    return delegate.createStatement();
  }

  public PreparedStatement prepareStatement(String sql) throws SQLException {
    return delegate.prepareStatement(sql);
  }

  public CallableStatement prepareCall(String sql) throws SQLException {
    return delegate.prepareCall(sql);
  }

  public String nativeSQL(String sql) throws SQLException {
    return delegate.nativeSQL(sql);
  }

  public void setAutoCommit(boolean autoCommit) throws SQLException {
    delegate.setAutoCommit(autoCommit);
  }

  public boolean getAutoCommit() throws SQLException {
    return delegate.getAutoCommit();
  }

  public void commit() throws SQLException {
    delegate.commit();
  }

  public void rollback() throws SQLException {
    delegate.rollback();
  }

  public void close() throws SQLException {
    delegate.close();
  }

  public boolean isClosed() throws SQLException {
    return delegate.isClosed();
  }

  public DatabaseMetaData getMetaData() throws SQLException {
    return delegate.getMetaData();
  }

  public void setReadOnly(boolean readOnly) throws SQLException {
    delegate.setReadOnly(readOnly);
  }

  public boolean isReadOnly() throws SQLException {
    return delegate.isReadOnly();
  }

  public void setCatalog(String catalog) throws SQLException {
    delegate.setCatalog(catalog);
  }

  public String getCatalog() throws SQLException {
    return delegate.getCatalog();
  }

  public void setTransactionIsolation(int level) throws SQLException {
    delegate.setTransactionIsolation(level);
  }

  public int getTransactionIsolation() throws SQLException {
    return delegate.getTransactionIsolation();
  }

  public SQLWarning getWarnings() throws SQLException {
    return delegate.getWarnings();
  }

  public void clearWarnings() throws SQLException {
    delegate.clearWarnings();
  }

  public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
    return delegate.createStatement(resultSetType, resultSetConcurrency);
  }

  public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
      throws SQLException {
    return delegate.prepareStatement(sql, resultSetType, resultSetConcurrency);
  }

  public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency)
      throws SQLException {
    return delegate.prepareCall(sql, resultSetType, resultSetConcurrency);
  }

  public Map<String, Class<?>> getTypeMap() throws SQLException {
    return delegate.getTypeMap();
  }

  public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
    delegate.setTypeMap(map);
  }

  public void setHoldability(int holdability) throws SQLException {
    delegate.setHoldability(holdability);
  }

  public int getHoldability() throws SQLException {
    return delegate.getHoldability();
  }

  public Savepoint setSavepoint() throws SQLException {
    return delegate.setSavepoint();
  }

  public Savepoint setSavepoint(String name) throws SQLException {
    return delegate.setSavepoint(name);
  }

  public void rollback(Savepoint savepoint) throws SQLException {
    delegate.rollback(savepoint);
  }

  public void releaseSavepoint(Savepoint savepoint) throws SQLException {
    delegate.releaseSavepoint(savepoint);
  }

  public Statement createStatement(int resultSetType, int resultSetConcurrency,
      int resultSetHoldability) throws SQLException {
    return delegate.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
  }

  public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
    return delegate.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
  }

  public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency,
      int resultSetHoldability) throws SQLException {
    return delegate.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
  }

  public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
    return delegate.prepareStatement(sql, autoGeneratedKeys);
  }

  public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
    return delegate.prepareStatement(sql, columnIndexes);
  }

  public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
    return delegate.prepareStatement(sql, columnNames);
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

  public final <T> T unwrap(Class<T> iface) throws SQLException {
    if (iface.equals(java.sql.Connection.class)) {
      return (T)delegate;
    }
    return delegate.unwrap(iface);
  }

  public final boolean isWrapperFor(Class<?> iface) throws SQLException {
    return delegate.isWrapperFor(iface);
  }
}
