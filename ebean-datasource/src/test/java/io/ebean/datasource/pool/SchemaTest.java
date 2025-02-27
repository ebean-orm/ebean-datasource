package io.ebean.datasource.pool;

import io.ebean.datasource.DataSourceConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class SchemaTest {

  private final ConnectionPool pool;

  SchemaTest() {
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

  @BeforeEach
  void initTables() throws SQLException {
    try (Connection conn = pool.getConnection()) {
      Statement statement = conn.createStatement();
      statement.execute("CREATE TABLE test (name VARCHAR(255))");
      statement.execute("INSERT INTO test (name) VALUES ('default schema')");
      statement.execute("CREATE SCHEMA SCHEMA1");
      statement.execute("CREATE SCHEMA SCHEMA2");
      conn.commit();
    }
    try (Connection conn = pool.getConnection()) {
      String orig = conn.getSchema();
      conn.setSchema("SCHEMA1");
      Statement statement = conn.createStatement();
      statement.execute("CREATE TABLE test (name VARCHAR(255))");
      statement.execute("INSERT INTO test (name) VALUES ('schema1')");
      conn.setSchema("SCHEMA2");
      statement.execute("CREATE TABLE test (name VARCHAR(255))");
      statement.execute("INSERT INTO test (name) VALUES ('schema2')");
      conn.setSchema(orig);
      conn.commit();
    }
  }

  @AfterEach
  void after() {
    pool.shutdown();
  }

  @Test
  void getConnectionWithSchemaSwitch() throws SQLException {
    try (Connection conn = pool.getConnection()) {
      Statement statement = conn.createStatement();
      ResultSet rs = statement.executeQuery("SELECT name FROM test");
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString("name")).isEqualTo("default schema");
      conn.rollback();
    }
    try (Connection conn = pool.getConnection()) {
      conn.setSchema("SCHEMA1");
      Statement statement = conn.createStatement();
      ResultSet rs = statement.executeQuery("SELECT name FROM test");
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString("name")).isEqualTo("schema1");
      conn.rollback();
    }
    try (Connection conn = pool.getConnection()) {
      conn.setSchema("SCHEMA2");
      Statement statement = conn.createStatement();
      ResultSet rs = statement.executeQuery("SELECT name FROM test");
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString("name")).isEqualTo("schema2");
      conn.rollback();
    }
    try (Connection conn = pool.getConnection()) {
      Statement statement = conn.createStatement();
      ResultSet rs = statement.executeQuery("SELECT name FROM test");
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString("name")).isEqualTo("default schema");
      conn.rollback();
    }
  }
}
