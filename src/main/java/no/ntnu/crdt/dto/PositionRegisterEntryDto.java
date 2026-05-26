package no.ntnu.crdt.dto;

/**
 * DTO representing the serialized state of a single LWW-Register entry for the
 * fractional position of a to-do item.
 *
 * <p>Positions are floating-point numbers used for fractional indexing: moving an
 * item between two neighbours with positions {@code p1} and {@code p2} assigns it
 * the midpoint {@code (p1 + p2) / 2}, preserving the total order without renumbering
 * every other item.</p>
 *
 * @param itemId    UUID of the to-do item this register belongs to
 * @param value     current fractional position
 * @param timestamp logical timestamp of the last write
 * @param replicaId id of the replica that performed the last write
 */
public record PositionRegisterEntryDto(
    String itemId,
    double value,
    long timestamp,
    String replicaId
) {}
