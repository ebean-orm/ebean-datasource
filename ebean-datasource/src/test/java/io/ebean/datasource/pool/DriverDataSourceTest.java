package io.ebean.datasource.pool;

import io.ebean.datasource.DataSourceBuilder;
import io.ebean.datasource.DataSourceConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class DriverDataSourceTest {

  @Test
  void ofWhenDataSourceSupplied() {
    DataSource suppliedDataSource = Mockito.mock(DataSource.class);

    DataSourceConfig builder = new DataSourceConfig().dataSource(suppliedDataSource);
    DataSource result = DriverDataSource.of("foo", builder);
    assertThat(result).isSameAs(suppliedDataSource);
  }

  @Test
  void of() throws SQLException {
    var builder = DataSourceBuilder.create()
      .url("jdbc:h2:mem:driverDataSource")
      .username("sa")
      .password("")
      .settings();

    DataSource dataSource = DriverDataSource.of("foo", builder);
    try (Connection connection = dataSource.getConnection()) {
      assertThat(connection).isNotNull();
    }
    try (Connection connection = dataSource.getConnection("sa", "")) {
      assertThat(connection).isNotNull();
    }
  }
}
