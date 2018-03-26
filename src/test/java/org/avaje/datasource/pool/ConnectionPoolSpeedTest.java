package org.avaje.datasource.pool;

import org.avaje.datasource.DataSourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Random;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class ConnectionPoolSpeedTest {

  private Logger logger = LoggerFactory.getLogger(ConnectionPoolSpeedTest.class);

  private ConnectionPool pool;

  private Random random = new Random();

  private int total;

  ConnectionPoolSpeedTest() {
    pool = createPool();
  }

  private ConnectionPool createPool() {

    DataSourceConfig config = new DataSourceConfig();
    config.setDriver("org.h2.Driver");
    config.setUrl("jdbc:h2:mem:tests");
    config.setUsername("sa");
    config.setPassword("");
    config.setMinConnections(2);
    config.setMaxConnections(100);

    return new ConnectionPool("testspeed", config);
  }

  @AfterClass
  public void after() {
    pool.shutdown(false);
  }

  @Test
  public void test() throws SQLException {

    warm();

    total = 0;

    long startNano = System.nanoTime();

    perform();

    long exeNano = System.nanoTime() - startNano;
    long avgNanos = exeNano / total;

    logger.info("avgNanos[{}] total[{}]", avgNanos, total);

    assertThat(avgNanos).isLessThan(300);
  }

  private void perform() throws SQLException {
    for (int i = 0; i < 10000000; i++) {
      getAndCloseSome();
    }
  }

  private void warm() throws SQLException {
    for (int i = 0; i < 50; i++) {
      getAndCloseSome();
    }
  }

  private void getAndCloseSome() throws SQLException {

    int num = random.nextInt(5);
    Connection[] bunch = new Connection[num];
    for (int i = 0; i < num; i++) {
      bunch[i] = pool.getConnection();
    }

    for (Connection connection : bunch) {
      connection.close();
    }

    total += num;
  }

}