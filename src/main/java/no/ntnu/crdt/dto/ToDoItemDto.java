package no.ntnu.crdt.dto;

/**
 * DTO representing a single to-do item's identity within a serialized OR-Set entry.
 *
 * <p>Only the item id is carried here. The current text value is transported
 * separately via {@link TextRegisterEntryDto} as part of the LWW-Register state.</p>
 *
 * @param id UUID of the to-do item
 */
public record ToDoItemDto(String id) {}
