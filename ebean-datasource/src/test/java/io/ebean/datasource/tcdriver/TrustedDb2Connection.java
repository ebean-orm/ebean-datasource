package io.ebean.datasource.tcdriver;

import com.ibm.db2.jcc.DB2Connection;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.ShardingKey;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * A Wrapper for DB2Connection that holds connection and cookie. TODO: May inherit from ConnectionDelegator later
 *
 * @author Noemi Praml, Foconis Analytics GmbH
 */
class TrustedDb2Connection implements Connection {

  private final DB2Connection delegate;

  private final byte[] cookie;
  private String user;
  private String password;

  TrustedDb2Connection(DB2Connection delegate, byte[] cookie) {
    this.delegate = delegate;
    this.cookie = cookie;
  }

  boolean switchUser(String user, String password) throws SQLException {
    if (!Objects.equals(user, this.user) || !Objects.equals(password, this.password)) {
      // reusing connection destroys all preparedStatements
      delegate.reuseDB2Connection(cookie, user, password, null, null, null, new Properties());
      this.user = user;
      this.password = password;
      return true;
    }
    return false;
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    if (iface == TrustedDb2Connection.class) {
      return true;
    }
    return delegate.isWrapperFor(iface);
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    if (iface == TrustedDb2Connection.class) {
      return (T) this;
    }
    return delegate.unwrap(iface);
  }

  @Override
  public void setShardingKey(ShardingKey shardingKey) throws SQLException {
    delegate.setShardingKey(shardingKey);
  }

  @Override
  public void setShardingKey(ShardingKey shardingKey, ShardingKey superShardingKey) throws SQLException {
    delegate.setShardingKey(shardingKey, superShardingKey);
  }

  @Override
  public boolean setShardingKeyIfValid(ShardingKey shardingKey, int timeout) throws SQLException {
    return delegate.setShardingKeyIfValid(shardingKey, timeout);
  }

  @Override
  public boolean setShardingKeyIfValid(ShardingKey shardingKey, ShardingKey superShardingKey, int timeout) throws SQLException {
    return delegate.setShardingKeyIfValid(shardingKey, superShardingKey, timeout);
  }

  @Override
  public void endRequest() throws SQLException {
    delegate.endRequest();
  }

  @Override
  public void beginRequest() throws SQLException {
    delegate.beginRequest();
  }

  @Override
  public int getNetworkTimeout() throws SQLException {
    return delegate.getNetworkTimeout();
  }

  @Override
  public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
    delegate.setNetworkTimeout(executor, milliseconds);
  }

  @Override
  public void abort(Executor executor) throws SQLException {
    delegate.abort(executor);
  }

  @Override
  public String getSchema() throws SQLException {
    return delegate.getSchema();
  }

  @Override
  public void setSchema(String schema) throws SQLException {
    delegate.setSchema(schema);
  }

  @Override
  public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
    return delegate.createStruct(typeName, attributes);
  }

  @Override
  public Properties getClientInfo() throws SQLException {
    return delegate.getClientInfo();
  }

  @Override
  public String getClientInfo(String name) throws SQLException {
    return delegate.getClientInfo(name);
  }

  @Override
  public void setClientInfo(Properties properties) throws SQLClientInfoException {
    delegate.setClientInfo(properties);
  }

  @Override
  public void setClientInfo(String name, String value) throws SQLClientInfoException {
    delegate.setClientInfo(name, value);
  }

  @Override
  public boolean isValid(int timeout) throws SQLException {
    return delegate.isValid(timeout);
  }

  @Override
  public SQLXML createSQLXML() throws SQLException {
    return delegate.createSQLXML();
  }

  @Override
  public NClob createNClob() throws SQLException {
    return delegate.createNClob();
  }

  @Override
  public Blob createBlob() throws SQLException {
    return delegate.createBlob();
  }

  @Override
  public Clob createClob() throws SQLException {
    return delegate.createClob();
  }

  @Override
  public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
    return delegate.prepareStatement(sql, columnNames);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
    return delegate.prepareStatement(sql, columnIndexes);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
    return delegate.prepareStatement(sql, autoGeneratedKeys);
  }

  @Override
  public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
    throws SQLException {
    return delegate.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
    throws SQLException {
    return delegate.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
  }

  @Override
  public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
    return delegate.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
  }

  @Override
  public void releaseSavepoint(Savepoint savepoint) throws SQLException {
    delegate.releaseSavepoint(savepoint);
  }

  @Override
  public void rollback(Savepoint savepoint) throws SQLException {
    delegate.rollback(savepoint);
  }

  @Override
  public Savepoint setSavepoint(String name) throws SQLException {
    return delegate.setSavepoint(name);
  }

  @Override
  public Savepoint setSavepoint() throws SQLException {
    return delegate.setSavepoint();
  }

  @Override
  public int getHoldability() throws SQLException {
    return delegate.getHoldability();
  }

  @Override
  public void setHoldability(int holdability) throws SQLException {
    delegate.setHoldability(holdability);
  }

  @Override
  public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
    delegate.setTypeMap(map);
  }

  @Override
  public Map<String, Class<?>> getTypeMap() throws SQLException {
    return delegate.getTypeMap();
  }

  @Override
  public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
    return delegate.prepareCall(sql, resultSetType, resultSetConcurrency);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
    return delegate.prepareStatement(sql, resultSetType, resultSetConcurrency);
  }

  @Override
  public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
    return delegate.createStatement(resultSetType, resultSetConcurrency);
  }

  @Override
  public void clearWarnings() throws SQLException {
    delegate.clearWarnings();
  }

  @Override
  public SQLWarning getWarnings() throws SQLException {
    return delegate.getWarnings();
  }

  @Override
  public int getTransactionIsolation() throws SQLException {
    return delegate.getTransactionIsolation();
  }

  @Override
  public void setTransactionIsolation(int level) throws SQLException {
    delegate.setTransactionIsolation(level);
  }

  @Override
  public String getCatalog() throws SQLException {
    return delegate.getCatalog();
  }

  @Override
  public void setCatalog(String catalog) throws SQLException {
    delegate.setCatalog(catalog);
  }

  @Override
  public boolean isReadOnly() throws SQLException {
    return delegate.isReadOnly();
  }

  @Override
  public void setReadOnly(boolean readOnly) throws SQLException {
    delegate.setReadOnly(readOnly);
  }

  @Override
  public DatabaseMetaData getMetaData() throws SQLException {
    return delegate.getMetaData();
  }

  @Override
  public boolean isClosed() throws SQLException {
    return delegate.isClosed();
  }

  @Override
  public void close() throws SQLException {
    delegate.close();
  }

  @Override
  public void rollback() throws SQLException {
    delegate.rollback();
  }

  @Override
  public void commit() throws SQLException {
    delegate.commit();
  }

  @Override
  public boolean getAutoCommit() throws SQLException {
    return delegate.getAutoCommit();
  }

  @Override
  public void setAutoCommit(boolean autoCommit) throws SQLException {
    delegate.setAutoCommit(autoCommit);
  }

  @Override
  public String nativeSQL(String sql) throws SQLException {
    return delegate.nativeSQL(sql);
  }

  @Override
  public CallableStatement prepareCall(String sql) throws SQLException {
    return delegate.prepareCall(sql);
  }

  @Override
  public PreparedStatement prepareStatement(String sql) throws SQLException {
    return delegate.prepareStatement(sql);
  }

  @Override
  public Statement createStatement() throws SQLException {
    return delegate.createStatement();
  }

  @Override
  public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
    return delegate.createArrayOf(typeName, elements);
  }

}
