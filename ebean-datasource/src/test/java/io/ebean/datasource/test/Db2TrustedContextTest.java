package io.ebean.datasource.test;

import io.ebean.datasource.DataSourceBuilder;
import io.ebean.datasource.DataSourcePool;
import io.ebean.datasource.tcdriver.TrustedContextListener;
import io.ebean.datasource.tcdriver.TrustedDb2Driver;
import io.ebean.test.containers.Db2Container;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DB2 has a strange, but API-compliant behaviour, when a connection is in a dirty state and neither committed nor rolled back.
 * <p>
 * By default, a DB2-connection cannot be closed if it is in a unit of work (=transaction) and an exception is thrown.
 * <p>
 * This can be controlled with the "connectionCloseWithInFlightTransaction" parameter
 * https://www.ibm.com/docs/en/db2/11.5?topic=pdsdjs-common-data-server-driver-jdbc-sqlj-properties-all-database-products
 * <p>
 * There are several cases, when there is an open unit of work:
 * <ul>
 *   <li>forget commit/rollback before closing the connection, because an exception occurs</li>
 *   <li>calling connection.getSchema() starts a new UOW (because it internally executes a query)</li>
 * </ul>
 * <p>
 * See also https://github.com/ebean-orm/ebean-datasource/issues/116 for more details
 */
@Disabled("DB2 container start is slow - run manually")
class Db2TrustedContextTest {

  private static Db2Container container;

  private static Method dockerSuMethod = getSuMethod();

  private static TrustedContextListener listener = new TrustedContextListener();

  static {
    new TrustedDb2Driver();
  }

