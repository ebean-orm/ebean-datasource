module io.ebean.datasource.api {

  requires transitive java.sql;
  requires transitive io.avaje.applog;

  uses io.ebean.datasource.DataSourceFactory;
  uses io.ebean.datasource.NewConnectionInitializer;

  exports io.ebean.datasource;
}
