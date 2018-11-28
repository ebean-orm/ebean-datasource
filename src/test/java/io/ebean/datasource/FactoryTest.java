package io.ebean.datasource;

import io.ebean.datasource.core.Factory;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class FactoryTest {

  @Test
  public void createPool() throws Exception {

    Factory factory = new Factory();

    DataSourceConfig config = new DataSourceConfig();
    config.setDriver("org.h2.Driver");
    config.setUrl("jdbc:h2:mem:tests");
    config.setUsername("sa");
    config.setPassword("");

    DataSourcePool pool = factory.createPool("test", config);

    try (Connection connection = pool.getConnection()) {
      try (PreparedStatement stmt = connection.prepareStatement("create table junk (acol varchar(10))")) {
        stmt.execute();
        connection.commit();
      }
    }
  }

  @Test
  public void dataSourceFactory_get_createPool() throws Exception {

    DataSourceFactory factory = DataSourceFactory.get();

    DataSourceConfig config = new DataSourceConfig();
    config.setDriver("org.h2.Driver");
    config.setUrl("jdbc:h2:mem:tests2");
    config.setUsername("sa");
    config.setPassword("");

    DataSourcePool pool = factory.createPool("test", config);

    try (Connection connection = pool.getConnection()) {
      try (PreparedStatement stmt = connection.prepareStatement("create table junk (acol varchar(10))")) {
        stmt.execute();
        connection.commit();
      }
    }

  }
}
