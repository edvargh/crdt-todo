package no.ntnu.crdt.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToDoItemTest {

  @Test
  void constructorShouldGenerateUniqueId() {
    ToDoItem item = new ToDoItem();

    assertNotNull(item.getId());
    assertFalse(item.getId().isBlank());
  }

  @Test
  void constructorShouldUseProvidedId() {
    ToDoItem item = new ToDoItem("item-1");

    assertEquals("item-1", item.getId());
  }

  @Test
  void itemsWithSameIdShouldBeEqual() {
    ToDoItem first = new ToDoItem("item-1");
    ToDoItem second = new ToDoItem("item-1");

    assertEquals(first, second);
  }

  @Test
  void itemsWithDifferentIdsShouldNotBeEqual() {
    ToDoItem first = new ToDoItem("item-1");
    ToDoItem second = new ToDoItem("item-2");

    assertNotEquals(first, second);
  }

  @Test
  void equalItemsShouldHaveSameHashCode() {
    ToDoItem first = new ToDoItem("item-1");
    ToDoItem second = new ToDoItem("item-1");

    assertEquals(first.hashCode(), second.hashCode());
  }
}
