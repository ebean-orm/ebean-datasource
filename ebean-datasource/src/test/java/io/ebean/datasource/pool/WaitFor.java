package io.ebean.datasource.pool;

import java.util.concurrent.TimeUnit;

import org.assertj.core.api.ThrowableAssert;

/**
 * Interface that can extend classes for tests, so a test can wait for a
 * condition to become true.
 *
 * @author Jonas P&ouml;hler, FOCONIS AG
 */
public interface WaitFor {

  /**
   * Tests if the action becomes correct (= will not throw errors) for up to 10
   * seconds. The check for correctness is performed every 50ms.
   *
   * Useful for tests which want to check the result of an asynchronous processing
   * thread without using Thread.sleep().
   */
  default void waitFor(final ThrowableAssert.ThrowingCallable action) {
    waitFor(action, TimeUnit.SECONDS.toMillis(10));
  }

  /**
   * Tests if the action becomes correct (= will not throw errors) during the
   * specified <code>millis</code>. The check for correctness is performed every
   * 50ms.
   *
   * Useful for tests which want to check the result of an asynchronous processing
   * thread without using Thread.sleep().
   */
  default void waitFor(final ThrowableAssert.ThrowingCallable action, final long millis) {
    long endNano = System.nanoTime() + millis * 1000000;
    int wait = 1;
    try {
      while (endNano > System.nanoTime()) {
        try {
          action.call();
          return;
        } catch (Throwable t) {
          Thread.sleep(wait);
          // double the wait time
          if (wait < 128) {
            wait = wait * 2;
          }
        }
      }
      action.call();
    } catch (Throwable t) {
      throw WaitFor.<RuntimeException>sneakyThrow(t); // hide real exception
      
    }
  }

  static <T extends Throwable> T sneakyThrow(Throwable t) throws T {
    throw (T) t;
  }

}
