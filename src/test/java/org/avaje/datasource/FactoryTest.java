package org.avaje.datasource;

import org.avaje.datasource.core.Factory;
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

    Connection connection = pool.getConnection();

    PreparedStatement stmt = connection.prepareStatement("create table junk (acol varchar(10))");
    stmt.execute();

  }
}