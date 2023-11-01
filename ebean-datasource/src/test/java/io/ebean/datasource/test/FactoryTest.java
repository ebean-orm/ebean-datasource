package io.ebean.datasource.test;

import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourceFactory;
import io.ebean.datasource.DataSourcePool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
public class FactoryTest {

  @Test
  public void createPool() throws Exception {

    DataSourcePool pool = new DataSourceConfig()
      .setName("test")
      .setUrl("jdbc:h2:mem:tests")
      .setUsername("sa")
      .setPassword("")
      .build();

    try (Connection connection = pool.getConnection()) {
      try (PreparedStatement stmt = connection.prepareStatement("create table junk (acol varchar(10))")) {
        stmt.execute();
        connection.commit();
      }
    }
  }

  @Test
  public void dataSourceFactory_get_createPool() throws Exception {

    DataSourcePool pool = new DataSourceConfig()
      .setUrl("jdbc:h2:mem:tests2")
      .setUsername("sa")
      .setPassword("")
      .build();

    try (Connection connection = pool.getConnection()) {
      try (PreparedStatement stmt = connection.prepareStatement("create table junk (acol varchar(10))")) {
        stmt.execute();
        connection.commit();
      }
    }
  }

  @Test
  public void testPreparedStatement() throws Exception {

    DataSourceConfig config = new DataSourceConfig();
    config.setDriver("org.h2.Driver");
    config.setUrl("jdbc:h2:mem:tests");
    config.setUsername("sa");
    config.setPassword("");

    DataSourcePool pool = DataSourceFactory.create("test", config);
    String sql = "select * from information_schema.settings where setting_name != ?";
    try (Connection connection = pool.getConnection()) {
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
        stmt.executeQuery();
        Assertions.fail("Not expected");
      } catch (SQLException e) {
        // expected: Parameter "#1" not set
      }

      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
        stmt.setString(1, "foo");
        ResultSet rs = stmt.executeQuery();
        rs.close();
      }

      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
        stmt.executeQuery();
        Assertions.fail("Not expected");
      } catch (SQLException e) {
        // expected: Parameter "#1" not set
      }
    }
  }
}
