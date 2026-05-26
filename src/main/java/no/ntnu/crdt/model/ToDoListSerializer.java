package no.ntnu.crdt.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.ntnu.crdt.core.LWWRegister;
import no.ntnu.crdt.core.ORSet;
import no.ntnu.crdt.dto.FinishedRegisterEntryDto;
import no.ntnu.crdt.dto.ORSetEntryDto;
import no.ntnu.crdt.dto.PositionRegisterEntryDto;
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
 * existence merging) and the LWW-Register state for every item's text field,
 * finished status, and fractional position.</p>
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
            e.getValue().getWriterReplicaId()
        ))
        .toList();

    List<FinishedRegisterEntryDto> finishedRegisterDtos = list.getFinishedRegisters().entrySet().stream()
        .map(e -> new FinishedRegisterEntryDto(
            e.getKey(),
            e.getValue().read(),
            e.getValue().getTimestamp(),
            e.getValue().getWriterReplicaId()
        ))
        .toList();

    List<PositionRegisterEntryDto> positionRegisterDtos = list.getPositionRegisters().entrySet().stream()
        .map(e -> new PositionRegisterEntryDto(
            e.getKey(),
            e.getValue().read(),
            e.getValue().getTimestamp(),
            e.getValue().getWriterReplicaId()
        ))
        .toList();

    ToDoListStateDto dto = new ToDoListStateDto(
        crdt.getReplicaId(),
        toEntryDtos(crdt.getAdds()),
        toEntryDtos(crdt.getRemoves()),
        textRegisterDtos,
        finishedRegisterDtos,
        positionRegisterDtos
    );

    return objectMapper.writeValueAsString(dto);
  }

  /**
   * Deserializes a to-do list from a JSON string.
   *
   * <p>All six fields in the payload are expected to be present. Missing register
   * lists ({@code textRegisters}, {@code finishedRegisters}, {@code positionRegisters})
   * are treated as empty. Missing {@code adds} or {@code removes} are also treated as
   * empty, but omitting them from a valid CRDT message will cause incorrect merge
   * behaviour since causality information is lost.</p>
   *
   * @param json JSON produced by {@link #serialize}
   * @return the reconstructed to-do list
   * @throws JsonProcessingException if deserialization fails
   */
  public ToDoList deserialize(String json) throws JsonProcessingException {
    ToDoListStateDto dto = objectMapper.readValue(json, ToDoListStateDto.class);

    List<ORSetEntryDto> addEntries = dto.adds() != null ? dto.adds() : List.of();
    List<ORSetEntryDto> removeEntries = dto.removes() != null ? dto.removes() : List.of();
    Map<ToDoItem, Set<String>> adds = fromEntryDtos(addEntries);
    Map<ToDoItem, Set<String>> removes = fromEntryDtos(removeEntries);
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

    List<FinishedRegisterEntryDto> finishedDtos = dto.finishedRegisters() != null
        ? dto.finishedRegisters()
        : List.of();

    Map<String, LWWRegister<Boolean>> finishedRegisters = new HashMap<>();
    for (FinishedRegisterEntryDto entry : finishedDtos) {
      finishedRegisters.put(
          entry.itemId(),
          new LWWRegister<>(entry.value(), entry.timestamp(), entry.replicaId())
      );
    }

    List<PositionRegisterEntryDto> positionDtos = dto.positionRegisters() != null
        ? dto.positionRegisters()
        : List.of();

    Map<String, LWWRegister<Double>> positionRegisters = new HashMap<>();
    for (PositionRegisterEntryDto entry : positionDtos) {
      positionRegisters.put(
          entry.itemId(),
          new LWWRegister<>(entry.value(), entry.timestamp(), entry.replicaId())
      );
    }

    return new ToDoList(crdt, textRegisters, finishedRegisters, positionRegisters);
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
