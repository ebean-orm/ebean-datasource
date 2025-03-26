package io.ebean.datasource.pool;

import java.util.*;

/**
 * A buffer designed especially to hold free pooled connections.
 * <p>
 * All thread safety controlled externally (by PooledConnectionQueue).
 * </p>
 */
final class FreeConnectionBuffer {


  private final Node free = Node.init();

  int size = 0;

  /**
   * Return the number of entries in the buffer.
   */
  int size() {
    return size;
  }

  /**
   * Return true if the buffer is empty.
   */
  boolean isEmpty() {
    return size == 0;
  }

  /**
   * Add connection to the free list.
   */
  void add(PooledConnection pc) {
    new Node(pc).addAfter(free);
    size++;
  }

  /**
   * Remove a connection from the free list.
   */
  PooledConnection remove() {
    Node node = free.next;
    node.remove();
    size--;
    return node.pc;
  }

  /**
   * Close all connections in this buffer.
   */
  void closeAll(boolean logErrors) {
    List<PooledConnection> tempList = new ArrayList<>();
    while (size > 0) {
      tempList.add(remove());
    }

    if (Log.isLoggable(System.Logger.Level.TRACE)) {
      Log.trace("... closing all {0} connections from the free list with logErrors: {1}", tempList.size(), logErrors);
    }
    for (PooledConnection connection : tempList) {
      connection.closeConnectionFully(logErrors);
    }
  }

  /**
   * Trim any inactive connections that have not been used since usedSince.
   */
  int trim(int minSize, long usedSince, long createdSince) {
    int trimCount = 0;
    Node node = free; // first boundary node
    do {
      node = node.next;
    } while (!node.isBoundaryNode() && minSize-- > 0);

    while (!node.isBoundaryNode()) {
      Node current = node;
      node = node.next;
      if (current.pc.shouldTrim(usedSince, createdSince)) {
        current.remove();
        size--;
        current.pc.closeConnectionFully(true);
        trimCount++;
      }
    }
    return trimCount;
  }

  /**
   * Node of a linkedlist. The linkedLists always have two empty nodes at the start and end.
   * (boundary nodes) They are generated with the init() method.
   * <p>
   * the first usable node is startNode.next (which could be the end edge)
   */
  static final class Node {

    private Node next;
    private Node prev;
    final PooledConnection pc;

    private Node(PooledConnection pc) {
      this.pc = pc;
    }

    /**
     * Creates new "list" with two empty boundary nodes
     */
    public static Node init() {
      Node node1 = new Node(null);
      Node node2 = new Node(null);
      node1.next = node2;
      node2.prev = node1;
      return node1;
    }

    /**
     * Retruns true, if this is a boundary node. (start or end node of list)
     */
    private boolean isBoundaryNode() {
      return pc == null;
    }

    /**
     * Removes the node from the list. The node can be re-added to an other list
     */
    private void remove() {
      assert pc != null : "called remove a boundary node";
      assert prev != null && next != null : "not part of a list";
      next.prev = prev;
      prev.next = next;
      prev = null;
      next = null;
    }

    /**
     * Adds <code>this</code> after <code>node</code>.
     * <p>
     * Node is in most cases a boundary node (e.g. start of list)
     */
    public void addAfter(Node node) {
      assert !this.isBoundaryNode() : "this is a boundary node";
      assert next == null & prev == null : "Node already member of a list";
      next = node.next;
      prev = node;
      node.next.prev = this;
      node.next = this;
    }
  }
}
