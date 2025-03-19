package io.ebean.datasource.pool;

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
 */
final class ExtendedPreparedStatement extends ExtendedStatement implements PreparedStatement {

  private final String cacheKey;
  private boolean closed;

  /**
   * Create a wrapped PreparedStatement that can be cached.
   */
  ExtendedPreparedStatement(PooledConnection pooledConnection, PreparedStatement pstmt, String cacheKey) {
    super(pooledConnection, pstmt);
    this.cacheKey = cacheKey;
  }

  /**
   * Reset the internal state (closed flag) to be ready for use.
   */
  ExtendedPreparedStatement reset() {
    this.closed = false;
    return this;
  }

  /**
   * Return the key used to cache this on the Connection.
   */
  String cacheKey() {
    return cacheKey;
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
  @Override
  public void close() {
    if (closed) {
      // multiple calls to close, do nothing - not ideal but valid
      return;
    }
    closed = true;
    pooledConnection.returnPreparedStatement(this);
  }

  @Override
  public void addBatch() throws SQLException {
    try {
      delegate.addBatch();
    } catch (SQLException ex) {
      pooledConnection.markWithError(ex);
      throw ex;
    }
  }

  @Override
  public void clearParameters() throws SQLException {
    try {
      delegate.clearParameters();
    } catch (SQLException ex) {
      pooledConnection.markWithError(ex);
      throw ex;
    }
  }

  @Override
  public boolean execute() throws SQLException {
    try {
      return delegate.execute();
    } catch (SQLException ex) {
      pooledConnection.markWithError(ex);
      throw ex;
    }
  }

  @Override
  public ResultSet executeQuery() throws SQLException {
    try {
      return delegate.executeQuery();
    } catch (SQLException ex) {
      pooledConnection.markWithError(ex);
      throw ex;
    }
  }

  @Override
  public int executeUpdate() throws SQLException {
    try {
      return delegate.executeUpdate();
    } catch (SQLException ex) {
      pooledConnection.markWithError(ex);
      throw ex;
    }
  }

  @Override
  public ResultSetMetaData getMetaData() throws SQLException {
    try {
      return delegate.getMetaData();
    } catch (SQLException ex) {
      pooledConnection.markWithError(ex);
      throw ex;
    }
  }

  @Override
  public ParameterMetaData getParameterMetaData() throws SQLException {
    return delegate.getParameterMetaData();
  }

  @Override
  public void setArray(int i, Array x) throws SQLException {
    delegate.setArray(i, x);
  }

  @Override
  public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
    delegate.setAsciiStream(parameterIndex, x, length);
  }

  @Override
  public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
    delegate.setBigDecimal(parameterIndex, x);
  }

  @Override
  public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
    delegate.setBinaryStream(parameterIndex, x, length);
  }

  @Override
  public void setBlob(int i, Blob x) throws SQLException {
    delegate.setBlob(i, x);
  }

  @Override
  public void setBoolean(int parameterIndex, boolean x) throws SQLException {
    delegate.setBoolean(parameterIndex, x);
  }

  @Override
  public void setByte(int parameterIndex, byte x) throws SQLException {
    delegate.setByte(parameterIndex, x);
  }

  @Override
  public void setBytes(int parameterIndex, byte[] x) throws SQLException {
    delegate.setBytes(parameterIndex, x);
  }

  @Override
  public void setCharacterStream(int parameterIndex, Reader reader, int length)
    throws SQLException {
    delegate.setCharacterStream(parameterIndex, reader, length);
  }

  @Override
  public void setClob(int i, Clob x) throws SQLException {
    delegate.setClob(i, x);
  }

  @Override
  public void setDate(int parameterIndex, Date x) throws SQLException {
    delegate.setDate(parameterIndex, x);
  }

  @Override
  public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
    delegate.setDate(parameterIndex, x, cal);
  }

  @Override
  public void setDouble(int parameterIndex, double x) throws SQLException {
    delegate.setDouble(parameterIndex, x);
  }

  @Override
  public void setFloat(int parameterIndex, float x) throws SQLException {
    delegate.setFloat(parameterIndex, x);
  }

  @Override
  public void setInt(int parameterIndex, int x) throws SQLException {
    delegate.setInt(parameterIndex, x);
  }

  @Override
  public void setLong(int parameterIndex, long x) throws SQLException {
    delegate.setLong(parameterIndex, x);
  }

  @Override
  public void setNull(int parameterIndex, int sqlType) throws SQLException {
    delegate.setNull(parameterIndex, sqlType);
  }

  @Override
  public void setNull(int paramIndex, int sqlType, String typeName) throws SQLException {
    delegate.setNull(paramIndex, sqlType, typeName);
  }

  @Override
  public void setObject(int parameterIndex, Object x) throws SQLException {
    delegate.setObject(parameterIndex, x);
  }

  @Override
  public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
    delegate.setObject(parameterIndex, x, targetSqlType);
  }

  @Override
  public void setObject(int parameterIndex, Object x, int targetSqlType, int scale) throws SQLException {
    delegate.setObject(parameterIndex, x, targetSqlType, scale);
  }

  @Override
  public void setRef(int i, Ref x) throws SQLException {
    delegate.setRef(i, x);
  }

  @Override
  public void setShort(int parameterIndex, short x) throws SQLException {
    delegate.setShort(parameterIndex, x);
  }

  @Override
  public void setString(int parameterIndex, String x) throws SQLException {
    delegate.setString(parameterIndex, x);
  }

  @Override
  public void setTime(int parameterIndex, Time x) throws SQLException {
    delegate.setTime(parameterIndex, x);
  }

  @Override
  public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
    delegate.setTime(parameterIndex, x, cal);
  }

  @Override
  public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
    delegate.setTimestamp(parameterIndex, x);
  }

  @Override
  public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
    delegate.setTimestamp(parameterIndex, x, cal);
  }

  @Override
  @Deprecated
  public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
    delegate.setUnicodeStream(parameterIndex, x, length);
  }

  @Override
  public void setURL(int parameterIndex, URL x) throws SQLException {
    delegate.setURL(parameterIndex, x);
  }

}
