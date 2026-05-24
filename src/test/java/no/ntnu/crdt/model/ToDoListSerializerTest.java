package no.ntnu.crdt.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.Set;

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
        .map(ToDoItem::getText)
        .collect(java.util.stream.Collectors.toSet());
    assertTrue(texts.contains("Walk the dog"));
  }

  @Test
  void roundTripShouldPreserveAddsAndRemoves() throws JsonProcessingException {
    ToDoList original = new ToDoList("clientC");
    original.addItem("Task A");
    original.addItem("Task B");

    // Remove Task A so its tags appear in the removes map
    ToDoItem taskA = original.getItems().stream()
        .filter(i -> i.getText().equals("Task A"))
        .findFirst()
        .orElseThrow();
    original.removeItem(taskA);

    String json = serializer.serialize(original);
    ToDoList restored = serializer.deserialize(json);

    // Only Task B should be visible after the remove
    Set<String> visibleTexts = restored.getItems().stream()
        .map(ToDoItem::getText)
        .collect(java.util.stream.Collectors.toSet());
    assertFalse(visibleTexts.contains("Task A"), "Removed item should not be visible");
    assertTrue(visibleTexts.contains("Task B"), "Non-removed item should still be visible");

    // Both adds and removes maps must be non-empty so a future merge can converge correctly
    assertFalse(restored.getItemsCrdt().getAdds().isEmpty(), "Adds map must be preserved");
    assertFalse(restored.getItemsCrdt().getRemoves().isEmpty(), "Removes map must be preserved");
  }
}
