package no.ntnu.crdt.core;

/**
 * A state-based Last-Write-Wins Register (LWW-Register) CRDT.
 *
 * <p>Each write is tagged with a monotonically increasing timestamp and the
 * id of the writing replica. On merge, the value with the higher timestamp
 * wins. Ties are broken lexicographically by replica id, so every replica
 * converges to the same value deterministically without any coordination.</p>
 *
 * <p>The timestamp is a logical counter local to this register. It advances
 * by one on every {@link #write} call, and is updated to
 * {@code max(local, other)} on every {@link #merge}. This ensures that any
 * subsequent local write will always beat both pre-merge values.</p>
 *
 * @param <T> the type of value held in this register
 */
public class LWWRegister<T> {

  private T value;
  private long timestamp;
  private String writerReplicaId;

  /**
   * Creates a new LWW-Register with an initial value and timestamp 0.
   *
   * @param initialValue the initial value stored in the register
   * @param replicaId    id of the replica performing the initial write
   */
  public LWWRegister(T initialValue, String replicaId) {
    this.value = initialValue;
    this.timestamp = 0;
    this.writerReplicaId = replicaId;
  }

  /**
   * Restores an LWW-Register from serialized state.
   *
   * <p>Used when reconstructing state received over the network.</p>
   *
   * @param value     the stored value
   * @param timestamp the timestamp of the last write
   * @param replicaId id of the replica that performed the last write
   */
  public LWWRegister(T value, long timestamp, String replicaId) {
    this.value = value;
    this.timestamp = timestamp;
    this.writerReplicaId = replicaId;
  }

  /**
   * Returns a copy of {@code source} with the same value, timestamp, and replica id.
   *
   * <p>Use this instead of manually replicating the constructor arguments when
   * copying a register during a merge.</p>
   *
   * @param <T>    the value type
   * @param source the register to copy
   * @return a new register equal in state to {@code source}
   */
  public static <T> LWWRegister<T> copyOf(LWWRegister<T> source) {
    return new LWWRegister<>(source.read(), source.getTimestamp(), source.getWriterReplicaId());
  }

  /**
   * Writes a new value into this register, incrementing the timestamp.
   *
   * @param newValue  the value to store
   * @param replicaId id of the replica performing the write
   */
  public void write(T newValue, String replicaId) {
    this.timestamp++;
    this.value = newValue;
    this.writerReplicaId = replicaId;
  }

  /**
   * Merges another LWW-Register into this one.
   *
   * <p>The side with the higher timestamp wins. Equal timestamps are broken
   * lexicographically by replica id, ensuring a deterministic total order
   * across all replicas. The local timestamp is advanced to
   * {@code max(local, other)}.</p>
   *
   * @param other the other LWW-Register replica to merge
   */
  public void merge(LWWRegister<T> other) {
    boolean otherWins = other.timestamp > this.timestamp
        || (other.timestamp == this.timestamp
            && other.writerReplicaId.compareTo(this.writerReplicaId) > 0);

    if (otherWins) {
      this.value = other.value;
      this.writerReplicaId = other.writerReplicaId;
    }

    this.timestamp = Math.max(this.timestamp, other.timestamp);
  }

  /**
   * Returns the current value of this register.
   *
   * @return the current value
   */
  public T read() {
    return value;
  }

  /**
   * Returns the timestamp of the last write.
   *
   * @return the timestamp
   */
  public long getTimestamp() {
    return timestamp;
  }

  /**
   * Returns the replica id of the last writer.
   *
   * @return the replica id of whoever last wrote this register
   */
  public String getWriterReplicaId() {
    return writerReplicaId;
  }
}
