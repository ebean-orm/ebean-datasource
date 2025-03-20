package io.ebean.datasource;

import java.sql.Connection;

/**
 * @author Roland Praml, Foconis Analytics GmbH
 */
public interface DataSourceConnection extends Connection {

  void clearPreparedStatementCache();
}
