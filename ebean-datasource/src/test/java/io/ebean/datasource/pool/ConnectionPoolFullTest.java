package io.ebean.datasource.pool;

import io.ebean.datasource.DataSourceAlert;
import io.ebean.datasource.DataSourceBuilder;
import io.ebean.datasource.DataSourcePool;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectionPoolFullTest implements DataSourceAlert, WaitFor {

  private static final Logger log = LoggerFactory.getLogger(ConnectionPoolFullTest.class);

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
      .alert(this)
      .failOnStart(false)
      .build();

    assertThat(up).isEqualTo(1);
    assertThat(down).isEqualTo(0);

    try {
      try (Connection connection = pool.getConnection()) {
        // we do a rollback here
        System.out.println("So we wait");
        Thread.sleep(2000);
        connection.rollback();
      }
    } finally {
      pool.shutdown();
    }
    assertThat(up).isEqualTo(1);
    assertThat(down).isEqualTo(0);

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
