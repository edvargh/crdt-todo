package no.ntnu.crdt.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.ntnu.crdt.crdt.LWWRegister;
import no.ntnu.crdt.crdt.ORSet;
import no.ntnu.crdt.dto.ORSetEntryDto;
import no.ntnu.crdt.dto.TextRegisterEntryDto;
import no.ntnu.crdt.dto.ToDoItemDto;
import no.ntnu.crdt.dto.ToDoListStateDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Serializes and deserializes to-do list state as JSON.
 *
 * <p>State is converted to/from {@link ToDoListStateDto} before JSON encoding.
 * This avoids passing domain objects directly to Jackson, which cannot handle
 * {@code Map<ToDoItem, Set<String>>} as JSON object keys.</p>
 *
 * <p>The serialized payload contains both the full OR-Set tag maps (for CRDT
 * existence merging) and the LWW-Register state for every item's text field
 * (for CRDT text merging).</p>
 */
public class ToDoListSerializer {

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Serializes a to-do list to a JSON string.
   *
   * @param list the to-do list to serialize
   * @return JSON representation of the list's full CRDT state
   * @throws JsonProcessingException if serialization fails
   */
  public String serialize(ToDoList list) throws JsonProcessingException {
    ORSet<ToDoItem> crdt = list.getItemsCrdt();

    List<TextRegisterEntryDto> textRegisterDtos = list.getTextRegisters().entrySet().stream()
        .map(e -> new TextRegisterEntryDto(
            e.getKey(),
            e.getValue().read(),
            e.getValue().getTimestamp(),
            e.getValue().getReplicaId()
        ))
        .toList();

    ToDoListStateDto dto = new ToDoListStateDto(
        crdt.getReplicaId(),
        toEntryDtos(crdt.getAdds()),
        toEntryDtos(crdt.getRemoves()),
        textRegisterDtos
    );

    return objectMapper.writeValueAsString(dto);
  }

  /**
   * Deserializes a to-do list from a JSON string.
   *
   * <p>If the {@code textRegisters} field is absent (messages from older clients
   * that predate LWW-Register support), the registers map is treated as empty
   * and {@link ToDoList#getText(String)} will return an empty string for those
   * items until a replica with register state is merged in.</p>
   *
   * @param json JSON produced by {@link #serialize}
   * @return the reconstructed to-do list
   * @throws JsonProcessingException if deserialization fails
   */
  public ToDoList deserialize(String json) throws JsonProcessingException {
    ToDoListStateDto dto = objectMapper.readValue(json, ToDoListStateDto.class);

    Map<ToDoItem, Set<String>> adds = fromEntryDtos(dto.adds());
    Map<ToDoItem, Set<String>> removes = fromEntryDtos(dto.removes());
    ORSet<ToDoItem> crdt = new ORSet<>(dto.replicaId(), adds, removes);

    List<TextRegisterEntryDto> textDtos = dto.textRegisters() != null
        ? dto.textRegisters()
        : List.of();

    Map<String, LWWRegister<String>> textRegisters = new HashMap<>();
    for (TextRegisterEntryDto entry : textDtos) {
      textRegisters.put(
          entry.itemId(),
          new LWWRegister<>(entry.value(), entry.timestamp(), entry.replicaId())
      );
    }

    return new ToDoList(crdt, textRegisters);
  }

  // --- private helpers ---

  private List<ORSetEntryDto> toEntryDtos(Map<ToDoItem, Set<String>> map) {
    return map.entrySet().stream()
        .map(e -> new ORSetEntryDto(
            new ToDoItemDto(e.getKey().getId()),
            e.getValue()))
        .toList();
  }

  private Map<ToDoItem, Set<String>> fromEntryDtos(List<ORSetEntryDto> entries) {
    return entries.stream()
        .collect(Collectors.toMap(
            e -> new ToDoItem(e.item().id()),
            ORSetEntryDto::tags
        ));
  }
}
