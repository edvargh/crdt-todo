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
 * </ul>
 *
 * <p>The two CRDTs are kept separate on purpose: the OR-Set's internal
 * {@code Map<ToDoItem, Set<String>>} uses item identity (UUID) as the key and knows
 * nothing about mutable properties. Merging it automatically would silently discard
 * any text-register state embedded inside an item object. The text registers are
 * therefore merged independently in {@link #merge(ToDoList)}.</p>
 */
public class ToDoList {

  private final ORSet<ToDoItem> items;
  private final Map<String, LWWRegister<String>> textRegisters;

  /**
   * Creates a new to-do list replica.
   *
   * @param replicaId unique identifier for this replica
   */
  public ToDoList(String replicaId) {
    this.items = new ORSet<>(replicaId);
    this.textRegisters = new HashMap<>();
  }

  /**
   * Reconstructs a to-do list from an existing OR-Set and text registers.
   *
   * <p>Used by {@link ToDoListSerializer} to restore state received over the
   * network without going through the public add/edit/remove API.</p>
   *
   * @param items         the OR-Set backing item existence
   * @param textRegisters the LWW-Registers for item text, keyed by item UUID
   */
  ToDoList(ORSet<ToDoItem> items, Map<String, LWWRegister<String>> textRegisters) {
    this.items = items;
    this.textRegisters = new HashMap<>(textRegisters);
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
  }
}
