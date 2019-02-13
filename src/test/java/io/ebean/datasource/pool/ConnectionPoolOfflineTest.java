package io.ebean.datasource.pool;

import io.ebean.datasource.DataSourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.testng.Assert.assertEquals;

public class ConnectionPoolOfflineTest {

  private static final Logger log = LoggerFactory.getLogger(ConnectionPoolOfflineTest.class);

  private DataSourceConfig config() {

    DataSourceConfig config = new DataSourceConfig();
    config.setDriver("org.h2.Driver");
    config.setUrl("jdbc:h2:mem:tests");
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

    ConnectionPool pool = new ConnectionPool("test", config);
    log.info("pool created ");
    Thread.sleep(3000);

    assertEquals(0, pool.getStatus(false).getFree());
    assertEquals(0, pool.getStatus(false).getBusy());

    pool.online();
    log.info("pool online");
    assertEquals(2, pool.getStatus(false).getFree());
    assertEquals(0, pool.getStatus(false).getBusy());

    Thread.sleep(3000);

    pool.offline();
    log.info("pool offline");
    assertEquals(0, pool.getStatus(false).getFree());
    assertEquals(0, pool.getStatus(false).getBusy());

    Thread.sleep(3000);

    pool.online();
    log.info("pool online");
    assertEquals(2, pool.getStatus(false).getFree());
    assertEquals(0, pool.getStatus(false).getBusy());
    Thread.sleep(3000);

    pool.shutdown(false);

    assertEquals(0, pool.getStatus(false).getFree());
    assertEquals(0, pool.getStatus(false).getBusy());
  }

}
