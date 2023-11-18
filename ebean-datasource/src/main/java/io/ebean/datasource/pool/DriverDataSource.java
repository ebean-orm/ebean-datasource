package io.ebean.datasource.pool;

import io.ebean.datasource.DataSourceBuilder;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

final class DriverDataSource implements DataSource {

  private final String name;
  private final Driver driver;
  private final String url;
  private final Properties connectionProps;
  private final String password2;
  private final ReentrantLock lock = new ReentrantLock();
  private boolean fixedCredentials;

  DriverDataSource(String name, Driver driver, String url, Properties properties, String password2) {
    this.name = name;
    this.driver = driver;
    this.url = url;
    this.connectionProps = properties;
    this.password2 = password2;
    this.fixedCredentials = password2 == null;
  }

  static DataSource of(String name, DataSourceBuilder.Settings builder) {
    final var dataSource = builder.dataSource();
    if (dataSource != null) {
      return dataSource;
    }
    final var connectionProps = builder.connectionProperties();
    final var driver = ObtainDriver.driver(builder.getDriver(), builder.getUrl());
    return new DriverDataSource(name, driver, builder.getUrl(), connectionProps, builder.getPassword2());
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    final var props = new Properties(connectionProps);
    props.setProperty("user", username);
    props.setProperty("password", password);
    return driver.connect(url, props);
  }

  @Override
  public Connection getConnection() throws SQLException {
    try {
      return driver.connect(url, connectionProps);
    } catch (SQLException e) {
      lock.lock();
      try {
        if (fixedCredentials) {
          throw e;
        }
        Log.debug("DataSource [{0}] trying alternate credentials due to {1}", name, e.getMessage());
        return switchCredentials(connectionProps);
      } finally {
        lock.unlock();
      }
    }
  }

  private Connection switchCredentials(Properties properties) throws SQLException {
    var copy = new Properties(properties);
    copy.setProperty("password", password2);
    var connection = driver.connect(url, copy);
    // success, permanently switch to use password2 from now on
    Log.info("DataSource [{0}] now using alternate credentials", name);
    fixedCredentials = true;
    properties.setProperty("password", password2);
    return connection;
  }

  @Override
  public PrintWriter getLogWriter() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setLogWriter(PrintWriter out) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setLoginTimeout(int seconds) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getLoginTimeout() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Logger getParentLogger() {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T unwrap(Class<T> iface) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) {
    throw new UnsupportedOperationException();
  }
}
