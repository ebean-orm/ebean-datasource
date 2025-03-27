package io.ebean.datasource.pool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionBufferTest {

  @Test
  void test() {

    ConnectionBuffer b = new ConnectionBuffer(257);

    PooledConnection p0 = new PooledConnection("0");
    PooledConnection p1 = new PooledConnection("1");
    PooledConnection p2 = new PooledConnection("2");


    assertEquals(0, b.freeSize());
    assertFalse(b.hasFreeConnections());

    b.addFree(p0);

    assertEquals(1, b.freeSize());
    assertTrue(b.hasFreeConnections());

    PooledConnection r0 = b.popFree(null);
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

    PooledConnection r1 = b.popFree(null);
    b.addBusy(r1);
    assertSame(p2, r1);
    PooledConnection r2 = b.popFree(null);
    b.addBusy(r2);
    assertSame(p1, r2);

    assertEquals(1, b.freeSize());
    b.moveToFreeList(r1);

    assertEquals(2, b.freeSize());
    PooledConnection r3 = b.popFree(null);
    b.addBusy(r3);
    assertSame(p2, r3);
    assertEquals(1, b.freeSize());
    PooledConnection r4 = b.popFree(null);
    b.addBusy(r4);
    assertSame(p0, r4);
    assertEquals(0, b.freeSize());

    b.moveToFreeList(r3); // = p2
    b.moveToFreeList(r2); // = p1
    b.moveToFreeList(r4); // = p0

    assertEquals(3, b.freeSize());

    PooledConnection r5 = b.popFree(null);
    b.addBusy(r5);
    assertSame(p0, r5);
    assertEquals(2, b.freeSize());

    PooledConnection r6 = b.popFree(null);
    b.addBusy(r6);
    assertSame(p1, r6);
    assertEquals(1, b.freeSize());

    PooledConnection r7 = b.popFree(null);
    b.addBusy(r7);
    assertSame(p2, r7);
    assertEquals(0, b.freeSize());

  }


  @Test
  public void test_busy_free() {

    ConnectionBuffer b = new ConnectionBuffer(257);

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

    PooledConnection c3 = b.popFree(null);
    assertSame(p3, c3);
    assertEquals(2, b.busySize());
    assertEquals(1, b.freeSize());

    b.addBusy(c3);
    assertThatThrownBy(() -> b.addBusy(p3)).hasMessageContaining("Node already member of a list");
    assertEquals(3, b.busySize());

    PooledConnection c2 = b.popFree(null);
    b.addBusy(c2);
    assertSame(p2, c2);

    assertEquals(4, b.busySize());
    assertEquals(0, b.freeSize());

    assertNull(b.popFree(null)); // no free connections left

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

  @Test
  public void test_Affinity() {

    ConnectionBuffer b = new ConnectionBuffer(257);

    PooledConnection p0 = new PooledConnection("0");
    PooledConnection p1 = new PooledConnection("1");
    PooledConnection p2 = new PooledConnection("2");
    PooledConnection p3 = new PooledConnection("3");

    b.addFree(p0);
    b.addFree(p1);
    b.addFree(p2);
    b.addFree(p3);

    PooledConnection c1 = getConnection(b, 42);
    PooledConnection c2 = getConnection(b, 17);
    b.moveToFreeList(c1);
    b.moveToFreeList(c2);

    PooledConnection c3 = getConnection(b,43);
    assertNotSame(c3, c1);
    assertNotSame(c2, c1);

    PooledConnection c4 = getConnection(b,42);
    assertSame(c4, c1);
  }

  private static PooledConnection getConnection(ConnectionBuffer b, Object affinity) {
    PooledConnection c1 = b.popFree(affinity);
    if (c1 == null) {
      c1 = b.popFree(ConnectionBuffer.POP_LAST);
    }
    c1.setAffinityId(affinity);
    b.addBusy(c1);
    return c1;
  }

}
