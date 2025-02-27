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

  private int success;
  private int failure;

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
    ExecutorService executorService = Executors.newCachedThreadPool();
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        futures.add(executorService.submit(() -> doSomeWork(pool)));
      }

      for (Future<?> future : futures) {
        future.get();
      }
      assertThat(success).isGreaterThan(1);
    } finally {
      executorService.shutdownNow();
      pool.shutdown();
    }

  }

  private void doSomeWork(DataSourcePool pool) {
    for (int i = 0; i < 20; i++) {
      System.out.println("Success: " + success + " failure: " + failure);
      try (Connection conn = pool.getConnection()) {
        Thread.sleep(2000);
        success++;
      } catch (Exception e) {
        // nop
        failure++;
      }
    }
  }

}
