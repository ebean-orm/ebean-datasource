package io.ebean.datasource.test;

import io.ebean.test.containers.Db2Container;
import io.ebean.test.containers.MariaDBContainer;
import io.ebean.test.containers.PostgresContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * This testsuite demonstrates, how a driver behaves, when a network outage occurs.
 *
 * @author Roland Praml, Foconis Analytics GmbH
 */
@Disabled
public class NetworkOutageTest {

  static void openPort(int port) throws Exception {
    Runtime.getRuntime().exec("sudo iptables -D OUTPUT -p tcp --dport " + port + " -j DROP").waitFor(); // remove rule
  }

  static void closePort(int port) throws Exception {
    System.out.println("Closing port " + port);
    Runtime.getRuntime().exec("sudo iptables -A OUTPUT -p tcp --dport " + port + " -j DROP").waitFor(); // remove rule
  }

  @BeforeAll
  @AfterAll
  static void removeRules() throws Exception {
    openPort(3306);
    openPort(50000);
    openPort(5432);
  }

  @Test
  @Disabled
  void testNetworkOutageDb2() throws Exception {
    Db2Container container = Db2Container.builder("latest").build();
    container.start();

    Connection conn = container.createConnection();
    PreparedStatement stmt = conn.prepareStatement("SELECT * from SYSCAT.TABLES");
    stmt.setFetchSize(10);
    ResultSet rs = stmt.executeQuery();
    rs.next();
    rs.next(); // read two rows. Now simulate network outage

    closePort(50000);

    long startTime = System.currentTimeMillis();
    stmt.close();  // this will block 1 minute or even longer
    conn.close();
    System.out.println("Done in " + (System.currentTimeMillis() - startTime) + "ms");

  }

  @Test
  @Disabled
  void testNetworkOutageMariaDb() throws Exception {
    MariaDBContainer container = MariaDBContainer.builder("latest").build();
    container.start();

    Connection conn = container.createConnection();
    PreparedStatement stmt = conn.prepareStatement("SELECT * from INFORMATION_SCHEMA.TABLES");
    stmt.setFetchSize(10);
    ResultSet rs = stmt.executeQuery();
    rs.next();
    rs.next(); // read two rows. Now simulate network outage

    closePort(3306);

    long startTime = System.currentTimeMillis();
    stmt.close();
    conn.close();
    // mariadb is graceful here. It slows down 3-4 ms
    System.out.println("Done in " + (System.currentTimeMillis() - startTime) + "ms");

  }

  @Test
  @Disabled
  void testNetworkOutagePostgres() throws Exception {
    PostgresContainer container = PostgresContainer.builder("latest").build();
    container.start();

    Connection conn = container.createConnection();
    PreparedStatement stmt = conn.prepareStatement("SELECT * from INFORMATION_SCHEMA.TABLES");
    stmt.setFetchSize(10);
    ResultSet rs = stmt.executeQuery();
    rs.next();
    rs.next(); // read two rows. Now simulate network outage

    closePort(5432);

    long startTime = System.currentTimeMillis();
    stmt.close();
    conn.close();
    // there is nearly no delay in PG
    System.out.println("Done in " + (System.currentTimeMillis() - startTime) + "ms");

  }

}
