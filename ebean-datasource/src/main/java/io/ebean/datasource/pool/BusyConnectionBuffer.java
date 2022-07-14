package io.ebean.datasource.pool;

import java.util.Arrays;

/**
 * A buffer especially designed for Busy PooledConnections.
 * <p>
 * All thread safety controlled externally (by PooledConnectionQueue).
 * <p>
 * It has a set of 'slots' and PooledConnections know which slot they went into
 * and this allows for fast addition and removal (by slotId without looping).
 * The capacity will increase on demand by the 'growBy' amount.
 */
final class BusyConnectionBuffer {

  private PooledConnection[] slots;
  private final int growBy;
  private int size;
  private int pos = -1;

  /**
   * Create the buffer with an initial capacity and fixed growBy.
   * We generally do not want the buffer to grow very often.
   *
   * @param capacity the initial capacity
   * @param growBy   the fixed amount to grow the buffer by.
   */
  BusyConnectionBuffer(int capacity, int growBy) {
    this.slots = new PooledConnection[capacity];
    this.growBy = growBy;
  }

  /**
   * We can only grow (not shrink) the capacity.
   */
  void setCapacity(int newCapacity) {
    if (newCapacity > slots.length) {
      PooledConnection[] current = this.slots;
      this.slots = new PooledConnection[newCapacity];
      System.arraycopy(current, 0, this.slots, 0, current.length);
    }
  }

  @Override
  public String toString() {
    return Arrays.toString(slots);
  }

  int getCapacity() {
    return slots.length;
  }

  int size() {
    return size;
  }

  boolean isEmpty() {
    return size == 0;
  }

  int add(PooledConnection pc) {
    if (size == slots.length) {
      // grow the capacity
      setCapacity(slots.length + growBy);
    }
    int slot = nextEmptySlot();
    pc.setSlotId(slot);
    slots[slot] = pc;
    return ++size;
  }

  boolean remove(PooledConnection pc) {
    int slotId = pc.getSlotId();
    if (slots[slotId] != pc) {
      PooledConnection heldBy = slots[slotId];
      Log.warn("Failed to remove from slot[{0}] PooledConnection[{1}] - HeldBy[{2}]", pc.getSlotId(), pc, heldBy);
      return false;
    }
    slots[slotId] = null;
    --size;
    return true;
  }

  /**
   * Close connections that should be considered leaked.
   */
  void closeBusyConnections(long leakTimeMinutes) {
    long olderThanTime = System.currentTimeMillis() - (leakTimeMinutes * 60000);
    Log.debug("Closing busy connections using leakTimeMinutes {0}", leakTimeMinutes);
    for (int i = 0; i < slots.length; i++) {
      if (slots[i] != null) {
        //tmp.add(slots[i]);
        PooledConnection pc = slots[i];
        //noinspection StatementWithEmptyBody
        if (pc.getLastUsedTime() > olderThanTime) {
          // PooledConnection has been used recently or
          // expected to be longRunning so not closing...
        } else {
          slots[i] = null;
          --size;
          closeBusyConnection(pc);
        }
      }
    }
  }

  private void closeBusyConnection(PooledConnection pc) {
    try {
      Log.warn("DataSourcePool closing busy connection? {0}", pc.getFullDescription());
      System.out.println("CLOSING busy connection: " + pc.getFullDescription());
      pc.closeConnectionFully(false);
    } catch (Exception ex) {
      Log.error("Error when closing potentially leaked connection " + pc.getDescription(), ex);
    }
  }

  /**
   * Returns information describing connections that are currently being used.
   */
  String getBusyConnectionInformation(boolean toLogger) {
    if (toLogger) {
      Log.info("Dumping [{0}] busy connections: (Use datasource.xxx.capturestacktrace=true  ... to get stackTraces)", size());
    }
    StringBuilder sb = new StringBuilder();
    for (PooledConnection pc : slots) {
      if (pc != null) {
        if (toLogger) {
          Log.info("Busy Connection - {0}", pc.getFullDescription());
        } else {
          sb.append(pc.getFullDescription()).append("\r\n");
        }
      }
    }
    return sb.toString();
  }


  /**
   * Return the position of the next empty slot.
   */
  private int nextEmptySlot() {
    // search forward
    while (++pos < slots.length) {
      if (slots[pos] == null) {
        return pos;
      }
    }
    // search from beginning
    pos = -1;
    while (++pos < slots.length) {
      if (slots[pos] == null) {
        return pos;
      }
    }
    // not expecting this
    throw new RuntimeException("No Empty Slot Found?");
  }

}
