package io.ebean.datasource.test;

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
    DataSourceConfig ds = new DataSourceConfig();
    ds.setUrl("jdbc:postgresql://127.0.0.1:9999/app");
    // our application credentials (typically same as db and schema name with Postgres)
    ds.setUsername("app");
    ds.setPassword("app_pass");
    // database owner credentials used to create the "app" role as needed
    ds.setOwnerUsername("db_owner");
    ds.setOwnerPassword("test");
    Properties clientInfo = new Properties();
    clientInfo.setProperty("ApplicationName", "my-app-name");
    // ClientUser and ClientHostname are not supported by Postgres
    // clientInfo.setProperty("ClientUser", "ci-user");
    // clientInfo.setProperty("ClientHostname", "ci-hostname");
    ds.setClientInfo(clientInfo);

    DataSourcePool pool = DataSourceFactory.create("app", ds);
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
        try (PreparedStatement statement = connection.prepareStatement("create schema if not exists fred;")) {
          statement.execute();
        }
        connection.commit();
        try (PreparedStatement statement = connection.prepareStatement("create table if not exists fred_table (acol integer);")) {
          statement.execute();
        }
        try (PreparedStatement statement = connection.prepareStatement("insert into fred_table (acol) values (?);")) {
          statement.setInt(1, 42);
          int rows = statement.executeUpdate();
          assertThat(rows).isEqualTo(1);
        }
        try (PreparedStatement statement = connection.prepareStatement("select acol from fred.fred_table")) {
          try (ResultSet resultSet = statement.executeQuery()) {
            while(resultSet.next()) {
              int res = resultSet.getInt(1);
              assertThat(res).isEqualTo(42);
            }
          }
        }
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
}
