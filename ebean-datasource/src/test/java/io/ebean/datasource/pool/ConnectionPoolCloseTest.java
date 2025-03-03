package io.ebean.datasource.pool;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.ebean.datasource.DataSourceBuilder;
import io.ebean.datasource.DataSourcePool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests, if connections are clean when closing them.
 * <p>
 * Test must run with "do not use module path" option in IntelliJ.
 * <p>
 * See also https://github.com/ebean-orm/ebean-datasource/issues/116 for more details
 */
class ConnectionPoolCloseTest {

  private static ListAppender<ILoggingEvent> logCapture = new ListAppender<>();

  private static DataSourcePool pool = createPool(false, false);
  private static DataSourcePool poolRo = createPool(false, true);
  private static DataSourcePool poolEnforce = createPool(true, false);
  private static DataSourcePool poolEnforceRo = createPool(true, true);


  @BeforeAll
  static void before() {
    // attach logback appender to capture log messages
    ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger("io.ebean.datasource")).addAppender(logCapture);
    logCapture.start();
  }

  @BeforeEach
  void beforeEach() {
    logCapture.list.clear();
  }

  @AfterAll
  static void after() {
    logCapture.stop();
    pool.shutdown();
    poolRo.shutdown();
    poolEnforce.shutdown();
    poolEnforceRo.shutdown();
  }

  @Test
  void testNoCommitOrRollback() throws SQLException {

    doNoCommitOrRollback(pool);
    assertThat(getAndResetLogWarinings())
      .hasSize(1)
      .first().asString().startsWith("[WARN] Tried to close a dirty connection at");

    assertThatThrownBy(() -> doNoCommitOrRollback(poolEnforce)).isInstanceOf(AssertionError.class);

    doNoCommitOrRollback(poolRo);
    assertThat(getAndResetLogWarinings()).isEmpty();

    doNoCommitOrRollback(poolEnforceRo);
    assertThat(getAndResetLogWarinings()).isEmpty();

  }

  @Test
  void testCommit() throws SQLException {
    doCommit(pool);
    doCommit(poolRo);
    doCommit(poolEnforce);
    doCommit(poolEnforceRo);
    assertThat(getAndResetLogWarinings()).isEmpty();
  }

  @Test
  void testRollback() throws SQLException {
    doRollback(pool);
    doRollback(poolRo);
    doRollback(poolEnforce);
    doRollback(poolEnforceRo);
    assertThat(getAndResetLogWarinings()).isEmpty();
  }

  @Test
  void testExecuteValidSql() throws SQLException {
    doExecute(pool, "SELECT 1");
    doExecute(poolRo, "SELECT 1");
    doExecute(poolEnforce, "SELECT 1");
    doExecute(poolEnforceRo, "SELECT 1");
    assertThat(getAndResetLogWarinings()).isEmpty();
  }

  @Test
  void testExecuteInvalidSql() throws SQLException {
    assertThatThrownBy(() -> doExecute(pool, "invalid query"))
      .isInstanceOf(SQLSyntaxErrorException.class)
      .hasNoSuppressedExceptions();
    // (un)fortunately, we will log a missing rollback, when exception in try-with-resourches is thrown.
    assertThat(getAndResetLogWarinings())
      .hasSize(1)
      .first().asString().startsWith("[WARN] Tried to close a dirty connection at");

    assertThatThrownBy(() -> doExecute(poolEnforce, "invalid query"))
      .isInstanceOf(SQLSyntaxErrorException.class)
      .hasSuppressedException(new AssertionError("Tried to close a dirty connection. See https://github.com/ebean-orm/ebean-datasource/issues/116 for details."));

    assertThatThrownBy(() -> doExecute(poolRo, "invalid query"))
      .isInstanceOf(SQLSyntaxErrorException.class)
      .hasNoSuppressedExceptions();

    assertThatThrownBy(() -> doExecute(poolEnforceRo, "invalid query"))
      .isInstanceOf(SQLSyntaxErrorException.class)
      .hasNoSuppressedExceptions();
    assertThat(getAndResetLogWarinings()).isEmpty();
  }

  private static void doNoCommitOrRollback(DataSourcePool pool) throws SQLException {
    try (Connection connection = pool.getConnection()) {
      // we do nothing here.
    }
  }

  private static void doCommit(DataSourcePool pool) throws SQLException {
    try (Connection connection = pool.getConnection()) {
      connection.commit();
    }
  }

  private static void doRollback(DataSourcePool pool) throws SQLException {
    try (Connection connection = pool.getConnection()) {
      connection.rollback();
    }
  }

  private static void doExecute(DataSourcePool pool, String query) throws SQLException {
    try (Connection connection = pool.getConnection()) {
      connection.createStatement().execute(query);
      connection.commit();
    }
  }

  private static List<ILoggingEvent> getAndResetLogWarinings() {
    List<ILoggingEvent> warnings = logCapture.list.stream().filter(e -> e.getLevel().levelInt >= Level.WARN_INT).collect(Collectors.toList());
    logCapture.list.clear();
    return warnings;
  }

  private static DataSourcePool createPool(boolean enforceCleanClose, boolean readOnly) {
    DataSourcePool ret = DataSourceBuilder.create()
      .url("jdbc:h2:mem:tests")
      .username("sa")
      .password("")
      .minConnections(2)
      .maxConnections(4)
      .enforceCleanClose(enforceCleanClose)
      .readOnly(readOnly)
      .build();
    // clear capturer after create
    logCapture.list.clear();
    return ret;
  }
}
