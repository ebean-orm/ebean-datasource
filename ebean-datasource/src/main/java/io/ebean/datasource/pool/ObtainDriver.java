package io.ebean.datasource.pool;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;

final class ObtainDriver {

  /**
   * Return the jdbc Driver to use.
   */
  static Driver driver(String driver, String url) {
    if (driver != null && !driver.isEmpty()) {
      return initDriver(driver);
    }
    try {
      return DriverManager.getDriver(url);
    } catch (SQLException e) {
      throw new IllegalStateException("Unable to obtain Driver", e);
    }
  }

  private static Driver initDriver(String driver) {
    Class<?> driverClass = initDriverClass(driver);
    try {
      return Driver.class.cast(driverClass.getConstructor().newInstance());
    } catch (Throwable e) {
      throw new IllegalStateException("Problem loading Database Driver: " + driver, e);
    }
  }

  private static Class<?> initDriverClass(String driver) {
    try {
      ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
      if (contextLoader != null) {
        return Class.forName(driver, true, contextLoader);
      } else {
        return Class.forName(driver, true, ObtainDriver.class.getClassLoader());
      }
    } catch (Throwable e) {
      throw new IllegalStateException("Problem loading Database Driver: " + driver, e);
    }
  }
}
