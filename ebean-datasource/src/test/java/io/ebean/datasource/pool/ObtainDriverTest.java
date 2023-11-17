package io.ebean.datasource.pool;

import org.junit.jupiter.api.Test;

import java.sql.Driver;

import static org.assertj.core.api.Assertions.assertThat;

class ObtainDriverTest {

  @Test
  void h2() {
    Driver h2Driver = ObtainDriver.driver(org.h2.Driver.class.getName(), "junk");
    assertThat(h2Driver).isInstanceOf(org.h2.Driver.class);
  }

  @Test
  void h2Url() {
    assertThat(ObtainDriver.driver(null, "jdbc:h2:mem")).isInstanceOf(org.h2.Driver.class);
    assertThat(ObtainDriver.driver("", "jdbc:h2:mem")).isInstanceOf(org.h2.Driver.class);
  }

  @Test
  void postgres() {
    Driver h2Driver = ObtainDriver.driver(org.postgresql.Driver.class.getName(), "junk");
    assertThat(h2Driver).isInstanceOf(org.postgresql.Driver.class);
  }

  @Test
  void postgresUrl() {
    Driver h2Driver = ObtainDriver.driver(null, "jdbc:postgresql://127.0.0.1:9999/app");
    assertThat(h2Driver).isInstanceOf(org.postgresql.Driver.class);
  }
}
