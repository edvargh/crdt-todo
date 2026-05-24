package no.ntnu.crdt.dto;

import java.util.Set;

public record ORSetEntryDto(ToDoItemDto item, Set<String> tags) {
}