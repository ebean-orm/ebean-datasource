package io.ebean.datasource.pool;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ExecutorFactory {

  static ExecutorService newExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }
}
