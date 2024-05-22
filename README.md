[![Build](https://github.com/ebean-orm/ebean-datasource/actions/workflows/build.yml/badge.svg)](https://github.com/ebean-orm/ebean-datasource/actions/workflows/build.yml)
[![Maven Central : ebean](https://maven-badges.herokuapp.com/maven-central/io.ebean/ebean-datasource/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.ebean/ebean-datasource)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/ebean-orm/ebean-datasource/blob/master/LICENSE)
[![JDK EA](https://github.com/ebean-orm/ebean-datasource/actions/workflows/jdk-ea.yml/badge.svg)](https://github.com/ebean-orm/ebean-datasource/actions/workflows/jdk-ea.yml)

# ebean-datasource
A robust and fast SQL DataSource implementation

### Example use:

```java
DataSourcePool pool = DataSourcePool.builder()
  .name("mypool")
  .url("jdbc:h2:mem:test")
  .username("sa")
  .password("")
  .build();
```

```java
DataSourcePool readOnlyPool = DataSourcePool.builder()
  .name("mypool")
  .url("jdbc:h2:mem:test")
  .username("sa")
  .password("")
  .readOnly(true)
  .autoCommit(true)
  .build();
```

Use like any java.sql.DataSource obtaining pooled connections
```java
    try (Connection connection = pool.getConnection()) {
      try (PreparedStatement stmt = connection.prepareStatement("create table junk (acol varchar(10))")) {
        stmt.execute();
        connection.commit();
      }
    }

```

For CRaC beforeCheckpoint() we can take the pool offline
closing the connections and stopping heart beat checking
```java
// take it offline
pool.offline();
```

For CRaC afterRestore() we can bring the pool online
creating the min number of connections and re-starting heart beat checking
```java
pool.online();
```

For explicit shutdown of the pool
```java
pool.shutdown();
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


### Java modules use

```java
module my.example {

  requires io.ebean.datasource;
  ...

}

```
