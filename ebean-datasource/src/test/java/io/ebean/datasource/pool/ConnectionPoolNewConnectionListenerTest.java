package io.ebean.datasource.pool;

import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.NewConnectionInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionPoolNewConnectionListenerTest {

  private ConnectionPool pool;

  private final HashMap<Connection, Integer> createdConnections = new HashMap<>();
  private final HashMap<Connection, Integer> afterConnections = new HashMap<>();

  ConnectionPoolNewConnectionListenerTest() {
    pool = createPool();
  }


  private ConnectionPool createPool() {

    DataSourceConfig config = new DataSourceConfig();
    config.setDriver("org.h2.Driver");
    config.setUrl("jdbc:h2:mem:tests");
    config.setUsername("sa");
    config.setPassword("");
    config.setMinConnections(1);
    config.setMaxConnections(5);
    config.connectionInitializer(new NewConnectionInitializer() {
      @Override
      public void preInitialize(Connection connection) {
        synchronized (createdConnections) {
          createdConnections.put(connection, 1 + createdConnections.getOrDefault(connection, 0));
          createdConnections.notifyAll();
        }
      }

      @Override
      public void postInitialize(Connection connection) {
        synchronized (afterConnections) {
          afterConnections.put(connection, 1 + afterConnections.getOrDefault(connection, 0));
          afterConnections.notifyAll();
        }
      }
    });

    return new ConnectionPool("initialize", config);
  }

  @AfterEach
  public void after() {
    pool.shutdown();
  }

  @Test
  public void initializeNewConnectionTest() {
    // Min connections is 1 so one should be created on pool initialization
    synchronized (createdConnections) {
      assertEquals(1, createdConnections.size());
      assertEquals(1, afterConnections.size());
    }

    try (Connection connection = pool.getConnection()) {
      assertNotNull(connection);
      synchronized (createdConnections) {
        assertEquals(1, createdConnections.size());
      }
      synchronized (afterConnections) {
        assertEquals(1, afterConnections.size());
      }
    } catch (Exception e) {
      fail(e.getMessage());
    }

    try (Connection connection = pool.getConnection()) {
      assertNotNull(connection);
      synchronized (createdConnections) {
        assertEquals(1, createdConnections.size());
      }
      synchronized (afterConnections) {
        assertEquals(1, afterConnections.size());
      }

      try (Connection connection2 = pool.getConnection()) {
        assertNotNull(connection2);
        synchronized (createdConnections) {
          assertEquals(2, createdConnections.size());
        }
        synchronized (afterConnections) {
          assertEquals(2, afterConnections.size());
        }
      }
    } catch (Exception e) {
      fail(e.getMessage());
    }
    synchronized (createdConnections) {
      for (var entry : createdConnections.entrySet()) {
        // It should be always 1
        assertEquals(1, entry.getValue());
      }
    }
    synchronized (afterConnections) {
      for (var entry : afterConnections.entrySet()) {
        // It should be always 1
        assertEquals(1, entry.getValue());
      }
    }
  }
}
