package io.ebean.datasource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Creates a schema and role using the database owner.
 */
public class PostgresInitDatabase implements InitDatabase {

  private static final System.Logger log = System.getLogger("io.ebean.datasource");

  @Override
  public void run(Connection connection, DataSourceConfig config) throws SQLException {
    String username = config.getUsername();
    String password = config.getPassword();
    log.log(System.Logger.Level.INFO, "Creating schema and role for %s", username);
    execute(connection, String.format("create schema if not exists %s", username));
    execute(connection, String.format("create role %s with login password '%s'", username, password));
    execute(connection, String.format("grant all on schema %s to %s", username, username));
  }

  private void execute(Connection connection, String sql) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.execute();
    }
  }
}
