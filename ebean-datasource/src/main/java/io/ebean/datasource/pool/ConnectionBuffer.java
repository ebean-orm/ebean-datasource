package io.ebean.datasource.pool;

import java.util.ArrayList;
import java.util.List;

/**
 * A buffer designed especially to hold pooled connections (free and busy ones)
 * <p>
 * The buffer contains two linkedLists (free and busy connection nodes) and optional
 * affinityLists.
 * <p>
 * The PooledConnections holds references to list-Nodes, which can be reused.
 * This avoids object creation/gc during add/unlink operations.
 * <p>
 * The connectionBuffer itself has one freeList, that contains all free connections
 * ordered by their last-used time. (the oldest connection is at the end)
 * In parallel, connections in the freeList can also be in an affinityList.
 * A hashing algoritm is used to distribute the connections to the affinityLists.
 * <p>
 * If <code>hashSize == 0</code> affinity support is disabled, and the connectionBuffer
 * handles only free and busyList.
 * <p>
 * Otherwise, there are <code>hashSize+1</code> affinityLists.
 * <p>
 * The last one is used for affinity-less connections and for the others, the
 * <code>object.hashCode() mod hashSize</code> is computed.
 * <p>
 * (When affinity is enabled, and no affinityID is used, <code>freeList</code>
 * and <code>affinityLists[hashSize]</code> have the same content.)
 * <p>
 * When we call <code>removeFree(someObjKey)</code>,
 * <ul>
 *   <li>try to return a matching connection from the according affinity-list slot</li>
 *   <li>try to return a connection with affinityId == null</li>
 *   <li>return null (and let the caller decide to create a new connection or query
 *   again with <code>GET_OLDEST</code> to return the last free connecion)</li>
 * </ul>
 * <p>
 * A free node is member in two lists:
 * <ol>
 *     <li>it is member in the freeList</li>
 *     <li>it is EITHER member in one of the affinity-hash-slots
 *     OR it is member of the <code>null</code> (=last) affiinity-slot</li>
 * </ol>
 * The remove / transition from free to busy will remove the node from both lists.
 * <p>
 * Graphical exammple
 * <pre>
 *     By default, the busy list is empty
 *     busyList (empty)
 *     freeList --> c1 --> c2 --> c3 --> c4 --> (end)
 *     al[0]    (empty - first hash slot)
 *     al[1]    (empty)
 *     al[2]    (empty)
 *     ...
 *     al[N-1]  (empty - last hash slot)
 *     al[N]    --> c1 --> c2 --> c3 --> c4 --> (end - null slot)
 *
 *     if a removeFree(1) is called, we lookup in al[1] and found no usable node.
 *     in this case, we return the first node of al[N] that has no affinity yet.
 *     When we remove the node from the affinityList, it is automatically removed
 *     from the freeList
 *
 *     busyList --> c1 --> (end)
 *     freeList ---------> c2 --> c3 --> c4 --> (end)
 *     al[0]    (empty)
 *     al[1]    (empty)
 *     al[2]    (empty)
 *     ...
 *     al[N-1]  (empty)
 *     al[N]    ---------> c2 --> c3 --> c4 --> (end)
 *
 *     When we put that node back in the freelist, it becomes the first node
 *     and it will be also linked in al[1]
 *
 *     busyList (empty)
 *     freeList --> c1 --> c2 --> c3 --> c4 --> (end)
 *     al[0]    (empty)
 *     al[1]    --> c1 --> (end)
 *     al[2]    (empty)
 *     ...
 *     al[N-1]  (empty)
 *     al[N]    ---------> c2 --> c3 --> c4 --> (end)
 *
 *     now we call removeFree(2) tree times. This will move c2 to c4 to busy list
 *     busyList --> c4 --> c3 --> c2 --> (end)
 *     freeList --> c1 ----------------> (end)
 *     al[0]    (empty)
 *     al[1]    --> c1 --> (end)
 *     al[2]    (empty)
 *     ...
 *     al[N-1]  (empty)
 *     al[N]    (empty)
 *
 *     when we return the connections (c4 to c2), we have this picture
 *     and there are no more affinity nodes left.
 *
 *     busyList (empty)
 *     freeList --> c2 --> c3 --> c4 --> c1 --> (end)
 *     al[0]    (empty)
 *     al[1]    --> c1 --> (end)
 *     al[2]    --> c2 --> c3 --> c4 --> (end)
 *     ...
 *     al[N-1]  (empty)
 *     al[N]    (empty)
 *
 *     subsequent queries to affinityId=1 / 2 will return c1, respectively c2..c4
 *
 *     querying for a connection with affinityId=3 will return null,
 *     because there is neither a matching one nor a null one.
 *
 *     The caller can now decide to create a new connection "c5" or
 *     query with GET_OLDEST for "c1"
 * </pre>
 * <p>
 * All thread safety controlled externally (by PooledConnectionQueue).
 * </p>
 */
