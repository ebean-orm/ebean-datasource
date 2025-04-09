package io.ebean.datasource.pool;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectionListTest {

  /**
   * Test adding and unlinking from connectionList.
   */
  @Test
  void test() {

    ConnectionList l1 = new ConnectionList();
    ConnectionList l2 = new ConnectionList();

    PooledConnection p0 = new PooledConnection("0");
    p0.setAffinityId(43);
    PooledConnection p1 = new PooledConnection("1");
    p1.setAffinityId(44);
    PooledConnection p2 = new PooledConnection("2");
    p2.setAffinityId(45);

    assertThat(l1.size()).isEqualTo(0);
    assertThat(l1.peekFirst()).isNull();
    assertThat(l1.peekLast()).isNull();
    assertThat(l1.find(42)).isNull();


    l1.addFirst(p0.busyFree());
    l2.addFirst(p1.affinity());

    assertThat(l1.size()).isEqualTo(1);
    assertThat(l1.peekFirst()).isSameAs(p0);
    assertThat(l1.peekLast()).isSameAs(p0);
    assertThat(l1.find(42)).isNull();
    assertThat(l1.find(43)).isSameAs(p0);

    assertThat(l2.size()).isEqualTo(1);

    l1.addFirst(p1.busyFree());
    l1.addFirst(p2.busyFree());

    assertThat(l1.size()).isEqualTo(3);
    assertThat(l2.size()).isEqualTo(1);

    assertThat(l1).containsExactly(p2, p1, p0);
    assertThat(l1.reverse()).containsExactly(p0, p1, p2);

    p0.unlink();
    assertThat(l1.size()).isEqualTo(2);
    assertThat(l2.size()).isEqualTo(1);

    p1.unlink(); // p1 is member of both lists
    assertThat(l1.size()).isEqualTo(1);
    assertThat(l2.size()).isEqualTo(0);

    ConnectionList l3 = new ConnectionList();

    // adding an already linked conncection will throw an error
    assertThatThrownBy(() -> l3.addFirst(p2.busyFree()))
      .isInstanceOf(AssertionError.class)
      .hasMessageContaining("Node already member of a list");

    assertThat(l3.size()).isEqualTo(0);

    p2.unlink();
    l3.addFirst(p2.busyFree());
    assertThat(l3.size()).isEqualTo(1);

    p2.unlink();
    p2.unlink(); // subsequent unlink must not throw error
    l2.addFirst(p2.busyFree());
    assertThat(l2.size()).isEqualTo(1);
    assertThat(l3.size()).isEqualTo(0);
  }

}
