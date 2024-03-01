package io.ebean.datasource.test;

import io.ebean.datasource.DataSourcePool;
import org.junit.jupiter.api.Test;

class ShutdownTest {

  /**
   * The test itself will not fail, but if DB shutdown on exit does not work, Junit will quit with:
   * [ERROR] Surefire is going to kill self fork JVM. The exit has elapsed 30 seconds after System.exit(0)
   */
  @Test
  void checkShutdownOnJvmExit() {
    DataSourcePool aPool = null;
    for (int i = 0; i < 4; i++) {
      DataSourcePool pool = DataSourcePool.builder()
        .name("test")
        .url("jdbc:h2:mem:tests" + i)
        .username("sa")
        .password("")
        .shutdownOnJvmExit(true)
        .build();
      if (i == 1) {
        aPool = pool;
      }
    }
    aPool.shutdown();
  }
}
