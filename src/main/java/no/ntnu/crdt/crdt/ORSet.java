package no.ntnu.crdt.crdt;

import java.util.*;

/**
 * A state-based Observed-Remove Set (OR-Set) CRDT.
 *
 * <p>Each element can be added multiple times, and every add operation gets a
 * unique tag containing the replica id. Remove operations do not delete elements
 * directly. Instead, they mark the locally observed add-tags as removed.</p>
 *
 * <p>Two replicas can be merged by unioning their add-tags and remove-tags.
 * This allows replicas to eventually converge to the same visible value.</p>
 *
 * @param <T> the type of elements stored in the set
 */
public class ORSet<T> {

  private final Map<T, Set<String>> adds = new HashMap<>();
  private final Map<T, Set<String>> removes = new HashMap<>();
  private final String replicaId;

  /**
   * Creates a new OR-Set replica.
   *
   * @param replicaId unique identifier for this replica/client
   */
  public ORSet(String replicaId) {
    this.replicaId = replicaId;
  }

  /**
   * Creates a new OR-Set replica from existing state.
   *
   * <p>This constructor is useful when restoring state received from another
   * replica, for example over the network.</p>
   *
   * @param replicaId unique identifier for this replica
   * @param adds existing add-tags
   * @param removes existing remove-tags
   */
  public ORSet(String replicaId, Map<T, Set<String>> adds, Map<T, Set<String>> removes) {
    this.replicaId = replicaId;
    this.adds.putAll(copyMap(adds));
    this.removes.putAll(copyMap(removes));
  }

  /**
   * Adds an element to the OR-Set by creating a unique tag for this add operation.
   *
   * @param element the element to add
   */
  public void add(T element) {
    String tag = replicaId + "-" + UUID.randomUUID();

    adds.computeIfAbsent(element, key -> new HashSet<>())
        .add(tag);
  }

  /**
   * Removes an element from the OR-Set by marking all locally observed add-tags
   * for that element as removed.
   *
   * <p>The element is not deleted from the add-set. This is important because
   * CRDT replicas need the add-tags and remove-tags to merge correctly later.</p>
   *
   * @param element the element to remove
   */
  public void remove(T element) {
    Set<String> observedTags = adds.get(element);

    if (observedTags == null) {
      return;
    }

    removes.computeIfAbsent(element, key -> new HashSet<>())
        .addAll(observedTags);
  }

  /**
   * Merges another OR-Set replica into this replica.
   *
   * <p>The merge operation is state-based and uses set union for both add-tags
   * and remove-tags.</p>
   *
   * @param other the other OR-Set replica to merge into this one
   */
  public void merge(ORSet<T> other) {
    mergeMap(this.adds, other.adds);
    mergeMap(this.removes, other.removes);
  }

  /**
   * Merges all tags from one map into another.
   *
   * @param target the map that receives the tags
   * @param source the map containing tags to copy
   */
  private void mergeMap(Map<T, Set<String>> target, Map<T, Set<String>> source) {
    for (Map.Entry<T, Set<String>> entry : source.entrySet()) {
      T element = entry.getKey();
      Set<String> tags = entry.getValue();

      target.computeIfAbsent(element, key -> new HashSet<>())
          .addAll(tags);
    }
  }

  /**
   * Returns the currently visible elements in the OR-Set.
   *
   * <p>An element is visible if it has at least one add-tag that has not been
   * marked as removed.</p>
   *
   * @return a set containing all currently visible elements
   */
  public Set<T> value() {
    Set<T> result = new HashSet<>();

    for (Map.Entry<T, Set<String>> entry : adds.entrySet()) {
      T element = entry.getKey();
      Set<String> addTags = entry.getValue();
      Set<String> removeTags = removes.getOrDefault(element, Set.of());

      boolean hasActiveTag = addTags.stream()
          .anyMatch(tag -> !removeTags.contains(tag));

      if (hasActiveTag) {
        result.add(element);
      }
    }

    return result;
  }

  /**
   * Returns the unique identifier of this replica.
   *
   * @return the replica id
   */
  public String getReplicaId() {
    return replicaId;
  }

  /**
   * Returns a copy of the add-tags in this OR-Set.
   *
   * @return a copy of the add-tags
   */
  public Map<T, Set<String>> getAdds() {
    return copyMap(adds);
  }

  /**
   * Returns a copy of the remove-tags in this OR-Set.
   *
   * @return a copy of the remove-tags
   */
  public Map<T, Set<String>> getRemoves() {
    return copyMap(removes);
  }

  /**
   * Creates a deep copy of a map from elements to tag sets.
   *
   * @param source the map to copy
   * @return a copied map
   */
  private Map<T, Set<String>> copyMap(Map<T, Set<String>> source) {
    Map<T, Set<String>> copy = new HashMap<>();

    for (Map.Entry<T, Set<String>> entry : source.entrySet()) {
      copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
    }

    return copy;
  }
}