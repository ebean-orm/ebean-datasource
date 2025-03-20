package io.ebean.datasource.tcdriver;

import io.ebean.datasource.DataSourceConnection;
import io.ebean.datasource.DataSourcePool;
import io.ebean.datasource.DataSourcePoolListener;

import java.sql.SQLException;

/**
 * Listener, that sets up TrustedConnection properly
 *
 * @author Roland Praml, Foconis Analytics GmbH
 */
public class TrustedContextListener implements DataSourcePoolListener {
  private ThreadLocal<String> user = new ThreadLocal<>();
  private ThreadLocal<String> pass = new ThreadLocal<>();
  private ThreadLocal<String> schema = new ThreadLocal<>();

  @Override
  public void onAfterBorrowConnection(DataSourcePool pool, DataSourceConnection connection) {
    try {
      TrustedDb2Connection trustedDb2Connection = connection.unwrap(TrustedDb2Connection.class);
      if (trustedDb2Connection.switchUser(user.get(), pass.get())) {
        connection.clearPreparedStatementCache();
      }
      connection.setSchema(schema.get());
      //System.out.println("Switched to " + user.get() + ", Schema: " + schema.get());
    } catch (SQLException e) {
      throw new RuntimeException(e); // TODO: Allow throwing sqlException here
    }
  }

  public void setContext(String user, String pass, String schema) {
    this.user.set(user);
    this.pass.set(pass);
    this.schema.set(schema);
  }
}
