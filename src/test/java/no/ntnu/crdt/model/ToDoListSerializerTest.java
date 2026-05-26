package no.ntnu.crdt.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ToDoListSerializerTest {

  private final ToDoListSerializer serializer = new ToDoListSerializer();

  @Test
  void serializeShouldIncludeReplicaIdAndItemText() throws JsonProcessingException {
    ToDoList list = new ToDoList("clientA");
    list.addItem("Buy milk");

    String json = serializer.serialize(list);

    assertTrue(json.contains("clientA"), "JSON should contain replicaId");
    assertTrue(json.contains("Buy milk"), "JSON should contain item text");
  }

  @Test
  void deserializeShouldRestoreReplicaIdAndVisibleItems() throws JsonProcessingException {
    ToDoList original = new ToDoList("clientB");
    original.addItem("Walk the dog");

    String json = serializer.serialize(original);
    ToDoList restored = serializer.deserialize(json);

    assertEquals("clientB", restored.getItemsCrdt().getReplicaId());
    Set<String> texts = restored.getItems().stream()
        .map(item -> restored.getText(item.getId()))
        .collect(Collectors.toSet());
    assertTrue(texts.contains("Walk the dog"));
  }

  @Test
  void roundTripShouldPreserveAddsAndRemoves() throws JsonProcessingException {
    ToDoList original = new ToDoList("clientC");
    original.addItem("Task A");
    original.addItem("Task B");

    // Remove Task A so its tags appear in the removes map
    ToDoItem taskA = original.getItems().stream()
        .filter(i -> original.getText(i.getId()).equals("Task A"))
        .findFirst()
        .orElseThrow();
    original.removeItem(taskA);

    String json = serializer.serialize(original);
    ToDoList restored = serializer.deserialize(json);

    // Only Task B should be visible after the remove
    Set<String> visibleTexts = restored.getItems().stream()
        .map(item -> restored.getText(item.getId()))
        .collect(Collectors.toSet());
    assertFalse(visibleTexts.contains("Task A"), "Removed item should not be visible");
    assertTrue(visibleTexts.contains("Task B"), "Non-removed item should still be visible");

    // Both adds and removes maps must be non-empty so a future merge can converge correctly
    assertFalse(restored.getItemsCrdt().getAdds().isEmpty(), "Adds map must be preserved");
    assertFalse(restored.getItemsCrdt().getRemoves().isEmpty(), "Removes map must be preserved");
  }

  @Test
  void roundTripShouldPreserveTextRegisterAfterEdit() throws JsonProcessingException {
    ToDoList original = new ToDoList("clientA");
    original.addItem("Original text");

    ToDoItem item = original.getItems().iterator().next();
    original.editItem(item.getId(), "Edited text");

    String json = serializer.serialize(original);
    ToDoList restored = serializer.deserialize(json);

    assertEquals("Edited text", restored.getText(item.getId()),
        "Edited text should survive a serialize/deserialize round-trip");
  }

  @Test
  void roundTripShouldPreservePositionAfterMove() throws JsonProcessingException {
    ToDoList original = new ToDoList("clientA");
    original.addItem("Alpha"); // position 1.0
    original.addItem("Beta");  // position 2.0
    original.addItem("Gamma"); // position 3.0

    // Move Gamma between Alpha and Beta → position should become 1.5
    ToDoItem gamma = original.getItems().stream()
        .filter(i -> original.getText(i.getId()).equals("Gamma"))
        .findFirst()
        .orElseThrow();
    ToDoItem alpha = original.getItems().stream()
        .filter(i -> original.getText(i.getId()).equals("Alpha"))
        .findFirst()
        .orElseThrow();
    ToDoItem beta = original.getItems().stream()
        .filter(i -> original.getText(i.getId()).equals("Beta"))
        .findFirst()
        .orElseThrow();

    original.moveItem(gamma.getId(),
        original.getPosition(alpha.getId()),
        original.getPosition(beta.getId()));

    double expectedPos = original.getPosition(gamma.getId());

    String json = serializer.serialize(original);
    ToDoList restored = serializer.deserialize(json);

    assertEquals(expectedPos, restored.getPosition(gamma.getId()), 1e-10,
        "Fractional position must survive a serialize/deserialize round-trip");
  }

  @Test
  void roundTripShouldPreserveTextRegisterTimestamp() throws JsonProcessingException {
    // ListA and ListB start with the same item via merge
    ToDoList listA = new ToDoList("clientA");
    listA.addItem("Initial");

    ToDoList listB = new ToDoList("clientB");
    listB.merge(listA);

    ToDoItem item = listA.getItems().iterator().next();
    String itemId = item.getId();

    // ListA edits twice (timestamp = 2); ListB edits once (timestamp = 1 — stale)
    listA.editItem(itemId, "A's first edit");  // A: timestamp = 1
    listA.editItem(itemId, "A's final edit");  // A: timestamp = 2
    listB.editItem(itemId, "B's stale edit");  // B: timestamp = 1

    // Round-trip ListA — the timestamp=2 must survive serialization
    String json = serializer.serialize(listA);
    ToDoList restored = serializer.deserialize(json);

    // Merge the stale ListB (timestamp=1) into the restored list (timestamp=2).
    // If the timestamp was not preserved (e.g. reset to 0), B's timestamp=1
    // would incorrectly win and overwrite A's final edit.
    restored.merge(listB);

    assertEquals("A's final edit", restored.getText(itemId),
        "Higher timestamp must survive round-trip so stale remote edits cannot overwrite it");
  }
}
