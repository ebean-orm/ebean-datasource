package io.ebean.datasource;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.ArrayList;

public class FactoryTest {

  @Test
  public void createPool() throws Exception {

    DataSourceConfig config = new DataSourceConfig();
    config.setDriver("org.h2.Driver");
    config.setUrl("jdbc:h2:mem:tests");
    config.setUsername("sa");
    config.setPassword("");

    DataSourcePool pool = DataSourceFactory.create("test", config);

    try (Connection connection = pool.getConnection()) {
      try (PreparedStatement stmt = connection.prepareStatement("create table junk (acol varchar(10))")) {
        stmt.execute();
        connection.commit();
      }
    }
  }

  @Disabled
  @Test
  void readOnly2() throws Exception {
    DataSourceConfig config = new DataSourceConfig()
      //.setName("testReadOnly")
      .setUrl("jdbc:h2:mem:testReadOnly3")
      .setUsername("sa")
      .setPassword("")
      .setReadOnly(true)
      .setAutoCommit(true)
      .setHeartbeatSql("select 2")
      .setHeartbeatFreqSecs(4)
      .setTrimPoolFreqSecs(4)
      .setMaxInactiveTimeSecs(4);

    DataSourcePool pool = DataSourceFactory.create("testReadOnly", config);

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
  public void dataSourceFactory_get_createPool() throws Exception {


    DataSourceConfig config = new DataSourceConfig();
    config.setDriver("org.h2.Driver");
    config.setUrl("jdbc:h2:mem:tests2");
    config.setUsername("sa");
    config.setPassword("");

    DataSourcePool pool = DataSourceFactory.create("test", config);

    try (Connection connection = pool.getConnection()) {
      try (PreparedStatement stmt = connection.prepareStatement("create table junk (acol varchar(10))")) {
        stmt.execute();
        connection.commit();
      }
    }

  }
}