  /**
   * Unfortunately, container.dockerSu is protected. So we use some reflection in the meantime
   *
   * @return
   */
  private static Method getSuMethod() {
    try {
      Method m = Db2Container.class.getDeclaredMethod("dockerSu", String.class, String.class);
      m.setAccessible(true);
      return m;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  static void dockerSu(String user, String cmd) {
    System.out.println("dockerSu: " + user + ", " + cmd);
    try {
      List<String> ret = (List<String>) dockerSuMethod.invoke(container, user, cmd);
      System.out.println("OK: " + ret);
    } catch (InvocationTargetException ite) {
      System.err.println("FAIL: " + ite.getCause().getMessage());
    } catch (Exception e) {
      System.err.println("FAIL: ");
      e.printStackTrace();
    }
  }

  @BeforeAll
  static void before() throws InvocationTargetException, IllegalAccessException {
    container = Db2Container.builder("11.5.6.0a")
      .port(55505)
      .containerName("trusted_context")
      .dbName("unit")
      .user("unit")
      .password("unit")
      // to change collation, charset and other parameters like pagesize:
      .configOptions("USING CODESET UTF-8 TERRITORY DE COLLATE USING IDENTITY PAGESIZE 32768")
      .configOptions("USING STRING_UNITS CODEUNITS32")
      .build();

    container.start();

    //setupTrustedContext("172.16.0.1"); // TODO: This will change per machine!
  }

  private static void setupTrustedContext(String localDockerIp) {

    dockerSu("root", "useradd webuser");
    dockerSu("root", "useradd tenant1");
    dockerSu("root", "useradd tenant2");
    dockerSu("root", "echo \"webuser:webpass\" | chpasswd");
    dockerSu("root", "echo \"tenant1:pass1\" | chpasswd");
    dockerSu("root", "echo \"tenant2:pass2\" | chpasswd");


    dockerSu("admin", "db2 connect to unit;db2 drop trusted context webapptrust");
    dockerSu("admin", "db2 connect to unit;db2 drop table S1.test;db2 drop table S2.test");

    // Setting up the trusted context
    dockerSu("admin", "db2 connect to unit;db2 create trusted context webapptrust based upon connection using system authid webuser attributes \\(address \\'" + localDockerIp + "\\'\\)  WITH USE FOR tenant1 WITHOUT AUTHENTICATION, tenant2 WITH AUTHENTICATION ENABLE");
    dockerSu("admin", "db2 connect to unit;db2 create table S1.test \\(id int\\)");
    dockerSu("admin", "db2 connect to unit;db2 insert into S1.test values \\(1\\)");
    dockerSu("admin", "db2 connect to unit;db2 create table S2.test \\(id int\\)");
    dockerSu("admin", "db2 connect to unit;db2 insert into S2.test values \\(2\\)");
    dockerSu("admin", "db2 connect to unit;db2 grant connect on database to user webuser");
    dockerSu("admin", "db2 connect to unit;db2 grant connect on database to user tenant1");
    dockerSu("admin", "db2 connect to unit;db2 grant connect on database to user tenant2");

    dockerSu("admin", "db2 connect to unit;db2 grant all on schema S1 to user tenant1");
    dockerSu("admin", "db2 connect to unit;db2 grant all on schema S2 to user tenant2");
  }

  @AfterAll
  static void after() {

  }

  private AtomicInteger successCount = new AtomicInteger();
  private AtomicInteger queryCount = new AtomicInteger();
  private boolean running = true;


  void doSomeWork(DataSourcePool pool, int tenant) {
    listener.setContext("tenant" + tenant, "pass" + tenant, "S" + tenant);
    try {
      while (running) {
        assertThat(executeQuery(pool, "select * from test")).isEqualTo(tenant); // each tenant must read its own data!
        queryCount.incrementAndGet();
      }
      successCount.incrementAndGet();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  Thread createWorkerThreas(DataSourcePool pool, int tenant) {
    Thread thread = new Thread(() -> {
      doSomeWork(pool, tenant);
    });
    thread.start();
    return thread;
  }

  int executeQuery(DataSourcePool pool, String query) throws SQLException {
    try (Connection conn = pool.getConnection()) {
      try (PreparedStatement pstmt = conn.prepareStatement(query)) {
        ResultSet rs = pstmt.executeQuery();
        assertThat(rs.next()).isTrue();
        return rs.getInt(1);
      } finally {
        conn.rollback();
      }
    }
  }

  @Test
  void testSwitch() throws Exception {

    DataSourcePool pool = getPool();
    try {
      // set tenant of this thread to tenant1
      listener.setContext("tenant1", "pass1", "S1");

      pool.status(true);
      assertThat(executeQuery(pool, "select * from test")).isEqualTo(1); // each tenant must read its own data!
      assertThat(executeQuery(pool, "select * from test")).isEqualTo(1); // check cache hit
      assertThat(pool.status(false).hitCount()).isEqualTo(2);

      assertThatThrownBy(() -> executeQuery(pool, "select * from S2.test"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("SQLCODE=-551, SQLSTATE=42501, SQLERRMC=TENANT1;SELECT;S2.TEST");

      listener.setContext("tenant2", "pass2", "S2"); // try again. Same query with
      assertThat(executeQuery(pool, "select * from S2.test")).isEqualTo(2);


      checkThroughput(pool, 500);
    } finally {
      pool.shutdown();
    }
  }

  private void checkThroughput(DataSourcePool pool, int threadCount) throws InterruptedException {
    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < threadCount; i++) {
      threads.add(createWorkerThreas(pool, i % 2 + 1));
    }
    Thread.sleep(5000);
    running = false;
    for (Thread thread : threads) {
      thread.join();
    }
    System.out.println("Success: " + successCount.get() + ", query: " + queryCount.get());
    System.out.println(pool.status(false));
    assertThat(successCount.get()).isEqualTo(threadCount);
  }


  private static DataSourcePool getPool() {
    return DataSourceBuilder.create()
      .url(container.jdbcUrl().replace(":db2:", ":db2trusted:"))
      .username("webuser")
      .password("webpass")
      .ownerUsername("unit")
      .ownerPassword("unit")
      .maxConnections(20)
      .listener(listener)
      .build();
  }
}
