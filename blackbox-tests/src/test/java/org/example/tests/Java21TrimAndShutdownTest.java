package org.example.tests;

import io.ebean.datasource.DataSourceBuilder;
import io.ebean.datasource.DataSourcePool;
import io.ebean.test.containers.PostgresContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

class Java21TrimAndShutdownTest {

  private static Logger log = LoggerFactory.getLogger(Java21TrimAndShutdownTest.class);

  @BeforeAll
  static void before() {
     PostgresContainer.builder("15")
      .port(9999)
      .containerName("pool_test")
      .dbName("app")
      .user("db_owner")
      .build()
      .startWithDropCreate();
  }

  @Test
  void test() throws InterruptedException, SQLException {
    Properties clientInfo = new Properties();
    clientInfo.setProperty("ApplicationName", "my-test");

    DataSourcePool pool = DataSourceBuilder.create()
      .url("jdbc:postgresql://127.0.0.1:9999/app")
      .username("db_owner")
      .password("test")
      .clientInfo(clientInfo)
      .maxInactiveTimeSecs(2)
      .heartbeatFreqSecs(1)
      .trimPoolFreqSecs(1)
      .build();

    List<Connection> connectionList = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      connectionList.add(pool.getConnection());
    }

    // close them slowly to allow multiple trims
    for (Connection connection : connectionList) {
      connection.rollback();
      connection.close();
      Thread.sleep(200);
    }

    log.info("----------- Sleep allowing trim -------------");
    Thread.sleep(9_000);
    log.info("----------- Shutdown pool -------------");
    pool.shutdown();
  }
}
