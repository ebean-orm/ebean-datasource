package org.avaje.datasource.pool;

import org.avaje.datasource.DataSourceConfig;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class ConnectionPoolTrimIdleTest {

  private ConnectionPool pool;

  ConnectionPoolTrimIdleTest() {
    pool = createPool();
  }

  private ConnectionPool createPool() {

    DataSourceConfig config = new DataSourceConfig();
    config.setDriver("org.h2.Driver");
    config.setUrl("jdbc:h2:mem:tests");
    config.setUsername("sa");
    config.setPassword("");
    config.setMinConnections(2);
    config.setMaxConnections(10);
    config.setMaxInactiveTimeSecs(1);
    config.setTrimPoolFreqSecs(1);
    config.setHeartbeatFreqSecs(1);

    return new ConnectionPool("testidle", config);
  }

  @AfterClass
  public void after() {
    pool.shutdown(false);
  }

  @Test
  public void test() throws SQLException, InterruptedException {

    Connection con1 = pool.getConnection();
    Connection con2 = pool.getConnection();
    Connection con3 = pool.getConnection();
    Connection con4 = pool.getConnection();

    con1.close();
    con2.close();
    con3.close();
    con4.close();

    assertThat(pool.getStatus(false).getFree()).isEqualTo(4);

    Thread.sleep(5000);

    assertThat(pool.getStatus(false).getFree()).isEqualTo(2);

  }
}