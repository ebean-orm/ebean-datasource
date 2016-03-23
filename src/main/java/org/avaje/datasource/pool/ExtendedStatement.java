package org.avaje.datasource.pool;

import org.avaje.datasource.delegate.PreparedStatementDelegator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Implements the Statement methods for ExtendedPreparedStatement.
 * <p>
 * PreparedStatements should always be used and the intention is that there
 * should be no use of Statement at all. The implementation here is generally
 * for the case where someone uses the Statement api on an ExtendedPreparedStatement.
 * </p>
 */
abstract class ExtendedStatement extends PreparedStatementDelegator {

  /**
   * The pooled connection this Statement belongs to.
   */
  final PooledConnection pooledConnection;

  /**
   * Create the ExtendedStatement for a given pooledConnection.
   */
  ExtendedStatement(PooledConnection pooledConnection, PreparedStatement delegate) {
    super(delegate);
    this.pooledConnection = pooledConnection;
  }

  /**
   * Put the statement back into the statement cache.
   */
  public abstract void close() throws SQLException;

  /**
   * Return the underlying connection.
   */
  public Connection getConnection() throws SQLException {
    try {
      return delegate.getConnection();
    } catch (SQLException e) {
      pooledConnection.markWithError();
      throw e;
    }
  }

  /**
   * Add the sql for batch execution.
   */
  public void addBatch(String sql) throws SQLException {
    try {
      pooledConnection.setLastStatement(sql);
      delegate.addBatch(sql);
    } catch (SQLException e) {
      pooledConnection.markWithError();
      throw e;
    }
  }

  /**
   * Execute the sql.
   */
  public boolean execute(String sql) throws SQLException {
    try {
      pooledConnection.setLastStatement(sql);
      return delegate.execute(sql);
    } catch (SQLException e) {
      pooledConnection.markWithError();
      throw e;
    }
  }

  /**
   * Execute the query.
   */
  public ResultSet executeQuery(String sql) throws SQLException {
    try {
      pooledConnection.setLastStatement(sql);
      return delegate.executeQuery(sql);
    } catch (SQLException e) {
      pooledConnection.markWithError();
      throw e;
    }
  }

  /**
   * Execute the dml sql.
   */
  public int executeUpdate(String sql) throws SQLException {
    try {
      pooledConnection.setLastStatement(sql);
      return delegate.executeUpdate(sql);
    } catch (SQLException e) {
      pooledConnection.markWithError();
      throw e;
    }
  }

}
