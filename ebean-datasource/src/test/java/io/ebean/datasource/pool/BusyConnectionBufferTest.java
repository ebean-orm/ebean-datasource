package io.ebean.datasource.pool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BusyConnectionBufferTest {

  @Test
  public void test() {

    BusyConnectionBuffer b = new BusyConnectionBuffer(2, 4);

    PooledConnection p0 = new PooledConnection("0");
    PooledConnection p1 = new PooledConnection("1");
    PooledConnection p2 = new PooledConnection("2");
    PooledConnection p3 = new PooledConnection("3");

    assertEquals(2, b.capacity());
    b.add(p0);
    b.add(p1);
    assertEquals(2, b.capacity());
    b.add(p2);
    assertEquals(6, b.capacity());
    b.add(p3);

    assertEquals(0, p0.slotId());
    assertEquals(1, p1.slotId());
    assertEquals(2, p2.slotId());
    assertEquals(3, p3.slotId());

    b.remove(p2);
    b.add(p2);
    assertEquals(4, p2.slotId());

    b.remove(p0);
    b.add(p0);
    assertEquals(5, p0.slotId());

    b.remove(p2);
    b.add(p2);
    assertEquals(0, p2.slotId());

  }

  @Test
  public void test_rotate() {

    BusyConnectionBuffer b = new BusyConnectionBuffer(2, 2);

    PooledConnection p0 = new PooledConnection("0");
    PooledConnection p1 = new PooledConnection("1");
    PooledConnection p2 = new PooledConnection("2");
    PooledConnection p3 = new PooledConnection("3");

    assertEquals(2, b.capacity());
    assertEquals(0, b.size());

    b.add(p0);
    b.add(p1);
    assertEquals(2, b.size());
    assertEquals(2, b.capacity());
    b.add(p2);
    assertEquals(3, b.size());
    assertEquals(4, b.capacity());
    b.add(p3);
    assertEquals(4, b.size());
    assertEquals(4, b.capacity());

    assertEquals(0, p0.slotId());
    assertEquals(1, p1.slotId());
    assertEquals(2, p2.slotId());
    assertEquals(3, p3.slotId());

    b.remove(p2);
    assertEquals(3, b.size());
    b.remove(p0);
    assertEquals(2, b.size());
    b.remove(p3);
    assertEquals(1, b.size());
    b.add(p2);
    assertEquals(2, b.size());
    assertEquals(0, p2.slotId());

    b.remove(p0);
    assertEquals(2, b.size());
    b.add(p0);
    assertEquals(3, b.size());

    // p1 is still in it's slot
    assertEquals(2, p0.slotId());

    b.remove(p2);
    b.add(p2);
    assertEquals(3, p2.slotId());

  }

  @Test
  public void closeBusyConnections_onlyClosesLeaks_notActiveConnections() {
    BusyConnectionBuffer b = new BusyConnectionBuffer(4, 4);

    // a connection that was checked out long ago and never returned (a leak)
    PooledConnection leaked = new PooledConnection("leaked");
    // a connection that sat idle in the free list for a while then was just
    // checked out - it is in active use, not a leak (startUseTime is recent)
    PooledConnection active = new PooledConnection("active");
    active.resetForUse();

    b.add(leaked);
    b.add(active);
    assertEquals(2, b.size());

    // close connections considered leaked using a 1 minute leak time
    b.closeBusyConnections(1);

    // the leaked connection is removed, the active connection is retained
    assertEquals(1, b.size());
    // active is still held in its slot, leaked is gone
    assertTrue(b.remove(active));
    assertFalse(b.remove(leaked));
  }

}
