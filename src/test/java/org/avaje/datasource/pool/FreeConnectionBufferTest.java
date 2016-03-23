package org.avaje.datasource.pool;

import org.mockito.Mockito;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class FreeConnectionBufferTest {

  @Test
  public void test() {

    FreeConnectionBuffer b = new FreeConnectionBuffer();

    PooledConnection p0 = new PooledConnection("0");
    PooledConnection p1 = new PooledConnection("1");
    PooledConnection p2 = new PooledConnection("2");
    // PooledConnection p3 = new PooledConnection("3");

    assertEquals(0, b.size());
    assertEquals(true, b.isEmpty());

    b.add(p0);

    assertEquals(1, b.size());
    assertEquals(false, b.isEmpty());

    PooledConnection r0 = b.remove();
    assertTrue(p0 == r0);

    assertEquals(0, b.size());
    assertEquals(true, b.isEmpty());

    b.add(p0);
    b.add(p1);
    b.add(p2);

    assertEquals(3, b.size());

    PooledConnection r1 = b.remove();
    assertSame(p0, r1);
    PooledConnection r2 = b.remove();
    assertSame(p1, r2);

    assertEquals(1, b.size());
    b.add(p0);
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
    assertSame(p2, r5);
    assertEquals(2, b.size());

    PooledConnection r6 = b.remove();
    assertSame(p1, r6);
    assertEquals(1, b.size());

    PooledConnection r7 = b.remove();
    assertSame(p0, r7);
    assertEquals(0, b.size());

  }

  @Test
  public void trim_withTime() {

    FreeConnectionBuffer b = new FreeConnectionBuffer();
    assertEquals(0, b.size());

    PooledConnection p0 = Mockito.mock(PooledConnection.class);
    Mockito.when(p0.shouldTrim(1500, 0)).thenReturn(true);

    PooledConnection p1 = Mockito.mock(PooledConnection.class);
    Mockito.when(p1.shouldTrim(1500, 0)).thenReturn(true);

    PooledConnection p2 = Mockito.mock(PooledConnection.class);
    Mockito.when(p2.shouldTrim(1500, 0)).thenReturn(false);

    b.add(p0);
    b.add(p1);
    b.add(p2);

    assertEquals(3, b.size());

    int trimCount = b.trim(1500, 0);

    assertEquals(1, b.size());
    assertEquals(2, trimCount);
  }
}
