package io.ebean.datasource.test;

import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourceFactory;
import io.ebean.datasource.DataSourcePool;
import io.ebean.test.containers.PostgresContainer;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

class PostgresInitTest {

  @Test
  void test() throws SQLException {
    PostgresContainer container = PostgresContainer.builder("12")
      .port(9999)
      .containerName("pool_test")
      .dbName("app")
      .user("db_owner")
      .build();
    try {
      container.startWithDropCreate();

      DataSourceConfig ds = new DataSourceConfig();
      ds.setUrl("jdbc:postgresql://127.0.0.1:9999/app");

      // our application credentials (typically same as db and schema name with Postgres)
      ds.setUsername("app");
      ds.setPassword("app_pass");

      // database owner credentials used to create the "app" role as needed
      ds.setOwnerUsername("db_owner");
      ds.setOwnerPassword("test");

      DataSourcePool pool = DataSourceFactory.create("app", ds);

      try (Connection connection = pool.getConnection()) {
        try (PreparedStatement statement = connection.prepareStatement("create table my_table (acol integer);")) {
          statement.execute();
        }
        connection.commit();
      }

    } finally {
      container.stopRemove();
    }
  }
}
