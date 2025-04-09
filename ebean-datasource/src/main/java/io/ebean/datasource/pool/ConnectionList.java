package io.ebean.datasource.pool;

import java.util.Iterator;
import java.util.Objects;

/**
 * A linked list implementation designed for pooledConnections.
 * <p>
 * The linkedList supports adding and removing connections in constant time.
 * In contrast to the java.util.LinkedList, this linkedList provides access
 * to the list nodes. The nodes can be unlinked from one list and can be
 * added to another. This saves a bit overhead of creating new node objects
 * on each transition from free to busy list.
 * <p>
 * the list implements Iterable and <code>reverse()</code> for reverse traversion.
 *
 * @author Roland Praml, Foconis Analytics GmbH
 */
final class ConnectionList implements Iterable<PooledConnection> {

  private final Node head;
  private final Node tail;
  private int size;

  /**
   * Construct new list with two boundary nodes.
   */
  ConnectionList() {
    // initialize the two boundary nodes;
    head = new Node(null);
    tail = new Node(null);
    head.next = tail;
    tail.prev = head;
  }

  /**
   * Adds the node in front of the list. This works in constant time
   */
  void addFirst(Node n) {
    assert !n.isBoundaryNode() : "this is a boundary node";
    assert !n.isLinked() : "Node already member of a list";
    n.next = head.next;
    n.prev = head;
    head.next.prev = n;
    head.next = n;
    n.list = this;
    size++;
  }

  /**
   * returns the first element in the list or null if list is empty
   */
  PooledConnection peekFirst() {
    Node ret = head.next;
    return ret.isBoundaryNode() ? null : ret.pc;
  }

  /**
   * returns last first element in the list or null if list is empty
   */
  PooledConnection peekLast() {
    Node ret = tail.prev;
    return ret.isBoundaryNode() ? null : ret.pc;
  }

  /**
   * Iterates the list starting with first element.
   */
  public Iterator<PooledConnection> iterator() {
    return new Iterator<>() {
      private Node n = head.next;
      private PooledConnection pc;

      @Override
      public boolean hasNext() {
        return !n.isBoundaryNode();
      }

      @Override
      public PooledConnection next() {
        pc = n.pc;
        n = n.next;
        return pc;
      }

      @Override
      public void remove() {
        pc.unlink();
      }
    };
  }

  /**
   * Iterates the reverse way over the list
   */
  Iterable<PooledConnection> reverse() {
    return () -> new Iterator<>() {
      private Node n = tail.prev;
      private PooledConnection pc;

      @Override
      public boolean hasNext() {
        return !n.isBoundaryNode();
      }

      @Override
      public PooledConnection next() {
        pc = n.pc;
        n = n.prev;
        return pc;
      }

      @Override
      public void remove() {
        pc.unlink();
      }
    };
  }

  /**
   * Finds the node with this affinity id.
   */
  PooledConnection find(Object affinityId) {
    Node n = head.next;
    while (!n.isBoundaryNode()) {
      if (Objects.equals(affinityId, n.pc.affinityId())) {
        return n.pc;
      }
      n = n.next;
    }
    return null;
  }

  int size() {
    return size;
  }

  /**
   * Returns true, if this node is linked to that list.
   */
  boolean isLinkedTo(Node node) {
    // The implementation relies on <code>node.list == this</code>
    // which is guaranteed by the add/unlink mehtod.
    return node != null && node.list == this;
  }


  /**
   * Node of a linkedlist. The linkedLists always have two empty nodes
   * at the start and end. (boundary nodes)
   * <p>
   * the first usable node is startNode.next (which could be the end boundary)
   */
  static final class Node {

    private Node next;
    private Node prev;
    private ConnectionList list;
    private final PooledConnection pc;

    Node(PooledConnection pc) {
      this.pc = pc;
    }

    /**
     * Retruns true, if this is a boundary node. (start or end node of list)
     */
    private boolean isBoundaryNode() {
      return pc == null;
    }

    /**
     * Removes the node from the list. The node can be re-added to an other list.
     * <p>
     * Note: As PooledConnections are often in two lists, always use
     * PooledConnection.detach() instead of calling this method directly.
     */
    boolean unlink() {
      assert !isBoundaryNode() : "called remove on a boundary node";
      if (!isLinked()) {
        return false;
      }
      list.size--;
      next.prev = prev;
      prev.next = next;
      prev = null;
      next = null;
      list = null;
      return true;
    }

    /**
     * Returns true, if this node is linked in a list.
     */
    boolean isLinked() {
      return list != null;
    }
  }
}
