module io.ebean.datasource {

  requires transitive java.sql;
  requires transitive io.ebean.datasource.api;

  provides io.ebean.datasource.DataSourceFactory with io.ebean.datasource.pool.ConnectionPoolFactory;
}