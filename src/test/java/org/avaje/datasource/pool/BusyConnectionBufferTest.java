package org.avaje.datasource.pool;


import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class BusyConnectionBufferTest {

  @Test
  public void test() {

    BusyConnectionBuffer b = new BusyConnectionBuffer(2, 4);

    PooledConnection p0 = new PooledConnection("0");
    PooledConnection p1 = new PooledConnection("1");
    PooledConnection p2 = new PooledConnection("2");
    PooledConnection p3 = new PooledConnection("3");

    assertEquals(2, b.getCapacity());
    b.add(p0);
    b.add(p1);
    assertEquals(2, b.getCapacity());
    b.add(p2);
    assertEquals(6, b.getCapacity());
    b.add(p3);

    assertEquals(0, p0.getSlotId());
    assertEquals(1, p1.getSlotId());
    assertEquals(2, p2.getSlotId());
    assertEquals(3, p3.getSlotId());

    b.remove(p2);
    b.add(p2);
    assertEquals(4, p2.getSlotId());

    b.remove(p0);
    b.add(p0);
    assertEquals(5, p0.getSlotId());

    b.remove(p2);
    b.add(p2);
    assertEquals(0, p2.getSlotId());

  }

  @Test
  public void test_rotate() {

    BusyConnectionBuffer b = new BusyConnectionBuffer(2, 2);

    PooledConnection p0 = new PooledConnection("0");
    PooledConnection p1 = new PooledConnection("1");
    PooledConnection p2 = new PooledConnection("2");
    PooledConnection p3 = new PooledConnection("3");

    assertEquals(2, b.getCapacity());
    assertEquals(0, b.size());

    b.add(p0);
    b.add(p1);
    assertEquals(2, b.size());
    assertEquals(2, b.getCapacity());
    b.add(p2);
    assertEquals(3, b.size());
    assertEquals(4, b.getCapacity());
    b.add(p3);
    assertEquals(4, b.size());
    assertEquals(4, b.getCapacity());

    assertEquals(0, p0.getSlotId());
    assertEquals(1, p1.getSlotId());
    assertEquals(2, p2.getSlotId());
    assertEquals(3, p3.getSlotId());

    b.remove(p2);
    assertEquals(3, b.size());
    b.remove(p0);
    assertEquals(2, b.size());
    b.remove(p3);
    assertEquals(1, b.size());
    b.add(p2);
    assertEquals(2, b.size());
    assertEquals(0, p2.getSlotId());

    b.remove(p0);
    assertEquals(2, b.size());
    b.add(p0);
    assertEquals(3, b.size());

    // p1 is still in it's slot
    assertEquals(2, p0.getSlotId());

    b.remove(p2);
    b.add(p2);
    assertEquals(3, p2.getSlotId());

  }

}
