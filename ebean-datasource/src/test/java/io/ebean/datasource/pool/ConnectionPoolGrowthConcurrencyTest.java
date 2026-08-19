package io.ebean.datasource.pool;

import io.ebean.datasource.DataSourceConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectionPoolGrowthConcurrencyTest {

  @Test
  void onDemandCreation_doesNotHoldQueueLock() throws Exception {
    var started = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    var pool = createPool(blockingDataSource(started, release));
    try {
      var first = executor.submit((java.util.concurrent.Callable<Connection>) pool::getConnection);
      var second = executor.submit((java.util.concurrent.Callable<Connection>) pool::getConnection);
      assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

      release.countDown();
      try (Connection firstConnection = first.get(2, TimeUnit.SECONDS);
           Connection secondConnection = second.get(2, TimeUnit.SECONDS)) {
        assertThat(pool.status(false).busy()).isEqualTo(2);
      }
    } finally {
      release.countDown();
      pool.shutdown();
      executor.shutdownNow();
    }
  }

  @Test
  void offlineDuringCreation_doesNotPublishLateConnection() throws Exception {
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var pool = createPool(blockingDataSource(started, release));
    var executor = Executors.newSingleThreadExecutor();
    try {
      var future = executor.submit((java.util.concurrent.Callable<Connection>) pool::getConnection);
      assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

      pool.offline();
      release.countDown();

      assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
        .hasCauseInstanceOf(SQLException.class);
      assertThat(pool.status(false).size()).isZero();
    } finally {
      release.countDown();
      pool.shutdown();
      executor.shutdownNow();
    }
  }

  private ConnectionPool createPool(DataSource dataSource) {
    var config = new DataSourceConfig()
      .setUrl("jdbc:h2:mem:growthConcurrency")
      .setUsername("sa")
      .setPassword("")
      .setMinConnections(0)
      .initialConnections(0)
      .setMaxConnections(2)
      .setHeartbeatFreqSecs(60)
      .setTrimPoolFreqSecs(60)
      .validateOnHeartbeat(false)
      .dataSource(dataSource);
    return new ConnectionPool("growthConcurrency", config);
  }

  private DataSource blockingDataSource(CountDownLatch started, CountDownLatch release) throws java.sql.SQLException {
    var dataSource = Mockito.mock(DataSource.class);
    Mockito.when(dataSource.getConnection()).thenAnswer(invocation -> {
      started.countDown();
      try {
        if (!release.await(2, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Timed out waiting to release connection creation");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted waiting to release connection creation", e);
      }
      try {
        return DriverManager.getConnection("jdbc:h2:mem:growthConcurrency", "sa", "");
      } catch (java.sql.SQLException e) {
        throw new RuntimeException(e);
      }
    });
    return dataSource;
  }
}
