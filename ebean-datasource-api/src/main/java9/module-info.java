module io.ebean.datasource.api {

  requires transitive org.slf4j;
  requires transitive java.sql;

  uses io.ebean.datasource.DataSourceFactory;

  exports io.ebean.datasource;
}