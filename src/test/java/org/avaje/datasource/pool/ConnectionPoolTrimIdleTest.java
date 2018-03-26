package org.avaje.datasource.pool;

import org.avaje.datasource.DataSourceConfig;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Timer;
import java.util.TimerTask;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class ConnectionPoolTrimIdleTest {

  private ConnectionPool createPool() {

    DataSourceConfig config = new DataSourceConfig();
    config.setDriver("org.h2.Driver");
    config.setUrl("jdbc:h2:mem:tests");
    config.setUsername("sa");
    config.setPassword("");
    config.setMinConnections(1);
    config.setMaxConnections(10);
    config.setMaxInactiveTimeSecs(1);
    config.setTrimPoolFreqSecs(1);
    config.setHeartbeatFreqSecs(1);

    return new ConnectionPool("testidle", config);
  }

  @Test
  public void test() throws SQLException, InterruptedException {

    ConnectionPool pool = createPool();
    try {
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

      assertThat(pool.getStatus(false).getFree()).isEqualTo(1);

    } finally {
      pool.shutdown(false);
    }
  }

  @Test
  public void test_withDecreasingActivity_expect_trimToActivityLevel() throws SQLException, InterruptedException {

    ConnectionPool pool = createPool();
    try {

      Connection[] con = new Connection[10];
      for (int i = 0; i < 10; i++) {
        con[i] = pool.getConnection();
      }
      for (int i = 0; i < 10; i++) {
        con[i].close();
      }

      // start at 10 connections
      assertThat(pool.getStatus(false).getFree()).isEqualTo(10);

      // keep 4 connections busy
      Timer timer0 = createTimer(pool, 4);
      Thread.sleep(6000);

      assertThat(pool.getStatus(false).getFree()).isEqualTo(4);
      timer0.cancel();

      // keep 2 connections busy
      Timer timer1 = createTimer(pool, 2);
      Thread.sleep(6000);

      assertThat(pool.getStatus(false).getFree()).isEqualTo(2);
      timer1.cancel();

      // Go Idle
      Thread.sleep(5000);
      assertThat(pool.getStatus(false).getFree()).isEqualTo(1);

    } finally {
      pool.shutdown(false);
    }
  }

  private Timer createTimer(ConnectionPool pool, int count) {
    Timer timer = new Timer();
    timer.scheduleAtFixedRate(new Task(pool, count), 100, 100);
    return timer;
  }

  class Task extends TimerTask {

    final ConnectionPool pool;

    int count;

    Task(ConnectionPool pool, int count) {
      this.pool = pool;
      this.count = count;
    }

    @Override
    public void run() {
      try {
        Connection[] connection = new Connection[count];
        for (int i = 0; i < count; i++) {
          connection[i] = pool.getConnection();
        }
        for (int i = 0; i < count; i++) {
          connection[i].close();
        }

      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
  }
}