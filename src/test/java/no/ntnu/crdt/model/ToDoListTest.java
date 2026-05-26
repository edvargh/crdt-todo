package no.ntnu.crdt.model;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

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

  // --- finished state tests ---

  @Test
  void setFinishedShouldMarkItemAsFinished() {
    ToDoList list = new ToDoList("clientA");
    list.addItem("Buy milk");

    ToDoItem item = list.getItems().iterator().next();
    assertFalse(list.getFinished(item.getId()), "Item should start unfinished");

    list.setFinished(item.getId(), true);

    assertTrue(list.getFinished(item.getId()));
  }

  @Test
  void finishedStateOnHigherTimestampShouldWinAfterMerge() {
    ToDoList listA = new ToDoList("clientA");
    listA.addItem("Buy milk");

    ToDoItem item = listA.getItems().iterator().next();
    String itemId = item.getId();

    ToDoList listB = new ToDoList("clientB");
    listB.merge(listA);

    // A marks finished (timestamp 1), then unfinished (timestamp 2)
    listA.setFinished(itemId, true);
    listA.setFinished(itemId, false);

    // B marks finished (timestamp 1) — concurrent, lower than A's final timestamp
    listB.setFinished(itemId, true);

    listA.merge(listB);
    listB.merge(listA);

    // A's timestamp=2 (false) must beat B's timestamp=1 (true) on both sides
    assertFalse(listA.getFinished(itemId), "Higher timestamp must win after merge");
    assertFalse(listB.getFinished(itemId), "Both replicas must converge");
  }

  // --- position / reorder tests ---

  @Test
  void addItemsShouldAssignStrictlyIncreasingPositions() {
    ToDoList list = new ToDoList("clientA");
    list.addItem("First");
    list.addItem("Second");
    list.addItem("Third");

    List<ToDoItem> sorted = list.getItems().stream()
        .sorted(Comparator.comparingDouble(item -> list.getPosition(item.getId())))
        .toList();

    assertEquals(3, sorted.size());
    assertTrue(
        list.getPosition(sorted.get(0).getId()) < list.getPosition(sorted.get(1).getId()),
        "First item position must be less than second"
    );
    assertTrue(
        list.getPosition(sorted.get(1).getId()) < list.getPosition(sorted.get(2).getId()),
        "Second item position must be less than third"
    );
  }

  @Test
  void moveItemShouldPlaceItemAtMidpointBetweenNeighbours() {
    ToDoList list = new ToDoList("clientA");
    list.addItem("A"); // position 1.0
    list.addItem("B"); // position 2.0
    list.addItem("C"); // position 3.0

    List<ToDoItem> sorted = list.getItems().stream()
        .sorted(Comparator.comparingDouble(item -> list.getPosition(item.getId())))
        .toList();

    ToDoItem itemA = sorted.get(0);
    ToDoItem itemB = sorted.get(1);
    ToDoItem itemC = sorted.get(2);

    double posA = list.getPosition(itemA.getId());
    double posB = list.getPosition(itemB.getId());

    // Move C to between A and B
    list.moveItem(itemC.getId(), posA, posB);

    double newPosC = list.getPosition(itemC.getId());
    assertEquals((posA + posB) / 2.0, newPosC, 1e-10,
        "Moved item position must be midpoint of its neighbours");
    assertTrue(newPosC > posA, "Moved item must be after A");
    assertTrue(newPosC < posB, "Moved item must be before B");
  }

  @Test
  void moveItemToStartShouldAssignPositionBeforeCurrentFirst() {
    ToDoList list = new ToDoList("clientA");
    list.addItem("A"); // position 1.0
    list.addItem("B"); // position 2.0

    List<ToDoItem> sorted = list.getItems().stream()
        .sorted(Comparator.comparingDouble(item -> list.getPosition(item.getId())))
        .toList();

    ToDoItem itemA = sorted.get(0);
    ToDoItem itemB = sorted.get(1);

    // Move B to the very start (prevPos = null)
    list.moveItem(itemB.getId(), null, list.getPosition(itemA.getId()));

    assertTrue(
        list.getPosition(itemB.getId()) < list.getPosition(itemA.getId()),
        "Item moved to start must have a smaller position than the previous first item"
    );
  }

  @Test
  void moveItemToEndShouldAssignPositionAfterCurrentLast() {
    ToDoList list = new ToDoList("clientA");
    list.addItem("A"); // position 1.0
    list.addItem("B"); // position 2.0

    List<ToDoItem> sorted = list.getItems().stream()
        .sorted(Comparator.comparingDouble(item -> list.getPosition(item.getId())))
        .toList();

    ToDoItem itemA = sorted.get(0);
    ToDoItem itemB = sorted.get(1);

    // Move A to the very end (nextPos = null)
    list.moveItem(itemA.getId(), list.getPosition(itemB.getId()), null);

    assertTrue(
        list.getPosition(itemA.getId()) > list.getPosition(itemB.getId()),
        "Item moved to end must have a larger position than the previous last item"
    );
  }

  @Test
  void concurrentMovesShouldConvergeViaLww() {
    ToDoList listA = new ToDoList("clientA");
    listA.addItem("Task");

    ToDoList listB = new ToDoList("clientB");
    listB.merge(listA);

    String itemId = listA.getItems().iterator().next().getId();

    // A moves the item to position ~0.5; B moves it to ~1.5 — concurrent, no sync
    listA.moveItem(itemId, null, 1.0);   // A writes timestamp 1, position = 0.5
    listB.moveItem(itemId, 1.0, 2.0);   // B writes timestamp 1, position = 1.5

    listA.merge(listB);
    listB.merge(listA);

    // Both replicas must agree on the same position
    assertEquals(listA.getPosition(itemId), listB.getPosition(itemId), 1e-10,
        "Concurrent moves must converge to the same position after merge");
  }

  @Test
  void mergeShouldCarryPositionRegistersForNewItems() {
    ToDoList listA = new ToDoList("clientA");
    listA.addItem("Remote task");

    ToDoItem item = listA.getItems().iterator().next();
    double originalPos = listA.getPosition(item.getId());

    ToDoList listB = new ToDoList("clientB");
    listB.merge(listA);

    assertEquals(originalPos, listB.getPosition(item.getId()), 1e-10,
        "Position register must be copied to a replica that receives a new item via merge");
  }
}
