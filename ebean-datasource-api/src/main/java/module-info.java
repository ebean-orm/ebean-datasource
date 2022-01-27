module io.ebean.datasource.api {

  requires transitive java.sql;

  uses io.ebean.datasource.DataSourceFactory;

  exports io.ebean.datasource;
}
