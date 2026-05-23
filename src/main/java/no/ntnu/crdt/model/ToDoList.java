package no.ntnu.crdt.model;

import no.ntnu.crdt.crdt.ORSet;

import java.util.Set;

/**
 * Represents a replicated to do list backed by an OR-Set CRDT.
 *
 * <p>The list stores {@link ToDoItem} objects and delegates conflict-free
 * add, remove, and merge behavior to the underlying {@link ORSet}.</p>
 */
public class ToDoList {

  private final ORSet<ToDoItem> items;

  /**
   * Creates a new to do list replica.
   *
   * @param replicaId unique identifier for this replica
   */
  public ToDoList(String replicaId) {
    this.items = new ORSet<>(replicaId);
  }

  /**
   * Adds a new item to the to do list.
   *
   * @param text the text description of the item
   */
  public void addItem(String text) {
    items.add(new ToDoItem(text));
  }

  /**
   * Removes an item from the to do list.
   *
   * @param item the item to remove
   */
  public void removeItem(ToDoItem item) {
    items.remove(item);
  }

  /**
   * Returns all currently visible items in the list.
   *
   * @return the visible to do items
   */
  public Set<ToDoItem> getItems() {
    return items.value();
  }

  /**
   * Merges another to do list replica into this replica.
   *
   * @param other the other to do list replica
   */
  public void merge(ToDoList other) {
    items.merge(other.items);
  }
}