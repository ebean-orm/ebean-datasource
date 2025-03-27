package io.ebean.datasource.pool;

import java.util.*;

/**
 * A buffer designed especially to hold pooled connections (free and busy ones)
 * <p>
 * The buffer contains two linkedLists (free and busy connection nodes)
 * <p>
 * When a node from the free list is removed, the node is attached to the
 * PooledConnection, so that the node object can be reused. This avoids object
 * creation/gc during remove operations.
 * <p>
 * The connectionbuffer iself has one linkedList from <code>free</code> to
 * <code>freeEnd</code>. In parallel, the elements in this list can also be part
 * the affinityNodes list, which implement a kind of hashmap.
 * <p>
 * So you can prefer which connection should be taken. You can use CurrentThread or
 * currentTenant as affinity ID. So you likely get a connection that has the right
 * pstatement caches or is already in the CPU cache.
 * <p>
 * Without affinityId, the first free-connection is taken.
 * <p>
 * With affinityId, the affinityNodes-list is determined by the hashCode, then the
 * list is searched, if there is a connection with the same affinity object.
 * <p>
 * If there is no one found, we take the LAST connection in freeList, as this is
 * the best candidate not to steal the affinity of a connection, that was currently
 * used. This ensures (or also causes) that the pool has at least that size of the
 * frequent used affinityIds. E.g. if the affinity id represents tenant id, and
 * 15 tenants are active, the pool will not shrink below 15 - on the other hand,
 * there is always one connection ready for each active tenant.
 * <p>
 * A free node can be member in two lists:
 * <ol>
 *     <li>it is definitively member in the freeList</li>
 *     <li>it may be member in one of the affinity-lists (mod hash)</li>
 * </ol>
 * The remove / transition from free to busy will remove the node from both lists.
 * <p>
 * Graphical exammple
 * <pre>
 *     By default, the busy list is empty
 *     busy ---------------------------------------------------> busyEnd
 *     free --> c1 --> c2 --> c3 --> c4 --> c5 --> c6 --> c7 --> freeEnd
 *     al1  ---------------------------------------------------> end
 *     al2  ---------------------------------------------------> end
 *     ...
 *     al257---------------------------------------------------> end
 *
 *     if a popFree(1) is called, we lookup in al1 and found no usable node.
 *     in this case, we take the last node, c7 and move it to the busy list
 *
 *     busy --> c7 --------------------------------------------> busyEnd
 *     free --> c1 --> c2 --> c3 --> c4 --> c5 --> c6 ---------> freeEnd
 *
 *     When we put that node back in the freelist, it becomes the first node
 *     and it will be also linked in affinity-list1
 *
 *     busy ---------------------------------------------------> busyEnd
 *     free --> c7 --> c1 --> c2 --> c3 --> c4 --> c5 --> c6 --> freeEnd
 *     al1  --> c7 --> end  (the node for c7 is in 'free' and 'al1')
 *     al2-> (empty)
 *
 *     subsequent popFree(1) will always return c7 as long as it is not busy.
 *     now we call popFree(1) twice, we will get this picture
 *
 *     busy --> c6 --> c7 ----------------------------------------> busyEnd
 *     free --> c1 --> c2 --> c3 --> c4 --> c5 -------------------> freeEnd
 *     al1-> (empty)
 *     al2-> (empty)
 *
 *     putting them back
 *
 *     busy ------------------------------------------------------> busyEnd
 *     free --> c7 --> c6 --> c1 --> c2 --> c3 --> c4 --> c5 -----> freeEnd
 *     al1  --> c7 --> c6 --> end
 *     al2-> (empty)
 *
 *     fetching a connection with affinity = 2 will remove c5
 *     (we take connection from the end, as the front of the list may
 *     contain 'hot' connections. c7 would be a bad choice here
 *
 *     busy --> c5 -----------------------------------------------> busyEnd
 *     free --> c7 --> c6 --> c1 --> c2 --> c3 --> c4 ------------> freeEnd
 *     al1  --> c7 --> c6 --> end
 *     al2-> (empty)
 *
 *     putting c5 back results in this list
 *
 *     busy ------------------------------------------------------> busyEnd
 *     free --> c5 --> c7 --> c6 --> c1 --> c2 --> c3 --> c4 -----> freeEnd
 *     al1  ---------> c7 --> c6 --> end
 *     al2  --> c5 ----------------> end
 *
 *     so we have 2 connections for affinity 1 and one connection for affinity 2
 *     (and the rest is ordered itself in the freeList)
 *
 *     when we now fetch a connection for affinity = 1 we will get c7:
 *
 *     busy --> c7 -----------------------------------------------> busyEnd
 *     free --> c5 ---------> c6 --> c1 --> c2 --> c3 --> c4 -----> freeEnd
 *     al1  ----------------> c6 --> end
 *     al2  --> c5 ----------------> end
 *
 *     putting c7 back will add the connection back to freelist and affinity
 *     list 1
 *
 *     busy ------------------------------------------------------> busyEnd
 *     free --> c7 --> c5 --> c6 --> c1 --> c2 --> c3 --> c4 -----> freeEnd
 *     al1  --> c7 ---------> c6 --> end
 *     al2  ---------> c5 ---------> end
 *
 *     when we now only fetch connections with affinity id 1 and 2, we will
 *     always get c7/c5 and the pool can trim c6,c1,c2,c3,c4
 * </pre>
 * <p>
 * All thread safety controlled externally (by PooledConnectionQueue).
 * </p>
 */
