package no.ntnu.crdt.dto;

import java.util.Set;

/**
 * Data-transfer object that carries a single OR-Set entry (one {@link ToDoItemDto}
 * together with the set of unique tags that represent its presence or removal).
 *
 * @param item the to-do item being transferred
 * @param tags the set of unique identifiers associated with this entry
 */
public record ORSetEntryDto(ToDoItemDto item, Set<String> tags) {
}