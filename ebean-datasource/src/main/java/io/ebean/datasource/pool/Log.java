package io.ebean.datasource.pool;

import io.avaje.applog.AppLog;

import static java.lang.System.Logger.Level.*;

/**
 * Common Logger instance for datasource implementation.
 */
final class Log {

  static final System.Logger log = AppLog.getLogger("io.ebean.datasource");

  static void debug(String message, Object... params) {
    log.log(DEBUG, message, params);
  }

  static void trace(String message, Object... params) {
    log.log(TRACE, message, params);
  }

  static void info(String message, Object... params) {
    log.log(INFO, message, params);
  }

  static void warn(String message, Object... params) {
    log.log(WARNING, message, params);
  }

  static void warn(String message, Throwable e) {
    log.log(WARNING, message, e);
  }

  static void error(String message, Object... params) {
    log.log(ERROR, message, params);
  }

  static void error(String message, Throwable e) {
    log.log(ERROR, message, e);
  }

  static boolean isLoggable(System.Logger.Level level) {
    return log.isLoggable(level);
  }
}
