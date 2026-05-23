package no.ntnu.crdt.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToDoListTest {

  @Test
  void addItemShouldMakeItemVisible() {
    ToDoList list = new ToDoList("clientA");

    list.addItem("Buy milk");

    assertEquals(1, list.getItems().size());
    assertTrue(list.getItems().stream()
        .anyMatch(item -> item.getText().equals("Buy milk")));
  }

  @Test
  void removeItemShouldMakeItemInvisible() {
    ToDoList list = new ToDoList("clientA");

    list.addItem("Buy milk");
    ToDoItem item = list.getItems().iterator().next();

    list.removeItem(item);

    assertTrue(list.getItems().isEmpty());
  }

  @Test
  void mergeShouldCombineItemsFromTwoLists() {
    ToDoList listA = new ToDoList("clientA");
    ToDoList listB = new ToDoList("clientB");

    listA.addItem("Buy milk");
    listB.addItem("Buy bread");

    listA.merge(listB);
    listB.merge(listA);

    assertEquals(listA.getItems(), listB.getItems());
    assertEquals(2, listA.getItems().size());
  }

  @Test
  void removeAfterMergeShouldBeReplicated() {
    ToDoList listA = new ToDoList("clientA");
    ToDoList listB = new ToDoList("clientB");

    listA.addItem("Buy milk");
    listB.merge(listA);

    ToDoItem item = listB.getItems().iterator().next();
    listB.removeItem(item);

    listA.merge(listB);

    assertTrue(listA.getItems().isEmpty());
    assertTrue(listB.getItems().isEmpty());
  }
}