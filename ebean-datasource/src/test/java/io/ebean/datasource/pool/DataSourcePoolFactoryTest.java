package io.ebean.datasource.pool;

import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourceFactory;
import io.ebean.datasource.DataSourcePool;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourcePoolFactoryTest {

  @Test
  void createPool() throws Exception {

    DataSourceConfig config = new DataSourceConfig();
    config.setUrl("jdbc:h2:mem:factory");
    config.setUsername("sa");
    config.setPassword("");

    DataSourcePool pool = DataSourceFactory.create("test_factory", config);
    assertThat(pool).isInstanceOf(ConnectionPool.class);

    try (Connection connection = pool.getConnection()) {
      try (PreparedStatement stmt = connection.prepareStatement("create table junk (acol varchar(10))")) {
        stmt.execute();
        connection.commit();
      }
    }

    pool.shutdown();
  }

  @Test
  void createPool_forLambda() throws Exception {

    DataSourcePool pool = DataSourcePool.builder()
      .url("jdbc:h2:mem:factory2")
      .username("sa")
      .password("")
      .useLambdaCheck(true)
      // .trimPoolFreqSecs(-1) // stop the heartbeat and reduce LAMBDA_MILLIS to 1100
      .build();

    assertThat(pool).isInstanceOf(LambdaPool.class);

    try (Connection connection = pool.getConnection()) {
      try (PreparedStatement stmt = connection.prepareStatement("create table junk (acol varchar(10))")) {
        stmt.execute();
        connection.commit();
      }
    }

    for (int i = 0; i < 10; i++) {
      try (Connection connection = pool.getConnection()) {
        connection.hashCode();
      }
    }

    pool.shutdown();
  }
}
