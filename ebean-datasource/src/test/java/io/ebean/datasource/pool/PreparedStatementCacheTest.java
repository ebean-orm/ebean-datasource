package io.ebean.datasource.pool;

import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourceConnection;
import io.ebean.datasource.DataSourcePool;
import io.ebean.datasource.DataSourcePoolListener;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.h2.Driver;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PreparedStatementCacheTest {

  private Connection activeConnection;

  private int tenant;

  /**
   * Nach dem Austausch der physischen Verbindung muss dieselbe SQL-Abfrage ein neues
   * PreparedStatement erhalten und den Wert aus der zweiten Datenbank liefern.
   */
  @Test
  void queryAfterPhysicalConnectionSwitch() throws SQLException {
    try (var first = DriverManager.getConnection("jdbc:h2:mem:preparedStatementCacheFirst");
         var second = DriverManager.getConnection("jdbc:h2:mem:preparedStatementCacheSecond")) {
      populate(first, 10);
      populate(second, 20);

      tenant = 1;
      activeConnection = first;

      var config = new DataSourceConfig()
        .setUsername("dummy")
        .setPassword("dummy")
        .setListener(createSwitchingListener(first, second))
        .setMinConnections(1)
        .setMaxConnections(1)
        .setAutoCommit(true)
        .setPstmtCacheSize(2)
        .validateOnHeartbeat(false);
      config.driver(createSwitchingDriver());
      var pool = new ConnectionPool("preparedStatementCache", config);
      Connection tmp;
      try {
        try (DataSourceConnection connection = pool.getConnection().unwrap(DataSourceConnection.class)) {
          assertThat(query(connection)).isEqualTo(10);
          tmp = connection;
        }

        try (DataSourceConnection connection = pool.getConnection().unwrap(DataSourceConnection.class)) {
          assertThat(query(connection)).isEqualTo(10);
          assertThat(connection).isSameAs(tmp);
        }

        tenant = 2;
        try (Connection connection = pool.getConnection()) {
          // If `preparedStatementCache` isn’t cleared here, the query returns `10`, a value from the wrong tenant!
          assertThat(query(connection)).isEqualTo(20);
          assertThat(connection).isSameAs(tmp);
        }

      } finally {
        pool.shutdown();
      }
    }
  }

  private @NonNull DataSourcePoolListener createSwitchingListener(Connection first, Connection second) {
    return new DataSourcePoolListener() {
      @Override
      public void onAfterBorrowConnection(DataSourcePool pool, DataSourceConnection connection) throws SQLException {
        switch (tenant) {
          case 1:
            if (activeConnection != first) {
              activeConnection = first;
              connection.clearPreparedStatementCache();
            }
            break;
          case 2:
            if (activeConnection != second) {
              activeConnection = second;
              connection.clearPreparedStatementCache();
            }
            break;
          default:
            throw new IllegalArgumentException(tenant + "is invalid");
        }
      }
    };
  }

  private @NonNull Driver createSwitchingDriver() {
    return new Driver() {
      @Override
      public Connection connect(String url, Properties info) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
          (proxy, method, args) -> {
            try {
              return method.invoke(activeConnection, args);
            } catch (InvocationTargetException e) {
              throw e.getCause();
            }
          });
      }
    };
  }

  private static void populate(Connection connection, int value) throws SQLException {
    try (var statement = connection.createStatement()) {
      statement.execute("create table test (id int primary key, test_value int)");
      statement.execute("insert into test values (1, " + value + ")");
    }
  }

  private static int query(Connection connection) throws SQLException {
    try (var statement = connection.prepareStatement("select test_value from test where id = ?")) {
      statement.setInt(1, 1);
      try (var result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        return result.getInt(1);
      }
    }
  }

  private static Connection forwardingConnection(AtomicReference<Connection> delegate) {
    return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
      (proxy, method, args) -> {
        try {
          return method.invoke(delegate.get(), args);
        } catch (InvocationTargetException e) {
          throw e.getCause();
        }
      });
  }
}
