package io.ebean.datasource.test;

import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourcePool;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * @author Roland Praml, FOCONIS AG
 */
public class ShutdownTest {

  /**
   * The test itself will not fail, but if DB shutdown on exit does not work, Junit will quit with:
   * [ERROR] Surefire is going to kill self fork JVM. The exit has elapsed 30 seconds after System.exit(0)
   */
  @Test
  void checkShutdownOnJvmExit() throws Exception {
    for (int i = 0; i < 10; i++) {
      DataSourcePool pool = new DataSourceConfig()
        .setName("test")
        .setUrl("jdbc:h2:mem:tests" + i)
        .setUsername("sa")
        .setPassword("")
        .build();
    }
    //pool.shutdown();
  }
}
