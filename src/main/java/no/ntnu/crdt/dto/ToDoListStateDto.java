package no.ntnu.crdt.dto;

import java.util.List;

/**
 * DTO representing the complete serialized state of a {@code ToDoList} replica.
 *
 * @param replicaId         unique id of the replica that produced this state
 * @param adds              OR-Set add-tag entries needed for CRDT existence merging
 * @param removes           OR-Set remove-tag entries needed for CRDT existence merging
 * @param textRegisters     LWW-Register entries for item text, one per item UUID
 * @param finishedRegisters LWW-Register entries for item finished status, one per item UUID
 * @param positionRegisters LWW-Register entries for item fractional position, one per item UUID
 */
public record ToDoListStateDto(
    String replicaId,
    List<ORSetEntryDto> adds,
    List<ORSetEntryDto> removes,
    List<TextRegisterEntryDto> textRegisters,
    List<FinishedRegisterEntryDto> finishedRegisters,
    List<PositionRegisterEntryDto> positionRegisters
) {}
