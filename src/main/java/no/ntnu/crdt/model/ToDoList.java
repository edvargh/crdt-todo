package no.ntnu.crdt.model;

import no.ntnu.crdt.crdt.LWWRegister;
import no.ntnu.crdt.crdt.ORSet;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Represents a replicated to-do list backed by two CRDTs.
 *
 * <ul>
 *   <li>An {@link ORSet} tracks which items exist (add-wins on concurrent add/remove).</li>
 *   <li>A map of {@link LWWRegister} instances tracks the current text of every item
 *       (last-write-wins on concurrent edits), keyed by item UUID.</li>
 *   <li>A second map of {@link LWWRegister} instances tracks the finished status of
 *       every item (last-write-wins on concurrent toggles), keyed by item UUID.</li>
 * </ul>
 *
 * <p>The CRDTs are kept separate on purpose: the OR-Set's internal
 * {@code Map<ToDoItem, Set<String>>} uses item identity (UUID) as the key and knows
 * nothing about mutable properties. Merging it automatically would silently discard
 * any register state embedded inside an item object. The registers are therefore
 * merged independently in {@link #merge(ToDoList)}.</p>
 */
public class ToDoList {

  private final ORSet<ToDoItem> items;
  private final Map<String, LWWRegister<String>> textRegisters;
  private final Map<String, LWWRegister<Boolean>> finishedRegisters;

  /**
   * Creates a new to-do list replica.
   *
   * @param replicaId unique identifier for this replica
   */
  public ToDoList(String replicaId) {
    this.items = new ORSet<>(replicaId);
    this.textRegisters = new HashMap<>();
    this.finishedRegisters = new HashMap<>();
  }

  /**
   * Reconstructs a to-do list from an existing OR-Set, text registers, and finished
   * registers.
   *
   * <p>Used by {@link ToDoListSerializer} to restore state received over the
   * network without going through the public add/edit/remove API.</p>
   *
   * @param items             the OR-Set backing item existence
   * @param textRegisters     the LWW-Registers for item text, keyed by item UUID
   * @param finishedRegisters the LWW-Registers for item finished status, keyed by item UUID
   */
  ToDoList(ORSet<ToDoItem> items,
           Map<String, LWWRegister<String>> textRegisters,
           Map<String, LWWRegister<Boolean>> finishedRegisters) {
    this.items = items;
    this.textRegisters = new HashMap<>(textRegisters);
    this.finishedRegisters = new HashMap<>(finishedRegisters);
  }

  /**
   * Adds a new item to the to-do list and creates a text register for it.
   *
   * @param text the text description of the item
   */
  public void addItem(String text) {
    ToDoItem item = new ToDoItem();
    items.add(item);
    textRegisters.put(item.getId(), new LWWRegister<>(text, items.getReplicaId()));
    finishedRegisters.put(item.getId(), new LWWRegister<>(false, items.getReplicaId()));
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
   * Updates the text of an existing item via its LWW-Register.
   *
   * <p>The write is attributed to the local replica and will beat any remote
   * write with a lower timestamp during the next merge.</p>
   *
   * @param itemId  the UUID of the item to edit
   * @param newText the new text
   */
  public void editItem(String itemId, String newText) {
    LWWRegister<String> register = textRegisters.get(itemId);
    if (register != null) {
      register.write(newText, items.getReplicaId());
    }
  }

  /**
   * Returns the current text for an item, read from its LWW-Register.
   *
   * <p>Returns an empty string if no register exists for the given id.
   * Under normal operation this should not occur, as every item added via
   * {@link #addItem(String)} gets a register immediately.</p>
   *
   * @param itemId the UUID of the item
   * @return the current text, or an empty string if the item is unknown
   */
  public String getText(String itemId) {
    LWWRegister<String> register = textRegisters.get(itemId);
    return register != null ? register.read() : "";
  }

  /**
   * Marks an existing item as finished or unfinished via its LWW-Register.
   *
   * <p>The write is attributed to the local replica and will beat any remote
   * write with a lower timestamp during the next merge.</p>
   *
   * @param itemId   the UUID of the item to update
   * @param finished {@code true} to mark the item as finished, {@code false} to unmark it
   */
  public void setFinished(String itemId, boolean finished) {
    LWWRegister<Boolean> register = finishedRegisters.get(itemId);
    if (register != null) {
      register.write(finished, items.getReplicaId());
    }
  }

  /**
   * Returns whether an item is currently marked as finished, read from its LWW-Register.
   *
   * <p>Returns {@code false} if no register exists for the given id. Under normal
   * operation this should not occur, as every item added via {@link #addItem(String)}
   * gets a finished register immediately.</p>
   *
   * @param itemId the UUID of the item
   * @return {@code true} if the item is finished, {@code false} otherwise
   */
  public boolean getFinished(String itemId) {
    LWWRegister<Boolean> register = finishedRegisters.get(itemId);
    return register != null && register.read();
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
   * Returns the underlying OR-Set CRDT used for item existence.
   *
   * @return the OR-Set backing this list
   */
  public ORSet<ToDoItem> getItemsCrdt() {
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
   * Merges another to-do list replica into this one.
   *
   * <p>Both the OR-Set (item existence) and the LWW-Registers (item text) are
   * merged. Registers for items that are new to this replica are copied in full;
   * registers for items that already exist are merged via
   * {@link LWWRegister#merge(LWWRegister)}.</p>
   *
   * @param other the other to-do list replica
   */
  public void merge(ToDoList other) {
    items.merge(other.items);

    for (Map.Entry<String, LWWRegister<String>> entry : other.textRegisters.entrySet()) {
      String itemId = entry.getKey();
      LWWRegister<String> otherRegister = entry.getValue();

      LWWRegister<String> localRegister = textRegisters.get(itemId);
      if (localRegister != null) {
        localRegister.merge(otherRegister);
      } else {
        textRegisters.put(itemId, new LWWRegister<>(
            otherRegister.read(),
            otherRegister.getTimestamp(),
            otherRegister.getReplicaId()
        ));
      }
    }

    for (Map.Entry<String, LWWRegister<Boolean>> entry : other.finishedRegisters.entrySet()) {
      String itemId = entry.getKey();
      LWWRegister<Boolean> otherRegister = entry.getValue();

      LWWRegister<Boolean> localRegister = finishedRegisters.get(itemId);
      if (localRegister != null) {
        localRegister.merge(otherRegister);
      } else {
        finishedRegisters.put(itemId, new LWWRegister<>(
            otherRegister.read(),
            otherRegister.getTimestamp(),
            otherRegister.getReplicaId()
        ));
      }
    }
  }
}
