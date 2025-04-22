package io.ebean.datasource.pool;

import io.ebean.datasource.DataSourceAlert;
import io.ebean.datasource.DataSourceBuilder;
import io.ebean.datasource.DataSourcePool;
import io.ebean.datasource.PoolStatus;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectionPoolFullTest implements DataSourceAlert {

  private int up;
  private int down;

  @Test
  void testPoolFullWithHeartbeat() throws Exception {

    DataSourcePool pool = DataSourceBuilder.create()
      .url("jdbc:h2:mem:testConnectionPoolFull")
      .username("sa")
      .password("sa")
      .heartbeatFreqSecs(1)
      .minConnections(1)
      .maxConnections(1)
      .trimPoolFreqSecs(1)
      .heartbeatMaxPoolExhaustedCount(1)
      .alert(this)
      .failOnStart(false)
      .build();

    assertThat(up).isEqualTo(1);
    assertThat(down).isEqualTo(0);

    try {
      // block the thread for 2 secs. The heartbeat must not shutdown the pool
      try (Connection connection = pool.getConnection()) {
        System.out.println("waiting 2s");
        Thread.sleep(2000);
        connection.rollback();
      }
      assertThat(up).isEqualTo(1);
      assertThat(down).isEqualTo(0);

      // now block the thread longer, so that exhausted count will be reached
      try (Connection connection = pool.getConnection()) {
        System.out.println("waiting 4s");
        Thread.sleep(4000);
        connection.rollback();
      }
      // we expect, that the pool goes down.
      assertThat(up).isEqualTo(1);
      assertThat(down).isEqualTo(1);

      System.out.println("waiting 2s for recovery");
      Thread.sleep(2000);
      assertThat(up).isEqualTo(2);
      assertThat(down).isEqualTo(1);

      // pool should be OK again
      try (Connection connection = pool.getConnection()) {
        connection.rollback();
      }

      assertThat(up).isEqualTo(2);
      assertThat(down).isEqualTo(1);

      PoolStatus status = pool.status(true);
      assertThat(status.waitCount()).isGreaterThan(0);
      assertThat(status.totalWaitMicros()).isBetween(0L, 9_000_000L);
      assertThat(status.totalAcquireMicros()).isBetween(0L, 20_000_000L);
      assertThat(status.maxAcquireMicros()).isBetween(0L, 3_000_000L);

    } finally {
      pool.shutdown();
    }

  }


  @Override
  public void dataSourceUp(DataSource dataSource) {
    up++;
  }

  @Override
  public void dataSourceDown(DataSource dataSource, SQLException reason) {
    down++;
  }
}
