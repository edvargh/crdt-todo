package no.ntnu.crdt.dto;

/**
 * DTO representing the serialized state of a single LWW-Register entry.
 *
 * <p>Each entry corresponds to one to-do item's text register and carries
 * the item UUID, the current text value, the logical timestamp, and the
 * id of the replica that last wrote the value.</p>
 *
 * @param itemId    UUID of the to-do item this register belongs to
 * @param value     current text value
 * @param timestamp logical timestamp of the last write
 * @param replicaId id of the replica that performed the last write
 */
public record TextRegisterEntryDto(
    String itemId,
    String value,
    long timestamp,
    String replicaId
) {}
