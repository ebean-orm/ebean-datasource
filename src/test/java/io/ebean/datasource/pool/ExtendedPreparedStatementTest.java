package io.ebean.datasource.pool;

import io.ebean.datasource.DataSourceConfig;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ExtendedPreparedStatementTest {

	private ConnectionPool createPool() {

		DataSourceConfig config = new DataSourceConfig();
		config.setDriver("org.h2.Driver");
		config.setUrl("jdbc:h2:mem:tests");
		config.setUsername("sa");
		config.setPassword("");
		return new ConnectionPool("stmt", config);
	}

	@Test
	public void extraClose_expect_noObjectAlreadyClosedError() throws SQLException {

		ConnectionPool pool = createPool();
		try (Connection connection = pool.getConnection()) {
			try (PreparedStatement stmt = connection.prepareStatement("select CURRENT_TIMESTAMP")) {
				stmt.executeQuery();
				// this is an extra call to close()
				stmt.close();
			}
			try (PreparedStatement stmt = connection.prepareStatement("select CURRENT_TIMESTAMP")) {
				stmt.executeQuery();
				// this is an extra call to close()
				stmt.close();
			}
		}
	}
}
