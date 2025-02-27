package io.ebean.datasource.pool;

import io.ebean.datasource.DataSourceBuilder;
import io.ebean.datasource.DataSourcePool;
import org.h2.jdbc.JdbcConnection;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

public class ConnectionPoolHangUpTest {

  @Test
  void testHoldLockOnObject() throws Exception {
    DataSourcePool pool = DataSourceBuilder.create()
      .url("jdbc:h2:mem:testConnectionPoolHangUp")
      .username("sa")
      .password("sa")
      .heartbeatFreqSecs(1)
      .minConnections(1)
      .maxConnections(1)
      .trimPoolFreqSecs(1)
      .heartbeatMaxPoolExhaustedCount(0)
      .failOnStart(false)
      .build();
    try {
      Connection conn = pool.getConnection();
      Thread t = new Thread(() -> {
        try {
          JdbcConnection h2Conn = conn.unwrap(JdbcConnection.class);
          synchronized (h2Conn) {
            Thread.sleep(300000);
          }
        } catch (Exception e) {
          // nop
        }
      });
      t.setDaemon(true);
      t.start();
    } finally {
      pool.shutdown();
    }
  }

}
