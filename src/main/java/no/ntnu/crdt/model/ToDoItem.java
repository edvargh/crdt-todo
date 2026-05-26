package no.ntnu.crdt.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a to-do item stored in the CRDT to-do list.
 *
 * <p>A {@code ToDoItem} is a pure identity object. Its only purpose is to give
 * the OR-Set a stable, globally unique key that every replica can recognise.
 * Mutable properties such as the item text are managed separately by
 * {@link ToDoList} via LWW-Registers, keyed by this id.</p>
 *
 * <p>Equality and hashing are based solely on {@link #getId()}, so two
 * instances with the same UUID are considered the same item regardless of any
 * other state.</p>
 */
public class ToDoItem {

  private final String id;

  /**
   * Creates a new to-do item with an automatically generated unique id.
   */
  public ToDoItem() {
    this.id = UUID.randomUUID().toString();
  }

  /**
   * Creates a to-do item with a predefined id.
   *
   * <p>Used when reconstructing an item from serialized state received over
   * the network.</p>
   *
   * @param id the unique identifier of the to-do item
   */
  public ToDoItem(String id) {
    this.id = id;
  }

  /**
   * Returns the unique identifier of this to-do item.
   *
   * @return the item id
   */
  public String getId() {
    return id;
  }

  /**
   * Compares this to-do item with another object.
   *
   * <p>Two to-do items are considered equal if they share the same id.</p>
   *
   * @param object the object to compare with
   * @return true if the objects represent the same to-do item
   */
  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }

    if (!(object instanceof ToDoItem todoItem)) {
      return false;
    }

    return Objects.equals(id, todoItem.id);
  }

  /**
   * Returns a hash code based on the to-do item id.
   *
   * @return the hash code of the to-do item
   */
  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
