package io.ebean.datasource.pool;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.ebean.datasource.DataSourceAlert;
import io.ebean.datasource.DataSourceConfig;

public class ConnectionPoolDbOutageTest implements DataSourceAlert, WaitFor {

  private static final Logger log = LoggerFactory.getLogger(ConnectionPoolDbOutageTest.class);
  private String connString;
  private Connection h2Conn;
  
  private int up;
  private int down;
  private int warn;
  
  
  @AfterEach
  void afterAll() throws SQLException {
    h2Conn.close();
  }
  
  /**
   * Changes the password and closes all exisitng sessions.
   */
  void setPassword(String pw) throws SQLException {
    List<Integer> sessions = new ArrayList<>();
    try (Statement stmt = h2Conn.createStatement()) {
      ResultSet rs = stmt.executeQuery("SELECT SESSION_ID from INFORMATION_SCHEMA.SESSIONS");
      while (rs.next()) {
        sessions.add(rs.getInt(1));
      }
    }
    
    try (Statement stmt = h2Conn.createStatement()) {
      stmt.execute("alter user sa set password '" + pw + "'");
    }
    
    h2Conn.close();
    // reopen connection with new credentials and kill old session
    h2Conn = DriverManager.getConnection(connString, "sa", pw);
    for (Integer sessionId : sessions) {
      try (Statement stmt = h2Conn.createStatement()) {
        stmt.execute("call abort_session(" + sessionId + ")");
      }
    }
  }

  private DataSourceConfig config() {

    DataSourceConfig config = new DataSourceConfig();
    config.setDriver("org.h2.Driver");
    config.setUrl(connString);
    config.setUsername("sa");
    config.setPassword("sa");
    config.setMinConnections(3);
    config.setMaxConnections(4);
    config.setFailOnStart(false);
    config.setHeartbeatFreqSecs(1);
    config.setAlert(this);

    return config;
  }

  @Test
  public void testOfflineFromStart() throws InterruptedException, SQLException {
    connString = "jdbc:h2:mem:testOfflineFromStart";
    h2Conn = DriverManager.getConnection(connString, "sa", "unknown");
    
    DataSourceConfig config = config();
    assertThat(down).isEqualTo(0);
    ConnectionPool pool = new ConnectionPool("mem:testOfflineFromStart", config);
    // 
    waitFor(() -> {
      assertThat(pool.isOnline()).isFalse();
      assertThat(up).isEqualTo(0);
      assertThat(down).isEqualTo(1);
      assertThat(pool.size()).isEqualTo(0);
    });

    log.info("pool created ");

    // now set the correct password and bring the DS up
    setPassword("sa");
    waitFor(() -> {
      assertThat(pool.isOnline()).isTrue();
      assertThat(up).isEqualTo(1);
      assertThat(down).isEqualTo(1);
      assertThat(pool.size()).isEqualTo(1);
    });
    
  }

  @Test
  public void testOfflineDuringRun() throws InterruptedException, SQLException {
    connString = "jdbc:h2:mem:testOfflineDuringRun";
    h2Conn = DriverManager.getConnection(connString, "sa", "sa");
  
    DataSourceConfig config = config();
    ConnectionPool pool = new ConnectionPool("testOfflineDuringRun", config);
    waitFor(()-> {
      assertThat(pool.isOnline()).isTrue();
      assertThat(up).isEqualTo(1); // we expect an up event in "failOnStart=false" mode
      assertThat(down).isEqualTo(0);
      assertThat(pool.size()).isEqualTo(3);
    });
    log.info("pool created ");
    
    // simulate an outage
    setPassword("outage");
    waitFor(()-> {
      assertThat(pool.isOnline()).isFalse();
      assertThat(up).isEqualTo(1);
      assertThat(down).isEqualTo(1);
    });
  
    // recover from outage
    setPassword("sa");
    waitFor(()-> {
      assertThat(pool.isOnline()).isTrue();
      assertThat(up).isEqualTo(2);
      assertThat(down).isEqualTo(1);
    });
  }

  @Override
  public void dataSourceUp(DataSource dataSource) {
    up++;
  }

  @Override
  public void dataSourceDown(DataSource dataSource, SQLException reason) {
    down++;
  }

  @Override
  public void dataSourceWarning(DataSource dataSource, String msg) {
    warn++;
  }
}
