package io.ebean.datasource.pool;

import java.sql.Connection;

/**
 * Helper object that can convert between transaction isolation descriptions and values.
 */
final class TransactionIsolation {

  /**
   * Return the string description of the transaction isolation level specified.
   */
  static String description(int level) {
    switch (level) {
      case Connection.TRANSACTION_NONE:
        return "NONE";
      case Connection.TRANSACTION_READ_COMMITTED:
        return "READ_COMMITTED";
      case Connection.TRANSACTION_READ_UNCOMMITTED:
        return "READ_UNCOMMITTED";
      case Connection.TRANSACTION_REPEATABLE_READ:
        return "REPEATABLE_READ";
      case Connection.TRANSACTION_SERIALIZABLE:
        return "SERIALIZABLE";
      case -1:
        return "NotSet";
      default:
        return "UNKNOWN[" + level + "]";
    }
  }
}