final class ConnectionBuffer {

  static final Object POP_LAST = new Object();

  private final Node free = Node.init();
  private final Node freeEnd = free.next;
  private final Node busy = Node.init();

  private final Node[] affinityNodes;
  private final int hashSize;

  ConnectionBuffer(int hashSize) {
    this.hashSize = hashSize;
    if (hashSize > 0) {
      affinityNodes = new Node[hashSize];
      for (int i = 0; i < affinityNodes.length; i++) {
        affinityNodes[i] = Node.init();
      }
    } else {
      affinityNodes = null;
    }
  }

  int freeSize = 0;
  int busySize = 0;

  /**
   * Return the number of entries in the buffer.
   */
  int freeSize() {
    return freeSize;
  }

  /**
   * Return the number of busy connections.
   */
  int busySize() {
    return busySize;
  }

  /**
   * Return true if the buffer is empty.
   */
  boolean hasFreeConnections() {
    return freeSize > 0;
  }

  /**
   * Adds a new connection to the free list.
   */
  void addFree(PooledConnection pc) {
    assert pc.busyNode() == null : "Connection seems not to be new";
    new Node(pc).addAfter(free);
    freeSize++;
  }

  /**
   * Removes the connection from the busy list. (For full close)
   * Returns true, if this connection was part of the busy list or false, if not (or removed twice)
   */
  boolean removeBusy(PooledConnection c) {
    Node node = c.busyNode();
    if (node == null || node.next == null) {
      // node is not yet or no longer in busy list
      return false;
    }
    node.remove();
    busySize--;
    c.setBusyNode(null);
    return true;
  }

  /**
   * Moves the connection from the busy list to the free list.
   */
  boolean moveToFreeList(PooledConnection c) {
    Node node = c.busyNode();
    if (node == null) {
      return false;
    }
    node.remove();
    busySize--;
    if (affinityNodes != null && c.affinityId() != null) {
      node.addAfter(free, affinityNodes[c.affinityId().hashCode() % hashSize]);
    } else {
      node.addAfter(free);
    }
    freeSize++;
    c.setBusyNode(null);
    return true;
  }

  /**
   * Remove a connection from the free list. Returns <code>null</code> if there is not any.
   * <p>
   * Connections that are returend from this method must be either added to busyList with
   * addBusy or closed fully.
   *
   * @param affinityId the preferred affinity-id.
   *                   If <code>null</code> is provided, the first element in the list is
   *                   returned.
   *                   If the affinity-id is not present in the list, <code>null</code>
   *                   is returned. The caller can decide to create a new connection or
   *                   ask again with <code>POP_LAST</code>, which returns the last
   *                   (=oldest) connection if affinity is enabled.
   */
  PooledConnection popFree(Object affinityId) {
    Node node;
    if (affinityId == null || affinityNodes == null) {
      node = free.next;
    } else if (affinityId == POP_LAST) {
      node = freeEnd.prev;
    } else {
      node = affinityNodes[affinityId.hashCode() % hashSize].find(affinityId);
      if (node == null) {
        // when we did not find a node with that affinity, we return null
        // this allows the pool to grow to its maximum size
        return null;
      }
    }
    if (node.isBoundaryNode()) {
      return null;
    }
    node.remove();
    freeSize--;
    node.pc.setBusyNode(node); // sets the node for reuse in "addBusy"
    return node.pc;
  }

