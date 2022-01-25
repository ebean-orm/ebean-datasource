package io.ebean.datasource.pool;

import io.ebean.datasource.DataSourceConfig;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConnectionPoolOfflineTest implements WaitFor {

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
    assertThat(pool.size()).isEqualTo(0);
    log.info("pool created ");

    waitFor(() -> {
      assertEquals(0, pool.getStatus(false).getFree());
      assertEquals(0, pool.getStatus(false).getBusy());
      assertThat(pool.size()).isEqualTo(0);
    });
    
    pool.online();
    log.info("pool online");
    assertThat(pool.isOnline()).isTrue();
    assertEquals(2, pool.getStatus(false).getFree());
    assertEquals(0, pool.getStatus(false).getBusy());
    assertThat(pool.size()).isEqualTo(2);

    pool.offline();
    log.info("pool offline");
    waitFor(() -> {
      assertThat(pool.isOnline()).isFalse();
      assertEquals(0, pool.getStatus(false).getFree());
      assertEquals(0, pool.getStatus(false).getBusy());
      assertThat(pool.size()).isEqualTo(0);
    });

    pool.online();
    log.info("pool online");
    
    waitFor(() -> {
      assertThat(pool.isOnline()).isTrue();
      assertEquals(2, pool.getStatus(false).getFree());
      assertEquals(0, pool.getStatus(false).getBusy());
      assertThat(pool.size()).isEqualTo(2);
    });
    
    pool.shutdown();

    waitFor(() -> {
      assertThat(pool.isOnline()).isFalse();
      assertEquals(0, pool.getStatus(false).getFree());
      assertEquals(0, pool.getStatus(false).getBusy());
      assertThat(pool.size()).isEqualTo(0);
    });
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
  public void offline_whenBusy_allowed() throws SQLException, InterruptedException {

    DataSourceConfig config = config().setUrl("jdbc:h2:mem:offlineWhenBusy");

    ConnectionPool pool = new ConnectionPool("offlineWhenBusy", config);
    pool.online();

    final Connection busy = pool.getConnection();
    Thread thread = new Thread(() -> {
      try {
        System.out.println("busy connection being used");
        try (PreparedStatement statement = busy.prepareStatement("select 'hello' from dual")) {
          statement.execute();
        }
        Thread.sleep(3000);
        System.out.println("busy connection closing now");
        busy.close();
      } catch (SQLException | InterruptedException e) {
        e.printStackTrace();
        throw new RuntimeException("Should not fail!!");
      }
    });

    thread.start();

    Thread.sleep(200);
    System.out.println("-- taking pool offline (with a busy connection)");
    assertEquals(1, pool.getStatus(false).getBusy());
    assertThat(pool.size()).isEqualTo(2);

    pool.offline();
    assertEquals(0, pool.getStatus(false).getFree());
    assertEquals(1, pool.getStatus(false).getBusy()); // still 1 busy connection
    assertThat(pool.size()).isEqualTo(1);

    // wait to let busy connection finish and close
    waitFor(() -> {
      // all done now
      assertEquals(0, pool.getStatus(false).getFree());
      assertEquals(0, pool.getStatus(false).getBusy());
      assertThat(pool.size()).isEqualTo(0);
    });
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
