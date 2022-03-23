package io.ebean.datasource.test;

import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourceFactory;
import io.ebean.datasource.DataSourcePool;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;

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
