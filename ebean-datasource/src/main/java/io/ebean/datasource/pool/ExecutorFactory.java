package io.ebean.datasource.pool;

import io.ebean.datasource.pool.ConnectionPool.Heartbeat;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

final class ExecutorFactory {

  static ExecutorService newExecutor() {
    return Executors.newSingleThreadExecutor(factory());
  }

  private static ThreadFactory factory() {
    return runnable -> {
      Thread thread = new Thread(runnable);
      thread.setName("datasource.reaper");
      return thread;
    };
  }

  /**
   * Return a new Heartbeat for the pool.
   */
  static Heartbeat newHeartBeat(ConnectionPool pool, int freqMillis) {
    final Timer timer = new Timer(nm(pool.name()), true);
    timer.scheduleAtFixedRate(new HeartbeatTask(pool), freqMillis, freqMillis);
    return new TimerHeartbeat(timer);
  }

  private static String nm(String poolName) {
    return poolName.isEmpty() ? "datasource.heartbeat" : "datasource." + poolName + ".heartbeat";
  }

  private static final class TimerHeartbeat implements Heartbeat {

    private final Timer timer;

    private TimerHeartbeat(Timer timer) {
      this.timer = timer;
    }

    @Override
    public void stop() {
      timer.cancel();
    }
  }

  private static final class HeartbeatTask extends TimerTask {

    private final ConnectionPool pool;

    private HeartbeatTask(ConnectionPool pool) {
      this.pool = pool;
    }

    @Override
    public void run() {
      pool.heartbeat();
    }
  }
}
