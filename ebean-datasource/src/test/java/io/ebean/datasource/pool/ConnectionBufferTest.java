package io.ebean.datasource.pool;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.ListIterator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

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
    assertThat(p0).isSameAs(r0);

    assertEquals(0, b.freeSize());
    assertFalse(b.hasFreeConnections());

    b.addFree(p0);
    b.addFree(p1);
    b.addFree(p2);

    assertEquals(3, b.freeSize());

    PooledConnection r1 = b.popFree();
    assertSame(p2, r1);
    PooledConnection r2 = b.popFree();
    assertSame(p1, r2);

    assertEquals(1, b.freeSize());
    b.addFree(p2);
    assertEquals(2, b.freeSize());
    PooledConnection r3 = b.popFree();
    assertSame(p2, r3);
    assertEquals(1, b.freeSize());
    PooledConnection r4 = b.popFree();
    assertSame(p0, r4);
    assertEquals(0, b.freeSize());

    b.addFree(p2);
    b.addFree(p1);
    b.addFree(p0);

    assertEquals(3, b.freeSize());

    PooledConnection r5 = b.popFree();
    assertSame(p0, r5);
    assertEquals(2, b.freeSize());

    PooledConnection r6 = b.popFree();
    assertSame(p1, r6);
    assertEquals(1, b.freeSize());

    PooledConnection r7 = b.popFree();
    assertSame(p2, r7);
    assertEquals(0, b.freeSize());

  }

  @Test
  void listIterator() {
    PooledConnection p0 = new PooledConnection("0");
    PooledConnection p1 = new PooledConnection("1");
    PooledConnection p2 = new PooledConnection("2");
    PooledConnection p3 = new PooledConnection("3");

    var list = new LinkedList<PooledConnection>();
    list.add(p0);
    list.add(p1);
    list.add(p2);
    list.add(p3);

    var set1 = listIterate(list, 1);
    assertThat(set1).hasSize(3);
    assertThat(set1).contains(p1, p2, p3);

    var set3 = listIterate(list, 3);
    assertThat(set3).hasSize(1);
    assertThat(set3).contains(p3);
  }

  private LinkedHashSet<PooledConnection> listIterate(LinkedList<PooledConnection> list, int position) {
    ListIterator<PooledConnection> it = list.listIterator(position);
    var set = new LinkedHashSet<PooledConnection>();
    while (it.hasNext()) {
      set.add(it.next());
    }
    return set;
  }

}
