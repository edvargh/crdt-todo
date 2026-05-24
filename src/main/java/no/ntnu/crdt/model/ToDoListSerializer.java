package no.ntnu.crdt.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.ntnu.crdt.crdt.ORSet;
import no.ntnu.crdt.dto.ORSetEntryDto;
import no.ntnu.crdt.dto.ToDoItemDto;
import no.ntnu.crdt.dto.ToDoListStateDto;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Serializes and deserializes to do list state as JSON.
 *
 * <p>State is converted to/from {@link ToDoListStateDto} before JSON encoding.
 * This avoids passing domain objects directly to Jackson, which cannot handle
 * {@code Map<ToDoItem, Set<String>>} as JSON object keys.</p>
 */
public class ToDoListSerializer {

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Serializes a to do list to a JSON string.
   *
   * @param list the to do list to serialize
   * @return JSON representation of the list's full CRDT state
   * @throws JsonProcessingException if serialization fails
   */
  public String serialize(ToDoList list) throws JsonProcessingException {
    ORSet<ToDoItem> crdt = list.getItemsCrdt();

    ToDoListStateDto dto = new ToDoListStateDto(
        crdt.getReplicaId(),
        toEntryDtos(crdt.getAdds()),
        toEntryDtos(crdt.getRemoves())
    );

    return objectMapper.writeValueAsString(dto);
  }

  /**
   * Deserializes a to do list from a JSON string.
   *
   * @param json JSON produced by {@link #serialize}
   * @return the reconstructed to do list
   * @throws JsonProcessingException if deserialization fails
   */
  public ToDoList deserialize(String json) throws JsonProcessingException {
    ToDoListStateDto dto = objectMapper.readValue(json, ToDoListStateDto.class);

    Map<ToDoItem, Set<String>> adds = fromEntryDtos(dto.adds());
    Map<ToDoItem, Set<String>> removes = fromEntryDtos(dto.removes());

    ORSet<ToDoItem> crdt = new ORSet<>(dto.replicaId(), adds, removes);
    return new ToDoList(crdt);
  }

  // --- private helpers ---

  private List<ORSetEntryDto> toEntryDtos(Map<ToDoItem, Set<String>> map) {
    return map.entrySet().stream()
        .map(e -> new ORSetEntryDto(
            new ToDoItemDto(e.getKey().getId(), e.getKey().getText()),
            e.getValue()))
        .collect(Collectors.toList());
  }

  private Map<ToDoItem, Set<String>> fromEntryDtos(List<ORSetEntryDto> entries) {
    return entries.stream()
        .collect(Collectors.toMap(
            e -> new ToDoItem(e.item().id(), e.item().text()),
            ORSetEntryDto::tags
        ));
  }
}
