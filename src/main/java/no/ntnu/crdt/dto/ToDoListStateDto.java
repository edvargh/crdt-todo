package no.ntnu.crdt.dto;

import java.util.List;

/**
 * DTO representing the complete serialized state of a {@code ToDoList} replica.
 *
 * <p>{@code adds} and {@code removes} carry the full OR-Set tag maps needed for
 * CRDT merging. {@code textRegisters} carries the LWW-Register state for every
 * item's text field. {@code finishedRegisters} carries the LWW-Register state for
 * every item's finished status. Missing fields deserialize as {@code null} and are
 * treated as empty lists.</p>
 *
 * @param replicaId         unique id of the replica that produced this state
 * @param adds              OR-Set add-tag entries
 * @param removes           OR-Set remove-tag entries
 * @param textRegisters     LWW-Register entries for item text, one per item UUID
 * @param finishedRegisters LWW-Register entries for item finished status, one per item UUID
 */
public record ToDoListStateDto(
    String replicaId,
    List<ORSetEntryDto> adds,
    List<ORSetEntryDto> removes,
    List<TextRegisterEntryDto> textRegisters,
    List<FinishedRegisterEntryDto> finishedRegisters
) {}
