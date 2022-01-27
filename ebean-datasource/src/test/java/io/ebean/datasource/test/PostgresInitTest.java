package io.ebean.datasource.test;

import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourceFactory;
import io.ebean.datasource.DataSourcePool;
import io.ebean.docker.commands.PostgresConfig;
import io.ebean.docker.commands.PostgresContainer;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class PostgresInitTest {

  @Test
  public void test() throws SQLException {

    PostgresConfig dockerConfig = new PostgresConfig("12");
    dockerConfig.setPort(9999);
    dockerConfig.setContainerName("pool_test");
    dockerConfig.setDbName("app");
    // create database with owner as "db_owner"
    dockerConfig.setUser("db_owner");

    PostgresContainer container = new PostgresContainer(dockerConfig);
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
