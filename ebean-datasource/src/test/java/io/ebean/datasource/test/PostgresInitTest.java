package io.ebean.datasource.test;

import io.ebean.datasource.DataSourceBuilder;
import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourceFactory;
import io.ebean.datasource.DataSourcePool;
import io.ebean.test.containers.PostgresContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresInitTest {

  private static PostgresContainer container;

  @BeforeAll
  static void before() {
    container = PostgresContainer.builder("15")
      .port(9999)
      .containerName("pool_test")
      .dbName("app")
      .user("db_owner")
      .build();

    container.startWithDropCreate();
  }

  @AfterAll
  static void after() {
    container.stopRemove();
  }

  @Test
  void test_with_clientInfo() throws SQLException {
    Properties clientInfo = new Properties();
    clientInfo.setProperty("ApplicationName", "my-app-name");
    // ClientUser and ClientHostname are not supported by Postgres
    // clientInfo.setProperty("ClientUser", "ci-user");
    // clientInfo.setProperty("ClientHostname", "ci-hostname");

    DataSourcePool pool = DataSourceBuilder.create()
      .setUrl("jdbc:postgresql://127.0.0.1:9999/app")
      // our application credentials (typically same as db and schema name with Postgres)
      .setUsername("app")
      .setPassword("app_pass")
      // database owner credentials used to create the "app" role as needed
      .setOwnerUsername("db_owner")
      .setOwnerPassword("test")
      .setClientInfo(clientInfo)
      .build();

    try {
      try (Connection connection = pool.getConnection()) {
        try (PreparedStatement statement = connection.prepareStatement("create table if not exists app.my_table (acol integer);")) {
          statement.execute();
        }
        connection.commit();
        try (PreparedStatement statement = connection.prepareStatement("select application_name from pg_stat_activity where usename = ?")) {
          statement.setString(1, "app");
          try (ResultSet rset = statement.executeQuery()) {
            while (rset.next()) {
              String applicationName = rset.getString(1);
              assertThat(applicationName).isEqualTo("my-app-name");
            }
          }
        }
      }
    } finally {
      pool.shutdown();
    }
  }

  @Test
  void test_with_applicationNameAndSchema() throws SQLException {
    DataSourceConfig ds = new DataSourceConfig();
    ds.setUrl("jdbc:postgresql://127.0.0.1:9999/app");
    ds.setSchema("fred");
    ds.setUsername("db_owner");
    ds.setPassword("test");
    ds.setApplicationName("my-application-name");

    DataSourcePool pool = DataSourceFactory.create("app", ds);
    try {
      try (Connection connection = pool.getConnection()) {
        setupTable(connection, "my_table");
        testConnectionWithSelect(connection, "select acol from app.my_table");
        connection.commit();

        try (PreparedStatement statement = connection.prepareStatement("select application_name from pg_stat_activity where usename = ?")) {
          statement.setString(1, "app");
          try (ResultSet rset = statement.executeQuery()) {
            while (rset.next()) {
              String applicationName = rset.getString(1);
              assertThat(applicationName).isEqualTo("my-application-name");
            }
          }
        }
      }
    } finally {
      pool.shutdown();
    }
  }

  @Test
  void test_password2() throws SQLException {
    DataSourceConfig ds = new DataSourceConfig();
    ds.setUrl("jdbc:postgresql://127.0.0.1:9999/app");
    ds.setSchema("fred");
    ds.setUsername("db_owner");
    ds.setPassword("test");
    ds.setPassword2("newRolledPassword");

    DataSourcePool pool = DataSourceFactory.create("app", ds);
    try {
      try (Connection connection0 = pool.getConnection()) {
        setupTable(connection0, "my_table2");
        testConnectionWithSelect(connection0, "select acol from app.my_table2");
        connection0.commit();
        try (Connection connection1 = pool.getConnection()) {
          testConnectionWithSelect(connection1, "select acol from app.my_table2");
          // change password
          try (PreparedStatement statement = connection0.prepareStatement("alter role db_owner with password 'newRolledPassword'")) {
            statement.execute();
            connection0.commit();
          }
          // existing connections still work
          testConnectionWithSelect(connection0, "select acol from app.my_table2");
          testConnectionWithSelect(connection1, "select acol from app.my_table2");

          // new connection triggers password switch
          try (Connection newConnection0 = pool.getConnection()) {
            testConnectionWithSelect(newConnection0, "select acol from app.my_table2");
            try (Connection newConnection1 = pool.getConnection()) {
              testConnectionWithSelect(newConnection1, "select acol from app.my_table2");
            }
          }
        }

        // a new pool switches immediately
        DataSourcePool pool2 = DataSourceFactory.create("app2", ds);
        try (var connP2_0 = pool2.getConnection()) {
          testConnectionWithSelect(connP2_0, "select acol from app.my_table2");
          try (var connP2_1 = pool2.getConnection()) {
            testConnectionWithSelect(connP2_1, "select acol from app.my_table2");
            try (var connP2_2 = pool2.getConnection()) {
              testConnectionWithSelect(connP2_2, "select acol from app.my_table2");
            }
          }
        }

        // reset the password back for other tests
        try (PreparedStatement statement = connection0.prepareStatement("alter role db_owner with password 'test'")) {
          statement.execute();
          connection0.commit();
        }
      }
    } finally {
      pool.shutdown();
    }
  }


  private static void setupTable(Connection connection, String tableName) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("create schema if not exists app;")) {
      statement.execute();
    }
    try (PreparedStatement statement = connection.prepareStatement("create table if not exists app." + tableName + " (acol integer);")) {
      statement.execute();
    }
    try (PreparedStatement statement = connection.prepareStatement("insert into app." + tableName + " (acol) values (?);")) {
      statement.setInt(1, 42);
      int rows = statement.executeUpdate();
      assertThat(rows).isEqualTo(1);
    }
  }

  private static void testConnectionWithSelect(Connection connection, String sql) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          int res = resultSet.getInt(1);
          assertThat(res).isEqualTo(42);
        }
      }
    }
  }
}
