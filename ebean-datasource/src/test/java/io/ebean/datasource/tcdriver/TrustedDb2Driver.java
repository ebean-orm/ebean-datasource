package io.ebean.datasource.tcdriver;

import com.ibm.db2.jcc.DB2Connection;
import com.ibm.db2.jcc.DB2Driver;
import com.ibm.db2.jcc.DB2PooledConnection;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;

/**
 * There is no way to create a trusted connection by a JDBC connection string
 * (like jdbc:db2:...trusted=true), so this is a simple driver wrapper, that
 * allows us to get a DB2 trusted connection via JDBC-API
 * (e.g. jdbc:db2trusted://localhost:40005/database:someProp=someValue;)
 *
 * @author Noemi Praml, Foconis Analytics GmbH
 */
public class TrustedDb2Driver implements Driver {

  private DB2Driver delegate = new DB2Driver();

  static {
    try {
      new DB2Driver();
      DriverManager.registerDriver(new TrustedDb2Driver());
    } catch (SQLException e) {
      // eat
    }
  }

  @Override
  public Connection connect(String url, Properties info) throws SQLException {
    Properties properties = new Properties();
    properties.putAll(info);

    HostPortDb result;

    try {
      result = HostPortDb.parse(url, properties);
    } catch (URISyntaxException e) {
      throw new SQLException("Invalid url: " + url);
    }

    com.ibm.db2.jcc.DB2ConnectionPoolDataSource ds1 =
      new com.ibm.db2.jcc.DB2ConnectionPoolDataSource();
    ds1.setServerName(result.host);
    ds1.setPortNumber(result.port);
    ds1.setDatabaseName(result.dbName);
    ds1.setDriverType(4);
    ds1.setUser(properties.getProperty("user"));
    ds1.setPassword(properties.getProperty("password"));

    Object[] objects = ds1.getDB2TrustedPooledConnection(properties.getProperty("user"), properties.getProperty("password"), properties);
    DB2PooledConnection pooledCon = (DB2PooledConnection) objects[0];
    byte[] cookie = (byte[]) objects[1];

    return new TrustedDb2Connection((DB2Connection) pooledCon.getConnection(), cookie);
  }


  @Override
  public boolean acceptsURL(String url) throws SQLException {
    return (url != null && (url.startsWith("jdbc:db2trusted:")));
  }

  @Override
  public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
    return new DriverPropertyInfo[0];
  }

  @Override
  public int getMajorVersion() {
    return delegate.getMajorVersion();
  }

  @Override
  public int getMinorVersion() {
    return delegate.getMinorVersion();
  }

  @Override
  public boolean jdbcCompliant() {
    return delegate.jdbcCompliant();
  }

  @Override
  public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return delegate.getParentLogger();
  }


  /**
   * Helper, that parses the JDBC-URL like jdbc:db2trusted://localhost:40005/migtest:currentSchema=METRICSTASK2;
   * in host/port/db (similar! to DB2 syntax)
   */
  private static class HostPortDb {
    public final String host;
    public final int port;
    public final String dbName;


    public HostPortDb(String host, int port, String dbName) {
      this.host = host;
      this.port = port;
      this.dbName = dbName;
    }

    static HostPortDb parse(String url, Properties properties) throws URISyntaxException {
      assert url.startsWith("jdbc:");
      URI uri = new URI(url.substring(5));

      String host = uri.getHost();
      int port = uri.getPort();
      if (port == 0) {
        port = 50000;
      }

      String path = uri.getPath();
      if (path.startsWith("/")) {
        path = path.substring(1);
      }
      int colon = path.indexOf(':');
      String dbName = colon == -1 ? path : path.substring(0, colon);


      if (colon != -1) {
        String propertiesString = path.substring(colon + 1);

        String[] keyValuePairs = propertiesString.split(";");
        for (String pair : keyValuePairs) {
          String[] keyValue = pair.split("=", 2);
          if (keyValue.length == 2) {
            properties.setProperty(keyValue[0].trim(), keyValue[1].trim());
          }
        }
      }
      return new HostPortDb(host, port, dbName);
    }
  }
}
