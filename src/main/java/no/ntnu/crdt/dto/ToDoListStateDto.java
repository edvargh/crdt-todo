package no.ntnu.crdt.dto;

import java.util.List;

/**
 * DTO representing the complete serialized state of a {@code ToDoList} replica.
 *
 * <p>{@code adds} and {@code removes} carry the full OR-Set tag maps needed for
 * CRDT merging. {@code textRegisters} carries the LWW-Register state for every
 * item's text field. Older messages that predate text-register support will have
 * a {@code null} {@code textRegisters} field, which the deserializer treats as an
 * empty list.</p>
 *
 * @param replicaId     unique id of the replica that produced this state
 * @param adds          OR-Set add-tag entries
 * @param removes       OR-Set remove-tag entries
 * @param textRegisters LWW-Register entries for item text, one per item UUID
 */
public record ToDoListStateDto(
    String replicaId,
    List<ORSetEntryDto> adds,
    List<ORSetEntryDto> removes,
    List<TextRegisterEntryDto> textRegisters
) {}
