package io.ebean.datasource.pool;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.ListIterator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class FreeConnectionBufferTest {

  @Test
  void test() {

    FreeConnectionBuffer b = new FreeConnectionBuffer();

    PooledConnection p0 = new PooledConnection("0");
    PooledConnection p1 = new PooledConnection("1");
    PooledConnection p2 = new PooledConnection("2");
    // PooledConnection p3 = new PooledConnection("3");

    assertEquals(0, b.size());
    assertTrue(b.isEmpty());

    b.add(p0);

    assertEquals(1, b.size());
    assertFalse(b.isEmpty());

    PooledConnection r0 = b.remove();
    assertThat(p0).isSameAs(r0);

    assertEquals(0, b.size());
    assertTrue(b.isEmpty());

    b.add(p0);
    b.add(p1);
    b.add(p2);

    assertEquals(3, b.size());

    PooledConnection r1 = b.remove();
    assertSame(p2, r1);
    PooledConnection r2 = b.remove();
    assertSame(p1, r2);

    assertEquals(1, b.size());
    b.add(p2);
    assertEquals(2, b.size());
    PooledConnection r3 = b.remove();
    assertSame(p2, r3);
    assertEquals(1, b.size());
    PooledConnection r4 = b.remove();
    assertSame(p0, r4);
    assertEquals(0, b.size());

    b.add(p2);
    b.add(p1);
    b.add(p0);

    assertEquals(3, b.size());

    PooledConnection r5 = b.remove();
    assertSame(p0, r5);
    assertEquals(2, b.size());

    PooledConnection r6 = b.remove();
    assertSame(p1, r6);
    assertEquals(1, b.size());

    PooledConnection r7 = b.remove();
    assertSame(p2, r7);
    assertEquals(0, b.size());

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
