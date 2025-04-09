package io.ebean.datasource;

import java.sql.Connection;

/**
 * Interface for connection objects returned from the ebean-datasource connection pool
 *
 * @author Roland Praml, Foconis Analytics GmbH
 */
public interface DataSourceConnection extends Connection {

  /**
   * Returns the affinity-ID, this connection was assigned to.
   */
  Object affinityId();

}
