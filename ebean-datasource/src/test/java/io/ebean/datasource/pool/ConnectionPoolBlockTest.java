package io.ebean.datasource.pool;

import io.ebean.datasource.DataSourceConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectionPoolBlockTest {

  private final Logger logger = LoggerFactory.getLogger(ConnectionPoolBlockTest.class);

  private final ConnectionPool pool;

  private final Random random = new Random();

  private int total;

  ConnectionPoolBlockTest() {
    pool = createPool();
  }

  private ConnectionPool createPool() {

    DataSourceConfig config = new DataSourceConfig();
    config.setDriver("org.h2.Driver");
    config.setUrl("jdbc:h2:mem:tests");
    config.setUsername("sa");
    config.setPassword("");
    config.setMinConnections(1);
    config.setMaxConnections(100);
    config.setAutoCommit(false);
    config.setTrimPoolFreqSecs(1);
    config.setMaxInactiveTimeSecs(1);
    config.setHeartbeatFreqSecs(1);
    config.enforceCleanClose(true);
    return new ConnectionPool("testblock", config);
  }

  @AfterEach
  public void after() throws InterruptedException {
    pool.shutdown();
  }

  /**
   * Yes, this code does some strange things to simulate a blocking close.
   *
   * 1. It does not commit/rollback the pooledConnection
   * 2. because of `enforceCleanClose = true`, the pool tries to close the
   *    underlying H2 console.
   * 3. another thread holds a synchronized-lock on that H2 connection,
   *    so the pool cannot close that connection quickly!
   *
   * This can happen on network outage, because close may send TCP/IP packet
   * which can slow down the pool whenever IO is done during the pool lock.
   */
  public void blockPool() {
    try (Connection conn = pool.getConnection()) {
      // we close the underlying h2 connection and start a thread that holds
      // synchronized lock on the h2 connection.
      new Thread(() -> {
        try {
          Connection h2Conn = conn.unwrap(org.h2.jdbc.JdbcConnection.class);
          synchronized (h2Conn) {
            Thread.sleep(1000);
          }
        } catch (Exception e) {
          e.printStackTrace();
        }

      }).start();
      Thread.sleep(50);
    } catch (AssertionError e) {
      // expected
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  public void test() throws Exception {

    new Thread(this::blockPool).start();
    // wait for warmup
    Thread.sleep(100);

    long start = System.currentTimeMillis();
    try (Connection conn = pool.getConnection()) {
      conn.rollback();
    }
    assertThat(System.currentTimeMillis() - start).isLessThan(100);
  }
}
