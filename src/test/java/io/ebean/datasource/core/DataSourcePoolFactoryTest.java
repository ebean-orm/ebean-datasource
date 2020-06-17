package io.ebean.datasource.core;

import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourceFactory;
import io.ebean.datasource.DataSourcePool;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class DataSourcePoolFactoryTest {

  @Test
  public void createPool() throws Exception {

    DataSourceConfig config = new DataSourceConfig();
    config.setUrl("jdbc:h2:mem:factory");
    config.setUsername("sa");
    config.setPassword("");

    DataSourcePool pool = DataSourceFactory.create("test_factory", config);

    try (Connection connection = pool.getConnection()) {
      try (PreparedStatement stmt = connection.prepareStatement("create table junk (acol varchar(10))")) {
        stmt.execute();
        connection.commit();
      }
    }

    pool.shutdown();
  }
}
