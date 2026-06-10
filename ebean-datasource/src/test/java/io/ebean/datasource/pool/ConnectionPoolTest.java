package io.ebean.datasource.pool;

import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.PoolStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class ConnectionPoolTest {

  private final ConnectionPool pool;

  ConnectionPoolTest() {
    pool = createPool();
  }

  private ConnectionPool createPool() {
    DataSourceConfig config = new DataSourceConfig();
    config.setUrl("jdbc:h2:mem:tests");
    config.setUsername("sa");
    config.setPassword("");
    config.setMinConnections(2);
    config.setMaxConnections(4);

    return new ConnectionPool("test", config);
  }

  @AfterEach
  void after() {
    pool.shutdown();
  }

  @Test
  void getConnection_expect_poolGrowsAboveMin() throws SQLException {
    Connection con1 = pool.getConnection();
    Connection con2 = pool.getConnection();

    assertThat(pool.status(false).busy()).isEqualTo(2);
    assertThat(pool.status(false).free()).isEqualTo(0);
    assertThat(pool.size()).isEqualTo(2);

    Connection con3 = pool.getConnection();
    assertThat(pool.status(false).busy()).isEqualTo(3);
    assertThat(pool.status(false).free()).isEqualTo(0);
    assertThat(pool.size()).isEqualTo(3);

    con2.rollback();
    con2.close();
    assertThat(pool.status(false).busy()).isEqualTo(2);
    assertThat(pool.status(false).free()).isEqualTo(1);
    assertThat(pool.size()).isEqualTo(3);

    con3.rollback();
    con3.close();
    assertThat(pool.status(false).busy()).isEqualTo(1);
    assertThat(pool.status(false).free()).isEqualTo(2);
    assertThat(pool.size()).isEqualTo(3);

    con1.rollback();
    con1.close();
    PoolStatus status = pool.status(true);
    assertThat(status.busy()).isEqualTo(0);
    assertThat(status.free()).isEqualTo(3);
    assertThat(pool.size()).isEqualTo(3);
    assertThat(status.waitCount()).isEqualTo(0);
    assertThat(status.totalWaitMicros()).isEqualTo(0);
    assertThat(status.hitCount()).isEqualTo(3);
    assertThat(status.maxAcquireMicros()).isBetween(0L, 9000L);
    assertThat(status.meanAcquireNanos() / 1000).isBetween(0L, 9000L);
    assertThat(status.highWaterMark()).isEqualTo(3);
    assertThat(status.minSize()).isEqualTo(2);
    assertThat(status.maxSize()).isEqualTo(4);
  }

  @Test
  void status_size_isBusyPlusFree() throws SQLException {
    PoolStatus initial = pool.status(false);
    assertThat(initial.size()).isEqualTo(initial.busy() + initial.free());

    Connection con1 = pool.getConnection();
    Connection con2 = pool.getConnection();
    Connection con3 = pool.getConnection();

    PoolStatus allBusy = pool.status(false);
    assertThat(allBusy.busy()).isEqualTo(3);
    assertThat(allBusy.free()).isEqualTo(0);
    assertThat(allBusy.size()).isEqualTo(3);
    assertThat(allBusy.size()).isEqualTo(allBusy.busy() + allBusy.free());

    con2.rollback();
    con2.close();

    PoolStatus mixed = pool.status(false);
    assertThat(mixed.busy()).isEqualTo(2);
    assertThat(mixed.free()).isEqualTo(1);
    assertThat(mixed.size()).isEqualTo(3);
    assertThat(mixed.size()).isEqualTo(mixed.busy() + mixed.free());

    con1.rollback();
    con1.close();
    con3.rollback();
    con3.close();

    PoolStatus allFree = pool.status(false);
    assertThat(allFree.busy()).isEqualTo(0);
    assertThat(allFree.free()).isEqualTo(3);
    assertThat(allFree.size()).isEqualTo(3);
    assertThat(allFree.size()).isEqualTo(allFree.busy() + allFree.free());
  }

  @Test
  void getConnection_explicitUserPassword() throws SQLException {
    Connection connection = pool.getConnection("sa", "");
    PreparedStatement statement = connection.prepareStatement("create user testing password '123'");
    statement.execute();
    statement.close();
    connection.rollback();
    connection.close();

    Connection another = pool.getConnection("testing", "123");
    another.close();

    for (int i = 0; i < 10_000; i++) {
      Connection another2 = pool.getConnection();
      another2.rollback();
      another2.close();
    }
    PoolStatus status0 = pool.status(true);

    for (int i = 0; i < 10_000; i++) {
      Connection another2 = pool.getConnection();
      another2.rollback();
      another2.close();
    }
    PoolStatus status = pool.status(false);

    assertThat(status.hitCount()).isEqualTo(10_000);
    assertThat(status.meanAcquireNanos()).isBetween(0L, 9000L);
    assertThat(status.maxAcquireMicros()).isBetween(0L, 9000L);
  }

  @Test
  void unwrapConnection() throws SQLException {
    Connection connection = pool.getConnection();
    Connection underlying = connection.unwrap(Connection.class);

    assertThat(underlying).isInstanceOf(org.h2.jdbc.JdbcConnection.class);
    connection.rollback();
    connection.close();
  }

  @Test
  void getDelegate() throws SQLException {
    Connection connection = pool.getConnection();
    PooledConnection pc = (PooledConnection)connection;
    Connection underlying = pc.delegate();

    assertThat(underlying).isInstanceOf(org.h2.jdbc.JdbcConnection.class);
    connection.rollback();
    connection.close();
  }

  @Test
  void isClosed_afterClose() throws SQLException {
    Connection connection = pool.getConnection();
    assertThat(connection.isClosed()).isFalse();

    connection.rollback();
    connection.close();
    assertThat(connection.isClosed()).isTrue();
  }

  @Test
  void testSchemaSwitch() throws SQLException {
    Connection conn = pool.getConnection();
    Statement stmt = conn.createStatement();
    stmt.executeUpdate("CREATE SCHEMA TENANT_1");
    stmt.executeUpdate("CREATE SCHEMA TENANT_2");
    stmt.executeUpdate("CREATE TABLE TENANT_1.LOCAL_MODEL (id integer)");
    stmt.executeUpdate("CREATE TABLE TENANT_2.LOCAL_MODEL (id integer)");
    stmt.close();

    conn.setSchema("TENANT_1");
    PreparedStatement ps1 = conn.prepareStatement("SELECT * from local_model");
    ps1.close();
    PreparedStatement ps2 = conn.prepareStatement("SELECT * from local_model");
    ps2.close();

    conn.setSchema("TENANT_2");
    PreparedStatement ps3 = conn.prepareStatement("SELECT * from local_model");
    ps3.close();


    assertThat(ps1).isSameAs(ps2);  // test if pstmtCache is working
    assertThat(ps1).isNotSameAs(ps3); // test if datasource recognize schema change
  }
}