final class ConnectionBuffer {

  // special key to return the oldest connection from freeList.
  static final Object GET_OLDEST = new Object();

  private final ConnectionList[] affinityLists;
  private final ConnectionList freeList = new ConnectionList();
  private final ConnectionList busyList = new ConnectionList();

  private final int hashSize;

  ConnectionBuffer(int hashSize) {
    assert hashSize >= 0;
    this.hashSize = hashSize;
    if (hashSize == 0) {
      affinityLists = null;
    } else {
      // we instantiate hashSize+1 slots. The last slot is reserved for connections
      // with `null` as affinityId
      affinityLists = new ConnectionList[hashSize + 1];
      for (int i = 0; i < affinityLists.length; i++) {
        affinityLists[i] = new ConnectionList();
      }
    }
  }

  /**
   * Return the number of free connections.
   */
  int freeSize() {
    return freeList.size();
  }

  /**
   * Return the number of busy connections.
   */
  int busySize() {
    return busyList.size();
  }

  /**
   * Add the connection to the beginning of the free list.
   * <p>
   * Note, the connection must be either new or unlinked from the busy list.
   */
  void addFree(PooledConnection c) {
    c.unlink();
    freeList.addFirst(c.busyFree());
    if (affinityLists != null) {
      if (c.affinityId() != null) {
        affinityLists[c.affinityId().hashCode() % hashSize].addFirst(c.affinity());
      } else {
        affinityLists[hashSize].addFirst(c.affinity());
      }
    }
  }

  /**
   * Adds the connection to the busy list.
   * <p>
   * Note, the connection must be either new or unlinked from the free list.
   */
  int addBusy(PooledConnection c) {
    busyList.addFirst(c.busyFree());
    return busyList.size();
  }

  /**
   * Removes the connection from the busy list.
   * Returns true, if this connection was part of the busy list or false, if not (or removed twice)
   */
  boolean removeBusy(PooledConnection c) {
    if (busyList.isLinkedTo(c.busyFree())) {
      c.unlink();
      return true;
    }
    return false;
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
  PooledConnection removeFree(Object affinityId) {
    PooledConnection pc;
    if (affinityId == GET_OLDEST) {
      pc = freeList.peekLast();
    } else if (affinityLists == null) {
      pc = freeList.peekFirst();
    } else if (affinityId == null) {
      pc = affinityLists[hashSize].peekFirst();
    } else { // we have an affinity id.
      pc = affinityLists[affinityId.hashCode() % hashSize].find(affinityId);
      if (pc == null) {
        // no pc with this affinity-id in the pool.
        // query "null"-affinityList
        pc = affinityLists[hashSize].peekFirst();
      }
    }
    if (pc == null) {
      return null;
    }
    pc.unlink();
    return pc;
  }

  /**
   * Close all free connections in this buffer.
   */
  void closeAllFree(boolean logErrors) {
    List<PooledConnection> tempList = new ArrayList<>();

    freeList.forEach(pc -> {
      pc.unlink();
      tempList.add(pc);
    });

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
    int toTrim = freeSize() - minSize;

    List<PooledConnection> ret = new ArrayList<>(toTrim);
    for (PooledConnection pc : freeList.reverse()) {
      if (ret.size() >= toTrim) {
        break;
      }
      if (pc.shouldTrim(usedSince, createdSince)) {
        pc.unlink();
        ret.add(pc);
      }
    }
    ret.forEach(pc -> pc.closeConnectionFully(true));
    return ret.size();
  }

  /**
   * Close connections that should be considered leaked.
   */
  void closeBusyConnections(long leakTimeMinutes) {
    long olderThanTime = System.currentTimeMillis() - (leakTimeMinutes * 60000);
    Log.debug("Closing busy connections using leakTimeMinutes {0}", leakTimeMinutes);
    busyList.forEach(pc -> {
      if (pc.lastUsedTime() > olderThanTime) {
        // PooledConnection has been used recently or
        // expected to be longRunning so not closing...
      } else {
        pc.unlink();
        closeBusyConnection(pc);
      }
    });
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
    busyList.forEach(pc -> {
      if (toLogger) {
        Log.info("Busy Connection - {0}", pc.fullDescription());
      } else {
        sb.append(pc.fullDescription()).append("\r\n");
      }
    });
    return sb.toString();
  }
}
