package io.ebean.datasource.pool;

import io.ebean.datasource.pool.ConnectionPool.Heartbeat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

final class ExecutorFactory {

  static ExecutorService newExecutor() {
    ThreadFactory factory = Thread.ofVirtual().name("datasource.reaper").factory();
    return Executors.newThreadPerTaskExecutor(factory);
  }

  static Heartbeat newHeartBeat(ConnectionPool pool, int freqMillis) {
    return new VTHeartbeat(pool, freqMillis).start();
  }

  private static final class VTHeartbeat implements Heartbeat {

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ConnectionPool pool;
    private final int freqMillis;
    private final Thread thread;

    private VTHeartbeat(ConnectionPool pool, int freqMillis) {
      this.pool = pool;
      this.freqMillis = freqMillis;
      this.thread = Thread.ofVirtual()
              .name(nm(pool.name()))
              .unstarted(this::run);
    }

    private static String nm(String poolName) {
      return poolName.isEmpty() ? "datasource.heartbeat" : "datasource." + poolName + ".heartbeat";
    }

    private void run() {
      while (running.get()) {
        try {
          Thread.sleep(freqMillis);
          pool.heartbeat();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        } catch (Exception e) {
          // continue heartbeat
          Log.warn("Error during heartbeat", e);
        }
      }
    }

    private Heartbeat start() {
      running.set(true);
      thread.start();
      return this;
    }

    @Override
    public void stop() {
      running.set(false);
      thread.interrupt();
    }
  }
}
