package no.ntnu.crdt.model;

import no.ntnu.crdt.core.LWWRegister;
import no.ntnu.crdt.core.ORSet;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A replicated to-do list backed by two CRDTs.
 *
 * <p>An {@link ORSet} tracks which items exist. Three maps of {@link LWWRegister}
 * track text, finished state, and position for each item, all keyed by item UUID.
 * The registers are merged independently of the set in {@link #merge(ToDoList)}.</p>
 */
public class ToDoList {

  private final ORSet<ToDoItem> items;
  private final Map<String, LWWRegister<String>> textRegisters;
  private final Map<String, LWWRegister<Boolean>> finishedRegisters;
  private final Map<String, LWWRegister<Double>> positionRegisters;

  /**
   * Creates a new to-do list replica.
   *
   * @param replicaId unique identifier for this replica
   */
  public ToDoList(String replicaId) {
    this.items = new ORSet<>(replicaId);
    this.textRegisters = new HashMap<>();
    this.finishedRegisters = new HashMap<>();
    this.positionRegisters = new HashMap<>();
  }

  /**
   * Restores a to-do list from existing CRDT state. Used by {@link ToDoListSerializer}.
   *
   * @param items             the item OR-Set
   * @param textRegisters     text registers keyed by item UUID
   * @param finishedRegisters finished-state registers keyed by item UUID
   * @param positionRegisters position registers keyed by item UUID
   */
  ToDoList(ORSet<ToDoItem> items,
           Map<String, LWWRegister<String>> textRegisters,
           Map<String, LWWRegister<Boolean>> finishedRegisters,
           Map<String, LWWRegister<Double>> positionRegisters) {
    this.items = items;
    this.textRegisters = new HashMap<>(textRegisters);
    this.finishedRegisters = new HashMap<>(finishedRegisters);
    this.positionRegisters = new HashMap<>(positionRegisters);
  }

  /**
   * Adds a new item to the list. The item is placed after all currently visible items.
   *
   * @param text the item text
   */
  public void addItem(String text) {
    ToDoItem item = new ToDoItem();
    items.add(item);
    textRegisters.put(item.getId(), new LWWRegister<>(text, items.getReplicaId()));
    finishedRegisters.put(item.getId(), new LWWRegister<>(false, items.getReplicaId()));

    double nextPosition = items.value().stream()
        .map(visible -> positionRegisters.get(visible.getId()))
        .filter(Objects::nonNull)
        .mapToDouble(LWWRegister::read)
        .max()
        .orElse(0.0) + 1.0;
    positionRegisters.put(item.getId(), new LWWRegister<>(nextPosition, items.getReplicaId()));
  }

  /**
   * Removes an item from the to-do list.
   *
   * @param item the item to remove
   */
  public void removeItem(ToDoItem item) {
    items.remove(item);
  }

  /**
   * Updates the text of an item.
   *
   * @param itemId  UUID of the item
   * @param newText the new text
   */
  public void editItem(String itemId, String newText) {
    LWWRegister<String> register = textRegisters.get(itemId);
    if (register != null) {
      register.write(newText, items.getReplicaId());
    }
  }

  /**
   * Returns the text of an item, or an empty string if not found.
   *
   * @param itemId UUID of the item
   * @return the current text
   */
  public String getText(String itemId) {
    LWWRegister<String> register = textRegisters.get(itemId);
    return register != null ? register.read() : "";
  }

  /**
   * Sets the finished state of an item.
   *
   * @param itemId   UUID of the item
   * @param finished {@code true} to mark as finished, {@code false} to unmark
   */
  public void setFinished(String itemId, boolean finished) {
    LWWRegister<Boolean> register = finishedRegisters.get(itemId);
    if (register != null) {
      register.write(finished, items.getReplicaId());
    }
  }

  /**
   * Returns whether an item is finished, or {@code false} if not found.
   *
   * @param itemId UUID of the item
   * @return {@code true} if the item is marked as finished
   */
  public boolean getFinished(String itemId) {
    LWWRegister<Boolean> register = finishedRegisters.get(itemId);
    return register != null && register.read();
  }

  /**
   * Moves an item to the midpoint between {@code prevPosition} and {@code nextPosition}.
   * Pass {@code null} for either to move to the start or end of the list.
   *
   * @param itemId       UUID of the item
   * @param prevPosition position of the item above the gap, or {@code null}
   * @param nextPosition position of the item below the gap, or {@code null}
   */
  public void moveItem(String itemId, Double prevPosition, Double nextPosition) {
    LWWRegister<Double> register = positionRegisters.get(itemId);
    if (register == null) {
      return;
    }
    double prev = prevPosition != null ? prevPosition : 0.0;
    double next = nextPosition != null ? nextPosition : prev + 2.0;
    register.write((prev + next) / 2.0, items.getReplicaId());
  }

  /**
   * Returns the position of an item, or {@code 0.0} if not found.
   *
   * @param itemId UUID of the item
   * @return the fractional position
   */
  public double getPosition(String itemId) {
    LWWRegister<Double> register = positionRegisters.get(itemId);
    return register != null ? register.read() : 0.0;
  }

  /**
   * Returns all currently visible items in the list.
   *
   * @return the visible to-do items
   */
  public Set<ToDoItem> getItems() {
    return items.value();
  }

  /**
   * Returns the underlying OR-Set. Used by {@link ToDoListSerializer}.
   *
   * @return the item OR-Set
   */
  ORSet<ToDoItem> getItemsCrdt() {
    return items;
  }

  /**
   * Returns an unmodifiable view of the text registers, keyed by item UUID.
   *
   * @return the text registers
   */
  public Map<String, LWWRegister<String>> getTextRegisters() {
    return Collections.unmodifiableMap(textRegisters);
  }

  /**
   * Returns an unmodifiable view of the finished registers, keyed by item UUID.
   *
   * @return the finished registers
   */
  public Map<String, LWWRegister<Boolean>> getFinishedRegisters() {
    return Collections.unmodifiableMap(finishedRegisters);
  }

  /**
   * Returns an unmodifiable view of the position registers, keyed by item UUID.
   *
   * @return the position registers
   */
  public Map<String, LWWRegister<Double>> getPositionRegisters() {
    return Collections.unmodifiableMap(positionRegisters);
  }

  /**
   * Merges another replica into this one.
   * The OR-Set and all three register maps are merged independently.
   *
   * @param other the replica to merge in
   */
  public void merge(ToDoList other) {
    items.merge(other.items);
    mergeRegisterMap(textRegisters, other.textRegisters);
    mergeRegisterMap(finishedRegisters, other.finishedRegisters);
    mergeRegisterMap(positionRegisters, other.positionRegisters);
  }

  /**
   * Merges register entries from {@code remote} into {@code local}.
   */
  private <V> void mergeRegisterMap(
      Map<String, LWWRegister<V>> local,
      Map<String, LWWRegister<V>> remote) {
    for (Map.Entry<String, LWWRegister<V>> entry : remote.entrySet()) {
      String itemId = entry.getKey();
      LWWRegister<V> remoteReg = entry.getValue();
      LWWRegister<V> localReg = local.get(itemId);
      if (localReg != null) {
        localReg.merge(remoteReg);
      } else {
        local.put(itemId, LWWRegister.copyOf(remoteReg));
      }
    }
  }
}
