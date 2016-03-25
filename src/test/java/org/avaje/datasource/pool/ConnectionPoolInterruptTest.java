package org.avaje.datasource.pool;

import org.avaje.datasource.DataSourceConfig;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.testng.Assert.assertTrue;

public class ConnectionPoolInterruptTest {

  private ConnectionPool pool;

  ConnectionPoolInterruptTest() {
    pool = createPool();
  }

  private ConnectionPool createPool() {

    DataSourceConfig config = new DataSourceConfig();
    config.setDriver("org.h2.Driver");
    config.setUrl("jdbc:h2:mem:tests");
    config.setUsername("sa");
    config.setPassword("");
    config.setMinConnections(1);
    config.setMaxConnections(1);

    return new ConnectionPool("interrupt", config);
  }

  @AfterClass
  public void after() {
    pool.shutdown(false);
  }

  @Test
  public void threadsWait_when_interrupt_expect_isInterrupted() throws SQLException, InterruptedException {

    // read all the connects from the pool to hit max size
    pool.getConnection();

    // this threads gets blocked
    Thread thread2 = new Thread(new Runnable() {
      @Override
      public void run() {
        getConnectionAndDoWork("thread2");
      }
    });
    thread2.start();
    Thread.sleep(10);
    // now lets interrupt it
    thread2.interrupt();
    System.out.println("main thread done");
  }

  private void getConnectionAndDoWork(String threadName) {
    try {
      System.out.println(threadName+" getting connection");
      pool.getConnection();
    } catch (Exception e) {
      System.out.println(threadName+" was interrupted? " + Thread.currentThread().isInterrupted());
      assertTrue(Thread.currentThread().isInterrupted());
      throw new RuntimeException("Was interrupted", e);
    }
  }

}