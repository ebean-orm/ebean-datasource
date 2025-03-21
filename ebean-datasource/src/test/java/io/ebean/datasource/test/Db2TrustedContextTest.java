package io.ebean.datasource.test;

import io.ebean.datasource.DataSourceBuilder;
import io.ebean.datasource.DataSourcePool;
import io.ebean.datasource.tcdriver.TrustedContextListener;
import io.ebean.datasource.tcdriver.TrustedDb2Driver;
import io.ebean.test.containers.Db2Container;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * This test class shows a competitition between ONE connection pool that uses a DB2
 * trusted context and two connection pools.
 */
@Disabled("DB2 container start is slow - run manually")
class Db2TrustedContextTest {

  private static Db2Container container;

  private static Method dockerSuMethod = getSuMethod();

  private static TrustedContextListener listener = new TrustedContextListener();
  private static final Random rand = new Random();
  private static List<String> summary = new ArrayList<>();

  static {
    new TrustedDb2Driver();
  }

  /**
   * Unfortunately, container.dockerSu is protected. So we use some reflection in the meantime
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

  /**
   * Unfortunately, container.dockerSu is protected. So we use some reflection in the meantime
   */
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

  /**
   * Setup the DB2 docker with trusted context support
   */
  @BeforeAll
  static void before() throws InvocationTargetException, IllegalAccessException {
    container = Db2Container.builder("11.5.6.0a")
      .port(55505)
      .containerName("trusted_context")
      .dbName("unit")
      .user("unit")
      .password("unit")
      // to change collation, charset and other parameters like pagesize:
      .createOptions("USING CODESET UTF-8 TERRITORY DE COLLATE USING IDENTITY PAGESIZE 32768")
      .configOptions("USING STRING_UNITS SYSTEM")
      .build();

    container.start();

    //setupTrustedContext("172.16.0.1"); // TODO: This will change per machine!
  }

