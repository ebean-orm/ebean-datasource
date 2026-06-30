package io.ebean.datasource.pool;

import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.PoolStatus;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionPoolHeartbeatMetricsTest {

  private ConnectionPool createPool() {
    DataSourceConfig config = new DataSourceConfig();
    config.setUrl("jdbc:h2:mem:heartbeatMetrics");
    config.setUsername("sa");
    config.setPassword("");
    config.setMinConnections(2);
    config.setMaxConnections(4);
    return new ConnectionPool("heartbeatMetrics", config);
  }

  @Test
  void heartbeat_doesNotAffectUsageMetrics() throws SQLException {
    ConnectionPool pool = createPool();
    try {
      // application use - establishes the baseline usage metrics
      try (Connection c1 = pool.getConnection(); Connection c2 = pool.getConnection()) {
        c1.rollback();
        c2.rollback();
      }

      PoolStatus before = pool.status(false);
      assertThat(before.hitCount()).isEqualTo(2);

      // heartbeat validation obtains/validates a connection multiple times
      for (int i = 0; i < 5; i++) {
        pool.heartbeat();
      }

      PoolStatus after = pool.status(false);
      // heartbeat validation must NOT be counted as application use
      assertThat(after.hitCount()).isEqualTo(before.hitCount());
      assertThat(after.waitCount()).isEqualTo(before.waitCount());
      assertThat(after.totalWaitMicros()).isEqualTo(before.totalWaitMicros());
      assertThat(after.maxAcquireMicros()).isEqualTo(before.maxAcquireMicros());
    } finally {
      pool.shutdown();
    }
  }
}
