package io.ebean.datasource.test;

import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourcePool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
class FactoryTest {

  @Test
  void createPool() throws Exception {
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
  void readOnly() throws Exception {
    DataSourcePool pool = DataSourcePool.builder()
      .name("testReadOnly")
      .url("jdbc:h2:mem:testReadOnly")
      .username("sa")
      .password("")
      .readOnly(true)
      .autoCommit(true)
      .build();

    try (Connection connection = pool.getConnection()) {
      try (PreparedStatement stmt = connection.prepareStatement("create table junk (acol varchar(10))")) {
        stmt.execute();
        connection.commit();
      }
    }
    pool.shutdown();
  }

  @Test
  void staleValidate() throws Exception {
    DataSourcePool pool = DataSourcePool.builder()
      .name("staleValidate")
      .url("jdbc:h2:mem:heartbeatTrimOnly")
      .username("sa")
      .password("")
      .readOnly(true)
      .autoCommit(true)
      .heartbeatFreqSecs(1)
      .trimPoolFreqSecs(1)
      .maxInactiveTimeSecs(1)
      .validateOnHeartbeat(false)
      // .useLambdaCheck(true)
      .build();


    Thread.sleep(3000);
    Connection connection1 = pool.getConnection();
    connection1.close();
    System.out.println("done");
    Thread.sleep(2000);
    pool.shutdown();
  }

  // @Disabled
  @Test
  void heartbeatTrimOnly() throws Exception {
    DataSourcePool pool = DataSourcePool.builder()
      .name("heartbeatTrimOnly")
      .url("jdbc:h2:mem:heartbeatTrimOnly")
      .username("sa")
      .password("")
      .readOnly(true)
      .autoCommit(true)
      .validateOnHeartbeat(false)
      .heartbeatFreqSecs(1)
      .trimPoolFreqSecs(1)
      .maxInactiveTimeSecs(3)
      .build();

    try (Connection connection = pool.getConnection()) {
      try (PreparedStatement stmt = connection.prepareStatement("create table junk4 (acol varchar(10))")) {
        stmt.execute();
        connection.commit();
      }
    }
    List<Connection> connections = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      connections.add(pool.getConnection());
    }
    for (Connection connection : connections) {
      connection.close();
    }
    Thread.sleep(1000);
    List<Connection> connections2 = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      Connection c = pool.getConnection();
      connections2.add(c);
    }
    for (Connection connection : connections2) {
      try (PreparedStatement ps = connection.prepareStatement("select * from junk4")) {
        ps.execute();
      }
      connection.close();
    }
    for (int i = 0; i < 5; i++) {
      Thread.sleep(1000);
      System.out.println(".");
    }
    pool.shutdown();
  }

  @Disabled
  @Test
  void readOnly2() throws Exception {
    DataSourcePool pool = DataSourcePool.builder()
      .name("testReadOnly")
      .url("jdbc:h2:mem:testReadOnly3")
      .username("sa")
      .password("")
      .readOnly(true)
      .autoCommit(true)
      .heartbeatSql("select 2")
      .heartbeatFreqSecs(4)
      .trimPoolFreqSecs(4)
      .maxInactiveTimeSecs(4)
      .build();

    try (Connection connection = pool.getConnection()) {
      try (PreparedStatement stmt = connection.prepareStatement("create table junk4 (acol varchar(10))")) {
        stmt.execute();
        connection.commit();
      }
    }
    List<Connection> connections = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      connections.add(pool.getConnection());
    }
    for (Connection connection : connections) {
      connection.close();
    }
    for (int i = 0; i < 30; i++) {
      Thread.sleep(1000);
      System.out.println(".");
    }
    pool.shutdown();
  }

  @Test
  void dataSourceFactory_get_createPool() throws Exception {
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
  void testPreparedStatement() throws Exception {
    DataSourcePool pool = DataSourcePool.builder()
      .setUrl("jdbc:h2:mem:tests")
      .setUsername("sa")
      .setPassword("")
      .setName("test")
      .build();

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
      connection.rollback();
    }
  }
}
