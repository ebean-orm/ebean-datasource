package io.ebean.datasource.pool;

import io.ebean.datasource.DataSourceBuilder;
import org.junit.jupiter.api.Test;

import java.sql.Driver;

import static org.assertj.core.api.Assertions.assertThat;

class ObtainDriverTest {

  @Test
  void h2() {
    var builder = DataSourceBuilder.create().driver(org.h2.Driver.class.getName());
    Driver h2Driver = ObtainDriver.driver(builder.settings(), "junk");
    assertThat(h2Driver).isInstanceOf(org.h2.Driver.class);
  }

  @Test
  void h2Url() {
    var builderWhenNull = DataSourceBuilder.create().driver((String) null).settings();
    assertThat(ObtainDriver.driver(builderWhenNull, "jdbc:h2:mem")).isInstanceOf(org.h2.Driver.class);

    var builderWhenEmpty = DataSourceBuilder.create().driver("").settings();
    assertThat(ObtainDriver.driver(builderWhenEmpty, "jdbc:h2:mem")).isInstanceOf(org.h2.Driver.class);
  }

  @Test
  void postgres() {
    var builder = DataSourceBuilder.create().driver(org.postgresql.Driver.class.getName()).settings();
    Driver h2Driver = ObtainDriver.driver(builder, "junk");
    assertThat(h2Driver).isInstanceOf(org.postgresql.Driver.class);
  }

  @Test
  void postgresUrl() {
    var builder = DataSourceBuilder.create().settings();
    Driver h2Driver = ObtainDriver.driver(builder, "jdbc:postgresql://127.0.0.1:9999/app");
    assertThat(h2Driver).isInstanceOf(org.postgresql.Driver.class);
  }

  @Test
  void driverClass() {
    var builder = DataSourceBuilder.create().driver(org.postgresql.Driver.class).settings();
    Driver pgDriver = ObtainDriver.driver(builder, "junk");
    assertThat(pgDriver).isInstanceOf(org.postgresql.Driver.class);
  }

  @Test
  void driverInstance() {
    org.postgresql.Driver driverInstance = new org.postgresql.Driver();
    var builder = DataSourceBuilder.create().driver(driverInstance).settings();
    Driver pgDriver = ObtainDriver.driver(builder, "junk");
    assertThat(pgDriver).isSameAs(driverInstance);
  }
}
