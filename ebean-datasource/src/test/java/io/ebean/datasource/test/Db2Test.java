package io.ebean.datasource.test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ibm.db2.jcc.DB2Connection;
import io.avaje.applog.AppLog;
import io.ebean.datasource.DataSourceBuilder;
import io.ebean.datasource.DataSourcePool;
import io.ebean.datasource.DataSourcePoolListener;
import io.ebean.test.containers.Db2Container;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB2 has a strange behaviour, when a connection is in a dirty state and neither committed nor rolled back.
 * <p>
 * By default, a transaction cannot be closed if it is in an unit of work and an exception is thrown.
 * <p>
 * This can be controlled with the "connectionCloseWithInFlightTransaction" parameter
 * https://www.ibm.com/docs/en/db2/11.5?topic=pdsdjs-common-data-server-driver-jdbc-sqlj-properties-all-database-products
 * <p>
 * There are several cases, when there is an open unit of work:
 * <ul>
 *   <li>forget commit/rollback before closing the connection, because an exception occurs</li>
 *   <li>calling connection.getSchema() starts a new UOW (because it internally executes a query)</li>
 * </ul>
 */
class Db2Test {

	private static Db2Container container;

	private static ListAppender<ILoggingEvent> logCapture = new ListAppender<>();

	@BeforeAll
	static void before() {
		container = Db2Container.builder("11.5.6.0a")
				.dbName("unit")
				.user("unit")
				.password("unit")
				// to change collation, charset and other parameters like pagesize:
				.configOptions("USING CODESET UTF-8 TERRITORY DE COLLATE USING IDENTITY PAGESIZE 32768")
				.configOptions("USING STRING_UNITS CODEUNITS32")
				.build();

		// container.startWithDropCreate();
		container.start();

		// attach logback appender to capture log messages
		((ch.qos.logback.classic.Logger) LoggerFactory.getLogger("io.ebean.datasource")).addAppender(logCapture);
		logCapture.start();
	}

	@AfterAll
	static void after() {
		//   container.stopRemove();
		logCapture.stop();
	}

	@Test
	void testNoCommitOrRollback() throws SQLException {

		DataSourcePool pool = getPool();

		logCapture.list.clear();
		try {
			try (Connection connection = pool.getConnection()) {
				// we do nothing here.
			}
		} finally {
			pool.shutdown();
		}
		assertThat(logCapture.list.stream().filter(e -> e.getLevel().levelInt >= Level.WARN_INT)).isEmpty();
		assertThat(logCapture.list)
				.extracting(ILoggingEvent::toString).contains("[TRACE] Closing active connection. Rollback performed.");
	}


	@Test
	void testFalseFriendRollback() throws SQLException {

		DataSourcePool pool = getPool();

		logCapture.list.clear();
		try {
			try (Connection connection = pool.getConnection()) {
				// we do a rollback here
				connection.rollback();
				connection.getSchema(); // will re-open a new UOW
			}
		} finally {
			pool.shutdown();
		}

		assertThat(logCapture.list.stream().filter(e -> e.getLevel().levelInt >= Level.WARN_INT))
				.extracting(ILoggingEvent::toString).containsExactly("[WARN] There is a DB2 UOW open!");
		assertThat(logCapture.list)
				.extracting(ILoggingEvent::toString).doesNotContain("[TRACE] Closing active connection. Rollback performed.");
	}

	@Test
	void testProperUse() throws SQLException {

		DataSourcePool pool = getPool();

		logCapture.list.clear();
		try {
			try (Connection connection = pool.getConnection()) {
				connection.commit();
			}
		} finally {
			pool.shutdown();
		}

		assertThat(logCapture.list.stream().filter(e -> e.getLevel().levelInt >= Level.WARN_INT)).isEmpty();
		assertThat(logCapture.list)
				.extracting(ILoggingEvent::toString).doesNotContain("[TRACE] Closing active connection. Rollback performed.");

	}

	@Test
	void testErrorOccured() throws SQLException {

		DataSourcePool pool = getPool();

		logCapture.list.clear();
		try {
			try (Connection connection = pool.getConnection()) {
				try (PreparedStatement statement = connection.prepareStatement("i am invalid")) {
					statement.execute();
				}
				connection.commit(); // we will not get here
			} catch (SQLException e) {
				// expected
			}
		} finally {
			pool.shutdown();
		}

		assertThat(logCapture.list.stream().filter(e -> e.getLevel().levelInt >= Level.WARN_INT)).isEmpty();
		assertThat(logCapture.list)
				.extracting(ILoggingEvent::toString).contains("[TRACE] Closing active connection. Rollback performed.");
	}


	@Test
	void testProperShutdownWithMissingListener() throws SQLException {

		DataSourcePool pool = DataSourceBuilder.create()
				.url(container.jdbcUrl())
				.username("unit")
				.password("unit")
				.ownerUsername("unit")
				.ownerPassword("unit")
				.closeWithinTxn("rollback")
				.build();

		logCapture.list.clear();
		try {
			try (Connection connection = pool.getConnection()) {
				// we do a rollback here
				connection.rollback();
				connection.getSchema();
			}
		} finally {
			pool.shutdown();
		}

		assertThat(logCapture.list.stream().filter(e -> e.getLevel().levelInt >= Level.WARN_INT)).isEmpty();
		assertThat(logCapture.list)
				.extracting(ILoggingEvent::toString).doesNotContain("[TRACE] Closing active connection. Rollback performed.");

	}

	private static DataSourcePool getPool() {
		return DataSourceBuilder.create()
				.url(container.jdbcUrl())
				.username("unit")
				.password("unit")
				.ownerUsername("unit")
				.ownerPassword("unit")
				.closeWithinTxn("rollback")
				.listener(new DataSourcePoolListener() {
					@Override
					public void onBeforeReturnConnection(Connection connection) {
						try {
							DB2Connection db2conn = connection.unwrap(DB2Connection.class);
							if (db2conn.isInDB2UnitOfWork()) {
								AppLog.getLogger("io.ebean.datasource").log(System.Logger.Level.WARNING, "There is a DB2 UOW open!");
								db2conn.rollback();
							}
						} catch (SQLException e) {
							throw new RuntimeException(e);
						}
					}
				})
				.build();
	}
}
