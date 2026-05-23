package no.ntnu.crdt.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToDoItemTest {

  @Test
  void constructorShouldGenerateIdAndStoreText() {
    ToDoItem item = new ToDoItem("Buy milk");

    assertNotNull(item.getId());
    assertFalse(item.getId().isBlank());
    assertEquals("Buy milk", item.getText());
  }

  @Test
  void constructorShouldUseProvidedIdAndText() {
    ToDoItem item = new ToDoItem("item-1", "Buy bread");

    assertEquals("item-1", item.getId());
    assertEquals("Buy bread", item.getText());
  }

  @Test
  void itemsWithSameIdShouldBeEqual() {
    ToDoItem first = new ToDoItem("item-1", "Buy milk");
    ToDoItem second = new ToDoItem("item-1", "Different text");

    assertEquals(first, second);
  }

  @Test
  void itemsWithDifferentIdsShouldNotBeEqual() {
    ToDoItem first = new ToDoItem("item-1", "Buy milk");
    ToDoItem second = new ToDoItem("item-2", "Buy milk");

    assertNotEquals(first, second);
  }

  @Test
  void equalItemsShouldHaveSameHashCode() {
    ToDoItem first = new ToDoItem("item-1", "Buy milk");
    ToDoItem second = new ToDoItem("item-1", "Different text");

    assertEquals(first.hashCode(), second.hashCode());
  }
}