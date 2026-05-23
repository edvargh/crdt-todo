package no.ntnu.crdt.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a to do item stored in the CRDT to do list.
 *
 * <p>Each to do item has a globally unique identifier and a text description.
 * Equality is based only on the id, which allows replicas to recognize the
 * same logical to do item across distributed systems.</p>
 */
public class ToDoItem {

  private final String id;
  private final String text;

  /**
   * Creates a new to do item with an automatically generated unique id.
   *
   * @param text the to do item description
   */
  public ToDoItem(String text) {
    this.id = UUID.randomUUID().toString();
    this.text = text;
  }

  /**
   * Creates a to do item with a predefined id.
   *
   * @param id the unique identifier of the to do item
   * @param text the to do item description
   */
  public ToDoItem(String id, String text) {
    this.id = id;
    this.text = text;
  }

  /**
   * Returns the unique identifier of the to do item.
   *
   * @return the to do item id
   */
  public String getId() {
    return id;
  }

  /**
   * Returns the text description of the to do item.
   *
   * @return the to do item text
   */
  public String getText() {
    return text;
  }

  /**
   * Compares this to do item with another object.
   *
   * <p>Two to do items are considered equal if they share the same id.</p>
   *
   * @param object the object to compare with
   * @return true if the objects represent the same to do item
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
   * Returns a hash code based on the to do item id.
   *
   * @return the hash code of the to do item
   */
  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}