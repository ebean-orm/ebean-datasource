package io.ebean.datasource.pool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionBufferTest {

  @Test
  void test() {

    ConnectionBuffer b = new ConnectionBuffer();

    PooledConnection p0 = new PooledConnection("0");
    PooledConnection p1 = new PooledConnection("1");
    PooledConnection p2 = new PooledConnection("2");
    // PooledConnection p3 = new PooledConnection("3");

    assertEquals(0, b.freeSize());
    assertFalse(b.hasFreeConnections());

    b.addFree(p0);

    assertEquals(1, b.freeSize());
    assertTrue(b.hasFreeConnections());

    PooledConnection r0 = b.popFree();
    b.addBusy(p0);
    assertThat(p0).isSameAs(r0);

    assertEquals(0, b.freeSize());
    assertFalse(b.hasFreeConnections());

    assertEquals(0, b.freeSize());
    assertEquals(1, b.busySize());
    b.moveToFreeList(p0);
    assertEquals(1, b.freeSize());
    assertEquals(0, b.busySize());

    b.addFree(p1);
    b.addFree(p2);

    assertEquals(3, b.freeSize());

    PooledConnection r1 = b.popFree();
    b.addBusy(r1);
    assertSame(p2, r1);
    PooledConnection r2 = b.popFree();
    b.addBusy(r2);
    assertSame(p1, r2);

    assertEquals(1, b.freeSize());
    b.moveToFreeList(r1);

    assertEquals(2, b.freeSize());
    PooledConnection r3 = b.popFree();
    b.addBusy(r3);
    assertSame(p2, r3);
    assertEquals(1, b.freeSize());
    PooledConnection r4 = b.popFree();
    b.addBusy(r4);
    assertSame(p0, r4);
    assertEquals(0, b.freeSize());

    b.moveToFreeList(r3); // = p2
    b.moveToFreeList(r2); // = p1
    b.moveToFreeList(r4); // = p0

    assertEquals(3, b.freeSize());

    PooledConnection r5 = b.popFree();
    b.addBusy(r5);
    assertSame(p0, r5);
    assertEquals(2, b.freeSize());

    PooledConnection r6 = b.popFree();
    b.addBusy(r6);
    assertSame(p1, r6);
    assertEquals(1, b.freeSize());

    PooledConnection r7 = b.popFree();
    b.addBusy(r7);
    assertSame(p2, r7);
    assertEquals(0, b.freeSize());

  }


  @Test
  public void test_busy_free() {

    ConnectionBuffer b = new ConnectionBuffer();

    PooledConnection p0 = new PooledConnection("0");
    PooledConnection p1 = new PooledConnection("1");
    PooledConnection p2 = new PooledConnection("2");
    PooledConnection p3 = new PooledConnection("3");

    assertEquals(0, b.busySize());
    assertEquals(0, b.freeSize());
    b.addBusy(p0);
    b.addBusy(p1);

    assertEquals(2, b.busySize());
    assertEquals(0, b.freeSize());

    b.addFree(p2);
    b.addFree(p3);

    assertEquals(2, b.busySize());
    assertEquals(2, b.freeSize());

    PooledConnection c3 = b.popFree();
    assertSame(p3, c3);
    assertEquals(2, b.busySize());
    assertEquals(1, b.freeSize());

    b.addBusy(c3);
    assertThatThrownBy(() -> b.addBusy(p3)).hasMessageContaining("Node already member of a list");
    assertEquals(3, b.busySize());

    PooledConnection c2 = b.popFree();
    assertSame(p2, c2);
    b.addBusy(c2);
    assertSame(p2, c2);

    assertEquals(4, b.busySize());
    assertEquals(0, b.freeSize());

    assertNull(b.popFree()); // no free connections left

    // all are busy now
    assertNotNull(p0.busyNode());
    assertNotNull(p1.busyNode());
    assertNotNull(p2.busyNode());
    assertNotNull(p3.busyNode());

    b.removeBusy(p0);
    assertEquals(3, b.busySize());
    assertEquals(0, b.freeSize());

    assertFalse(b.moveToFreeList(p0));
    assertTrue(b.moveToFreeList(p1));
    assertFalse(b.moveToFreeList(p1));

    assertEquals(2, b.busySize());
    assertEquals(1, b.freeSize());

    b.moveToFreeList(p2);
    b.moveToFreeList(p3);

    assertEquals(0, b.busySize());
    assertEquals(3, b.freeSize());
  }

}
