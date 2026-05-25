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
        .anyMatch(item -> list.getText(item.getId()).equals("Buy milk")));
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

  @Test
  void editItemShouldUpdateTextReturnedByGetText() {
    ToDoList list = new ToDoList("clientA");
    list.addItem("Original text");

    ToDoItem item = list.getItems().iterator().next();
    list.editItem(item.getId(), "Updated text");

    assertEquals("Updated text", list.getText(item.getId()));
  }

  @Test
  void editOnHigherTimestampShouldWinAfterMerge() {
    ToDoList listA = new ToDoList("clientA");
    listA.addItem("Initial");

    ToDoItem item = listA.getItems().iterator().next();
    String itemId = item.getId();

    // B receives A's state, then both edit concurrently
    ToDoList listB = new ToDoList("clientB");
    listB.merge(listA);

    listA.editItem(itemId, "Edit from A"); // A's register: timestamp = 1
    listA.editItem(itemId, "Second edit from A"); // A's register: timestamp = 2
    listB.editItem(itemId, "Edit from B"); // B's register: timestamp = 1

    // After merge, A's higher timestamp should win on both sides
    listA.merge(listB);
    listB.merge(listA);

    assertEquals("Second edit from A", listA.getText(itemId));
    assertEquals("Second edit from A", listB.getText(itemId));
  }

  @Test
  void mergeShouldCarryTextRegistersForNewItems() {
    ToDoList listA = new ToDoList("clientA");
    listA.addItem("Task only on A");

    ToDoItem item = listA.getItems().iterator().next();
    String itemId = item.getId();

    ToDoList listB = new ToDoList("clientB");
    listB.merge(listA);

    // B should now be able to read the text from the register it received
    assertEquals("Task only on A", listB.getText(itemId));
  }

  @Test
  void concurrentEditsWithSameTimestampShouldConvergeByReplicaId() {
    ToDoList listA = new ToDoList("clientA");
    listA.addItem("Initial");

    ToDoItem item = listA.getItems().iterator().next();
    String itemId = item.getId();

    ToDoList listB = new ToDoList("clientB");
    listB.merge(listA);

    // Both replicas make exactly one edit, giving both timestamp = 1
    listA.editItem(itemId, "Edit from clientA");
    listB.editItem(itemId, "Edit from clientB");

    listA.merge(listB);
    listB.merge(listA);

    // "clientB" > "clientA" lexicographically, so clientB's edit should win
    assertEquals(listA.getText(itemId), listB.getText(itemId), "Both replicas must converge");
    assertEquals("Edit from clientB", listA.getText(itemId));
  }
}
