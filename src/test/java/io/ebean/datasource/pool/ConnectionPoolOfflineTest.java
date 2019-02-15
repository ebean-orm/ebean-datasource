package io.ebean.datasource.pool;

import io.ebean.datasource.DataSourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertEquals;

public class ConnectionPoolOfflineTest {

  private static final Logger log = LoggerFactory.getLogger(ConnectionPoolOfflineTest.class);

  private DataSourceConfig config() {

    DataSourceConfig config = new DataSourceConfig();
    config.setDriver("org.h2.Driver");
    config.setUrl("jdbc:h2:mem:testOffline");
    config.setUsername("sa");
    config.setPassword("");
    config.setMinConnections(2);
    config.setMaxConnections(4);
    config.setOffline(true);
    config.setHeartbeatFreqSecs(1);

    return config;
  }

  @Test
  public void testOffline() throws InterruptedException, SQLException {

    DataSourceConfig config = config();

    ConnectionPool pool = new ConnectionPool("testOffline", config);
    assertThat(pool.isOnline()).isFalse();
    log.info("pool created ");
    Thread.sleep(3000);

    assertEquals(0, pool.getStatus(false).getFree());
    assertEquals(0, pool.getStatus(false).getBusy());

    pool.online();
    log.info("pool online");
    assertThat(pool.isOnline()).isTrue();
    assertEquals(2, pool.getStatus(false).getFree());
    assertEquals(0, pool.getStatus(false).getBusy());

    Thread.sleep(3000);

    pool.offline();
    log.info("pool offline");
    assertThat(pool.isOnline()).isFalse();
    assertEquals(0, pool.getStatus(false).getFree());
    assertEquals(0, pool.getStatus(false).getBusy());

    Thread.sleep(3000);

    pool.online();
    log.info("pool online");
    assertThat(pool.isOnline()).isTrue();
    assertEquals(2, pool.getStatus(false).getFree());
    assertEquals(0, pool.getStatus(false).getBusy());
    Thread.sleep(3000);

    pool.shutdown();

    assertThat(pool.isOnline()).isFalse();
    assertEquals(0, pool.getStatus(false).getFree());
    assertEquals(0, pool.getStatus(false).getBusy());
  }

  @Test
  public void offlineOffline() {

    DataSourceConfig config = config().setUrl("jdbc:h2:mem:offlineOffline");

    ConnectionPool pool = new ConnectionPool("offlineOffline", config);
    assertThat(pool.isOnline()).isFalse();

    pool.offline();
    assertThat(pool.isOnline()).isFalse();

    pool.offline();
    pool.offline();
    assertThat(pool.isOnline()).isFalse();
  }

  @Test
  public void offlineGetConnection_expect_goesOnline() throws SQLException {

    DataSourceConfig config = config().setUrl("jdbc:h2:mem:offlineOffline");

    ConnectionPool pool = new ConnectionPool("offlineOffline", config);
    pool.offline();
    assertThat(pool.isOnline()).isFalse();

    try (Connection connection = pool.getConnection()) {
      assertThat(connection).isNotNull();
      assertThat(pool.isOnline()).isTrue();
    }

    pool.shutdown();
    assertThat(pool.isOnline()).isFalse();
  }

  @Test
  public void onlineOnline() throws SQLException {

    DataSourceConfig config = config().setUrl("jdbc:h2:mem:onlineOnline");

    ConnectionPool pool = new ConnectionPool("onlineOnline", config);
    assertThat(pool.isOnline()).isFalse();

    pool.online();
    assertThat(pool.isOnline()).isTrue();

    pool.online();
    pool.online();
    assertThat(pool.isOnline()).isTrue();
  }
}
