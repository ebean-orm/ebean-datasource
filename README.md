[![Build](https://github.com/ebean-orm/ebean-datasource/actions/workflows/build.yml/badge.svg)](https://github.com/ebean-orm/ebean-datasource/actions/workflows/build.yml)
[![Maven Central : ebean](https://maven-badges.herokuapp.com/maven-central/io.ebean/ebean-datasource/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.ebean/ebean-datasource)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/ebean-orm/ebean-datasource/blob/master/LICENSE)
[![JDK EA](https://github.com/ebean-orm/ebean-datasource/actions/workflows/jdk-ea.yml/badge.svg)](https://github.com/ebean-orm/ebean-datasource/actions/workflows/jdk-ea.yml)

# ebean-datasource
Implementation of ebean-datasource-api - a SQL DataSource implementation


### Example use:

```java

    DataSourceConfig config = new DataSourceConfig();
    config.setUrl("jdbc:postgresql://127.0.0.1:5432/unit");
    config.setUsername("foo");
    config.setPassword("bar");


    DataSource pool = DataSourceFactory.create("app", config);

```


### Robust and fast

This pool is robust in terms of handling loss of connectivity to the database and restoring connectivity.
It will automatically reset itself as needed.

This pool is fast and simple. It uses a strategy of testing connections in the background and when connections
are returned to the pool that have throw SQLException. This makes the connection testing strategy low overhead
but also robust.



### Mature

This pool has been is heavy use for more that 10 years and stable since April 2010 (the last major refactor to use  `java.util.concurrent.locks`).

This pool was previously part of Ebean ORM with prior history in sourceforge.

There are other good DataSource pools out there but this pool has proven to be fast, simple and robust and maintains it's status as the preferred pool for use with Ebean ORM.


### JPMS use

```java
module example {

  requires io.ebean.datasource;
  ...

}

```
