package org.avaje.datasource.pool;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;

/**
 * Extended PreparedStatement that supports caching.
 * <p>
 * Designed so that it can be cached by the PooledConnection. It additionally
 * notes any Exceptions that occur and this is used to ensure bad connections
 * are removed from the connection pool.
 * </p>
 */
class ExtendedPreparedStatement extends ExtendedStatement implements PreparedStatement {

  /**
   * The SQL used to create the underlying PreparedStatement.
   */
  private final String sql;

  /**
   * The key used to cache this in the connection.
   */
  private final String cacheKey;

  /**
   * Create a wrapped PreparedStatement that can be cached.
   */
  ExtendedPreparedStatement(PooledConnection pooledConnection, PreparedStatement pstmt, String sql, String cacheKey) {
    super(pooledConnection, pstmt);
    this.sql = sql;
    this.cacheKey = cacheKey;
  }

  /**
   * Return the key used to cache this on the Connection.
   */
  String getCacheKey() {
    return cacheKey;
  }

  /**
   * Return the SQL used to create this PreparedStatement.
   */
  public String getSql() {
    return sql;
  }

  /**
   * Fully close the underlying PreparedStatement. After this we can no longer
   * reuse the PreparedStatement.
   */
  void closeDestroy() throws SQLException {
    delegate.close();
  }

  /**
   * Returns the PreparedStatement back into the cache. This doesn't fully
   * close the underlying PreparedStatement.
   */
  public void close() throws SQLException {
    pooledConnection.returnPreparedStatement(this);
  }

  /**
   * Add the last binding for batch execution.
   */
  public void addBatch() throws SQLException {
    try {
      delegate.addBatch();
    } catch (SQLException e) {
      pooledConnection.markWithError();
      throw e;
    }
  }

  /**
   * Clear parameters.
   */
  public void clearParameters() throws SQLException {
    try {
      delegate.clearParameters();
    } catch (SQLException e) {
      pooledConnection.markWithError();
      throw e;
    }
  }

  /**
   * execute the statement.
   */
  public boolean execute() throws SQLException {
    try {
      return delegate.execute();
    } catch (SQLException e) {
      pooledConnection.markWithError();
      throw e;
    }
  }

  /**
   * Execute teh query.
   */
  public ResultSet executeQuery() throws SQLException {
    try {
      return delegate.executeQuery();
    } catch (SQLException e) {
      pooledConnection.markWithError();
      throw e;
    }
  }

  /**
   * Execute the dml statement.
   */
  public int executeUpdate() throws SQLException {
    try {
      return delegate.executeUpdate();
    } catch (SQLException e) {
      pooledConnection.markWithError();
      throw e;
    }
  }

  /**
   * Return the MetaData for the query.
   */
  public ResultSetMetaData getMetaData() throws SQLException {
    try {
      return delegate.getMetaData();
    } catch (SQLException e) {
      pooledConnection.markWithError();
      throw e;
    }
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public ParameterMetaData getParameterMetaData() throws SQLException {
    return delegate.getParameterMetaData();
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setArray(int i, Array x) throws SQLException {
    delegate.setArray(i, x);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
    delegate.setAsciiStream(parameterIndex, x, length);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
    delegate.setBigDecimal(parameterIndex, x);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
    delegate.setBinaryStream(parameterIndex, x, length);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setBlob(int i, Blob x) throws SQLException {
    delegate.setBlob(i, x);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setBoolean(int parameterIndex, boolean x) throws SQLException {
    delegate.setBoolean(parameterIndex, x);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setByte(int parameterIndex, byte x) throws SQLException {
    delegate.setByte(parameterIndex, x);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setBytes(int parameterIndex, byte[] x) throws SQLException {
    delegate.setBytes(parameterIndex, x);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setCharacterStream(int parameterIndex, Reader reader, int length)
      throws SQLException {
    delegate.setCharacterStream(parameterIndex, reader, length);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setClob(int i, Clob x) throws SQLException {
    delegate.setClob(i, x);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setDate(int parameterIndex, Date x) throws SQLException {
    delegate.setDate(parameterIndex, x);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
    delegate.setDate(parameterIndex, x, cal);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setDouble(int parameterIndex, double x) throws SQLException {
    delegate.setDouble(parameterIndex, x);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setFloat(int parameterIndex, float x) throws SQLException {
    delegate.setFloat(parameterIndex, x);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setInt(int parameterIndex, int x) throws SQLException {
    delegate.setInt(parameterIndex, x);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setLong(int parameterIndex, long x) throws SQLException {
    delegate.setLong(parameterIndex, x);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setNull(int parameterIndex, int sqlType) throws SQLException {
    delegate.setNull(parameterIndex, sqlType);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setNull(int paramIndex, int sqlType, String typeName) throws SQLException {
    delegate.setNull(paramIndex, sqlType, typeName);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setObject(int parameterIndex, Object x) throws SQLException {
    delegate.setObject(parameterIndex, x);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
    delegate.setObject(parameterIndex, x, targetSqlType);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setObject(int parameterIndex, Object x, int targetSqlType, int scale)
      throws SQLException {
    delegate.setObject(parameterIndex, x, targetSqlType, scale);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setRef(int i, Ref x) throws SQLException {
    delegate.setRef(i, x);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setShort(int parameterIndex, short x) throws SQLException {
    delegate.setShort(parameterIndex, x);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setString(int parameterIndex, String x) throws SQLException {
    delegate.setString(parameterIndex, x);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setTime(int parameterIndex, Time x) throws SQLException {
    delegate.setTime(parameterIndex, x);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
    delegate.setTime(parameterIndex, x, cal);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
    delegate.setTimestamp(parameterIndex, x);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
    delegate.setTimestamp(parameterIndex, x, cal);
  }

  /**
   * Standard PreparedStatement method execution.
   *
   * @deprecated
   */
  public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
    delegate.setUnicodeStream(parameterIndex, x, length);
  }

  /**
   * Standard PreparedStatement method execution.
   */
  public void setURL(int parameterIndex, URL x) throws SQLException {
    delegate.setURL(parameterIndex, x);
  }

}