  /**
   * Adds the connection to the busy list. The connection must be either new or popped from the free list.
   */
  int addBusy(PooledConnection c) {
    Node node = c.busyNode(); // we try to reuse the node to avoid object creation.
    if (node == null) {
      node = new Node(c);
      c.setBusyNode(node);
    }
    node.addAfter(busy);
    busySize++;
    return busySize;
  }

  /**
   * Close all free connections in this buffer.
   */
  void closeAllFree(boolean logErrors) {
    List<PooledConnection> tempList = new ArrayList<>();
    PooledConnection c = popFree(null);
    while (c != null) {
      tempList.add(c);
      c = popFree(null);
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
        freeSize--;
        current.pc.closeConnectionFully(true);
        trimCount++;
      }
    }
    return trimCount;
  }

  /**
   * Close connections that should be considered leaked.
   */
  void closeBusyConnections(long leakTimeMinutes) {
    long olderThanTime = System.currentTimeMillis() - (leakTimeMinutes * 60000);
    Log.debug("Closing busy connections using leakTimeMinutes {0}", leakTimeMinutes);
    Node node = busy.next;
    while (!node.isBoundaryNode()) {
      Node current = node;
      node = node.next;

      PooledConnection pc = current.pc;
      //noinspection StatementWithEmptyBody
      if (pc.lastUsedTime() > olderThanTime) {
        // PooledConnection has been used recently or
        // expected to be longRunning so not closing...
      } else {
        current.remove();
        --busySize;
        closeBusyConnection(pc);
      }
    }
  }

  private void closeBusyConnection(PooledConnection pc) {
    try {
      Log.warn("DataSource closing busy connection? {0}", pc.fullDescription());
      System.out.println("CLOSING busy connection: " + pc.fullDescription());
      pc.closeConnectionFully(false);
    } catch (Exception ex) {
      Log.error("Error when closing potentially leaked connection " + pc.description(), ex);
    }
  }

  /**
   * Returns information describing connections that are currently being used.
   */
  String busyConnectionInformation(boolean toLogger) {
    if (toLogger) {
      Log.info("Dumping [{0}] busy connections: (Use datasource.xxx.capturestacktrace=true  ... to get stackTraces)", busySize());
    }
    StringBuilder sb = new StringBuilder();
    Node node = busy.next;
    while (!node.isBoundaryNode()) {
      PooledConnection pc = node.pc;
      node = node.next;
      if (toLogger) {
        Log.info("Busy Connection - {0}", pc.fullDescription());
      } else {
        sb.append(pc.fullDescription()).append("\r\n");
      }
    }
    return sb.toString();
  }


  /**
   * Node of a linkedlist. The linkedLists always have two empty nodes at the start and end.
   * (boundary nodes) They are generated with the init() method.
   * <p>
   * the first usable node is startNode.next (which could be the end boundary)
   */
  static final class Node {

    private Node next;
    private Node prev;
    // Double-LL nodes for affinity management
    private Node afNext;
    private Node afPrev;
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
      node1.afNext = node2;
      node2.afPrev = node1;
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
      assert pc != null : "called remove on a boundary node";
      assert prev != null && next != null : "not part of a list";
      next.prev = prev;
      prev.next = next;
      prev = null;
      next = null;
      if (afNext != null) {
        afNext.afPrev = afPrev;
        afPrev.afNext = afNext;
        afPrev = null;
        afNext = null;
      }
    }

    /**
     * Adds <code>this</code> after <code>node</code>.
     * <p>
     * Node is in most cases a boundary node (e.g. start of list)
     */
    public void addAfter(Node node) {
      assert !this.isBoundaryNode() : "this is a boundary node";
      assert next == null && prev == null : "Node already member of a list";
      next = node.next;
      prev = node;
      node.next.prev = this;
      node.next = this;
    }

    /**
     * Adds <code>this</code> after <code>node</code> AND as affinity-node after <code>afNode</code>.
     */
    public void addAfter(Node node, Node afNode) {
      addAfter(node);
      assert afNext == null && afPrev == null : "Node already member of affinity-list";
      afNext = afNode.afNext;
      afPrev = afNode;
      afNode.afNext.afPrev = this;
      afNode.afNext = this;
    }

    /**
     * Find the connection with given affinity id in this affinity-list.
     */
    public Node find(Object affinityId) {
      Node n = this.afNext;
      while (!n.isBoundaryNode()) {
        if (affinityId.equals(n.pc.affinityId())) {
          return n;
        }
        n = n.afNext;
      }
      return null;
    }
  }
}