  @AfterAll
  static void after() {
    //container.stop();
    summary.forEach(System.out::println);
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


  private AtomicInteger successCount = new AtomicInteger();
  private AtomicInteger queryCount = new AtomicInteger();
  private volatile boolean running = true;


  enum LoadProfile {
    /**
     * try to do as much as work you can
     */
    MAX_LOAD,
    /**
     * Perform 100 queries and hold connection for 10ms
     */
    HOLD,
    /**
     * After a random delay, acquire 10 connections at once and hold them in total for 1s
     */
    BURST;
  }

  void doSomeWork(DataSourcePool pool, int tenant, LoadProfile loadProfile) {
    listener.setContext("tenant" + tenant, "pass" + tenant, "S" + tenant);
    try {
      switch (loadProfile) {
        case MAX_LOAD:
          while (running) {
            assertThat(executeQuery(pool, "select * from test")).isEqualTo(tenant);
            queryCount.incrementAndGet();
          }
          break;
        case HOLD:
          for (int i = 0; i < 100; i++) {
            try (Connection conn = pool.getConnection()) {
              Thread.sleep(10);
              conn.rollback();
            }
            queryCount.incrementAndGet();
          }
          break;
        case BURST:
          int wait = rand.nextInt(1000);
          Thread.sleep(wait);
          List<Connection> connections = new ArrayList<>();
          for (int i = 0; i < 10; i++) {
            connections.add(pool.getConnection());
            queryCount.incrementAndGet();
          }
          Thread.sleep(1000 - wait);
          for (Connection connection : connections) {
            connection.rollback();
            connection.close();
          }
          break;
      }
      successCount.incrementAndGet();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  Thread createWorkerThreas(DataSourcePool pool, int tenant, LoadProfile loadProfile) {
    Thread thread = new Thread(() -> {
      doSomeWork(pool, tenant, loadProfile);
    });
    return thread;
  }

  int executeQuery(DataSourcePool pool, String query) throws Exception {
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

  @ParameterizedTest
  @MethodSource("testKeys")
  void testSwitchWithTrustedContext(TestKey testKey) throws Exception {

    DataSourcePool pool = getPool(testKey.poolSize);
    try {
      // set tenant of this thread to tenant1
      listener.setContext("tenant1", "pass1", "S1");
      // TestDDL
      pool.status(true);
      assertThat(executeQuery(pool, "select * from test")).isEqualTo(1); // each tenant must read its own data!
      assertThat(executeQuery(pool, "select * from test")).isEqualTo(1); // check cache hit
      assertThat(pool.status(false).hitCount()).isEqualTo(2);

      testDdl(pool, 1);
      assertThat(executeQuery(pool, "select * from test2")).isEqualTo(1);

      assertThatThrownBy(() -> executeQuery(pool, "select * from S2.test"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("SQLCODE=-551, SQLSTATE=42501, SQLERRMC=TENANT1;SELECT;S2.TEST");

      listener.setContext("tenant2", "pass2", "S2"); // try again. Same query with
      assertThat(executeQuery(pool, "select * from S2.test")).isEqualTo(2);

      testDdl(pool, 2);
      assertThat(executeQuery(pool, "select * from test")).isEqualTo(2);
      assertThat(executeQuery(pool, "select * from test2")).isEqualTo(2);

      try {
        long qps = checkThroughput(pool, pool, testKey.threads, testKey.loadProfile);
        summary.add(testKey + ",\t" + qps+",\tswitch");
      } catch (Throwable t) {
        summary.add(testKey + ",\tFAIL,\tswitch");
        throw t;
      }
      // Query per seconds
      // Threads | maxConn 5 | maxConn 10 | maxConn 20
      // 1       | 3837      | 3885       | 3904
      // 2       | 5401      | 3900       | 5649
      // 5       | 8991      | 9441       | 8029
      // 10      | 1407      | 12438      | 12187
      // 20      | 1739      | 1825       | 13845
      // 200     |           |            | 2127
      // on high contention, the switching pool drops massive in performance

      // Query per seconds (with holdConnections)
      // Threads | maxConn 5   | maxConn 10  | maxConn 20
      // 1       | 90          | 87          | 84
      // 2       | 179         | 163         | 176
      // 5       | 399         | 450         | 445
      // 10      | 397         | 873         | 773
      // 20      | 386         | 747         | 1673
      // 200     | 373 HBFail  | 810         | 1337
    } finally {
      pool.shutdown();
    }
  }


  @ParameterizedTest
  @MethodSource("testKeys")
  void testTwoPools(TestKey testKey) throws Exception {

    DataSourcePool pool1 = getPool1(testKey.poolSize / 2);
    DataSourcePool pool2 = getPool2(testKey.poolSize - testKey.poolSize / 2);
    try {
      // set tenant of this thread to tenant1
      pool1.status(true);
      assertThat(executeQuery(pool1, "select * from test")).isEqualTo(1); // each tenant must read its own data!
      assertThat(executeQuery(pool1, "select * from test")).isEqualTo(1); // check cache hit
      assertThat(pool1.status(false).hitCount()).isEqualTo(2);

      assertThatThrownBy(() -> executeQuery(pool1, "select * from S2.test"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("SQLCODE=-551, SQLSTATE=42501, SQLERRMC=TENANT1;SELECT;S2.TEST");

      testDdl(pool1, 1);
      assertThat(executeQuery(pool1, "select * from test2")).isEqualTo(1);

      assertThat(executeQuery(pool2, "select * from S2.test")).isEqualTo(2);

      testDdl(pool2, 2);
      assertThat(executeQuery(pool2, "select * from test")).isEqualTo(2);
      assertThat(executeQuery(pool2, "select * from test2")).isEqualTo(2);

      try {
        long qps = checkThroughput(pool1, pool2, testKey.threads, testKey.loadProfile);
        summary.add(testKey + ",\t" + qps+",\ttwoPools");
      } catch (Throwable t) {
        summary.add(testKey + ",\tFAIL,\ttwoPools");
        throw t;
      }
      // Query per seconds
      // Threads | maxConn 2+3 | maxConn 5+5 | maxConn 10+10
      // 1       | 3878        | 3675        | 3899
      // 2       | 6533        | 6601        | 6498
      // 5       | 8883        | 11665       | 11145
      // 10      | 9820        | 18292       | 17891
      // 20      | 10937       | 17742       | 28214
      // 200     |             |             | 23486
      // even on high contention, dedicated pools provide best performance

      // Query per seconds (with holdConnections)
      // Threads | maxConn 2+3 | maxConn 5+5 | maxConn 10+10
      // 1       | 88          | 88          | 87
      // 2       | 180         | 178         | 181
      // 5       | 154         | 452         | 446
      // 10      | 365         | 895         | 891
      // 20      | 362         | 901         | 1796
      // 200     | 366         | 912         | 1832
    } finally {
      pool1.shutdown();
      pool2.shutdown();
    }
  }

  private static void testDdl(DataSourcePool pool, int value) throws SQLException {
    try (Connection conn = pool.getConnection()) {
      try (Statement stmt = conn.createStatement()) {
        try {
          stmt.execute("drop table test2");
          conn.commit();
        } catch (SQLException e) {
          // Table did not exist
        }
        stmt.execute("create table test2 (id int)");
        try (PreparedStatement pstmt = conn.prepareStatement("insert into test2 values (?)")) {
          pstmt.setInt(1, value);
          pstmt.executeUpdate();
        }
      } finally {
        conn.commit();
      }
    }
  }


  private long checkThroughput(DataSourcePool pool1, DataSourcePool pool2, int threadCount, LoadProfile loadProfile) throws InterruptedException {
    successCount.set(0);
    long time = System.currentTimeMillis();
    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < threadCount; i++) {
      int tenant = i % 2 + 1;
      threads.add(createWorkerThreas(tenant == 1 ? pool1 : pool2, tenant, loadProfile));
    }
    running = true;
    for (Thread thread : threads) {
      thread.start();
    }
    if (loadProfile == LoadProfile.MAX_LOAD) {
      Thread.sleep(5000);
    }
    running = false;
    for (Thread thread : threads) {
      thread.join();
    }
    time = System.currentTimeMillis() - time;
    long qps = queryCount.get() * 1000L / time;
    System.out.println("Success: " + successCount.get() + ", QPS: " + qps);
    System.out.println(pool1.status(false));
    if (pool1 != pool2) {
      System.out.println(pool2.status(false));
    }
    assertThat(successCount.get()).isEqualTo(threadCount);
    return qps;
  }

  static List<TestKey> testKeys() {
    List<TestKey> keys = new ArrayList<>();
    int[] threadsList = {1, 2, 5, 10, 20, 50, 100};
    int[] poolSizeList = {5, 10, 20, 50};
    for (int threads : threadsList) {
      for (int pools : poolSizeList) {
        for (LoadProfile profile : LoadProfile.values()) {
          if (profile == LoadProfile.BURST && pools < 10) {
            continue; // does not make sense
          }
          keys.add(new TestKey(pools, threads, profile));
        }
      }
    }
    return keys;
  }

  static class TestKey {
    final int poolSize;
    final int threads;
    final LoadProfile loadProfile;

    TestKey(int poolSize, int threads, LoadProfile loadProfile) {
      this.poolSize = poolSize;
      this.threads = threads;
      this.loadProfile = loadProfile;
    }

    @Override
    public String toString() {
      return poolSize + ",\t" + threads + ",\t" + loadProfile;
    }
  }

  private static DataSourcePool getPool(int size) {
    return DataSourceBuilder.create()
      .url(container.jdbcUrl().replace(":db2:", ":db2trusted:"))
      .username("webuser")
      .password("webpass")
      .maxConnections(size)
      .listener(listener)
      .build();
  }

  private static DataSourcePool getPool1(int size) {
    return DataSourceBuilder.create()
      .url(container.jdbcUrl() + ":currentSchema=S1;")
      .username("tenant1")
      .password("pass1")
      .maxConnections(size)
      .build();
  }

  private static DataSourcePool getPool2(int size) {
    return DataSourceBuilder.create()
      .url(container.jdbcUrl() + ":currentSchema=S2;")
      .username("tenant2")
      .password("pass2")
      .maxConnections(size)
      .build();
  }
}
