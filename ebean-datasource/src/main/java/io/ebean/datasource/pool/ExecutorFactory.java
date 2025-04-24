package io.ebean.datasource.pool;

import io.ebean.datasource.pool.ConnectionPool.Heartbeat;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ExecutorFactory {

  static ExecutorService newExecutor() {
    return Executors.newSingleThreadExecutor();
  }

  /**
   * Return a new Heartbeat for the pool.
   */
  static Heartbeat newHeartBeat(ConnectionPool pool, int freqMillis) {
    final Timer timer = new Timer(pool.name() + ".heartbeat", true);
    timer.scheduleAtFixedRate(new HeartbeatTask(pool), freqMillis, freqMillis);
    return new TimerHeartbeat(timer);
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
