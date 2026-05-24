package no.ntnu.crdt.dto;

import java.util.List;

public record ToDoListStateDto(
    String replicaId,
    List<ORSetEntryDto> adds,
    List<ORSetEntryDto> removes
) {
}