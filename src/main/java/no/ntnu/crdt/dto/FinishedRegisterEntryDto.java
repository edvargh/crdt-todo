package no.ntnu.crdt.dto;

/**
 * DTO representing the serialized state of a single LWW-Register entry for the
 * finished (done/undone) status of a to-do item.
 *
 * <p>Each entry carries the item UUID, the current boolean value, the logical
 * timestamp, and the id of the replica that last wrote the value.</p>
 *
 * @param itemId    UUID of the to-do item this register belongs to
 * @param value     {@code true} if the item is marked as finished
 * @param timestamp logical timestamp of the last write
 * @param replicaId id of the replica that performed the last write
 */
public record FinishedRegisterEntryDto(
    String itemId,
    boolean value,
    long timestamp,
    String replicaId
) {}
