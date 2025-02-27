package io.ebean.datasource.pool;

import io.ebean.datasource.DataSourceBuilder;
import io.ebean.datasource.DataSourcePool;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectionPoolRecoverTest {

  @Test
  void testHeavyLoadPool() throws Exception {
    DataSourcePool pool = DataSourceBuilder.create()
      .url("jdbc:h2:mem:testConnectionPoolFull")
      .username("sa")
      .password("sa")
      .heartbeatFreqSecs(1)
      .minConnections(1)
      .maxConnections(1)
      .trimPoolFreqSecs(1)
      //     .heartbeatMaxPoolExhaustedCount(1)
      .failOnStart(false)
      .build();
    try {
      for (int i = 0; i < 5; i++) {
        try (Connection conn = pool.getConnection()) {
          Thread.sleep(2000);
          conn.rollback();
        }
      }
    } finally {
      pool.shutdown();
    }
  }

}
