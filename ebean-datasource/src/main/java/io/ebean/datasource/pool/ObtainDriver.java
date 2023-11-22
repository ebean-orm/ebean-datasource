package io.ebean.datasource.pool;

import io.ebean.datasource.DataSourceBuilder;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;

final class ObtainDriver {

  /**
   * Return the jdbc Driver to use.
   */
  static Driver driver(DataSourceBuilder.Settings builder, String url) {
    Driver driver = builder.driver();
    if (driver != null) {
      return driver;
    }
    Class<? extends Driver> driverClass = builder.driverClass();
    if (driverClass != null) {
      return initDriver(driverClass);
    }
    String driverClassName = builder.driverClassName();
    if (driverClassName != null && !driverClassName.isEmpty()) {
      return initDriver(driverClassName);
    }
    try {
      return DriverManager.getDriver(url);
    } catch (SQLException e) {
      throw new IllegalStateException("Unable to obtain Driver", e);
    }
  }

  private static Driver initDriver(String driverClassName) {
    try {
      return initDriver(initDriverClass(driverClassName));
    } catch (Throwable e) {
      throw new IllegalStateException("Problem loading Database Driver: " + driverClassName, e);
    }
  }

  private static Driver initDriver(Class<?> driverClass) {
    try {
      return (Driver) driverClass.getConstructor().newInstance();
    } catch (Throwable e) {
      throw new IllegalStateException("Problem loading Database Driver: " + driverClass, e);
    }
  }

  private static Class<?> initDriverClass(String driver) throws ClassNotFoundException {
    ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
    if (contextLoader != null) {
      return Class.forName(driver, true, contextLoader);
    } else {
      return Class.forName(driver, true, ObtainDriver.class.getClassLoader());
    }
  }
}
