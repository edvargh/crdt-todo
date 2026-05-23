package no.ntnu.crdt.crdt;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ORSetTest {

  @Test
  void addShouldMakeElementVisible() {
    ORSet<String> set = new ORSet<>("clientA");

    set.add("milk");

    assertTrue(set.value().contains("milk"));
  }

  @Test
  void removeShouldMakeElementInvisible() {
    ORSet<String> set = new ORSet<>("clientA");

    set.add("milk");
    set.remove("milk");

    assertFalse(set.value().contains("milk"));
  }

  @Test
  void mergeShouldCombineElementsFromTwoReplicas() {
    ORSet<String> replicaA = new ORSet<>("clientA");
    ORSet<String> replicaB = new ORSet<>("clientB");

    replicaA.add("milk");
    replicaB.add("bread");

    replicaA.merge(replicaB);
    replicaB.merge(replicaA);

    assertEquals(replicaA.value(), replicaB.value());
    assertTrue(replicaA.value().contains("milk"));
    assertTrue(replicaA.value().contains("bread"));
  }

  @Test
  void getReplicaIdShouldReturnReplicaId() {
    ORSet<String> set = new ORSet<>("clientA");

    assertEquals("clientA", set.getReplicaId());
  }

  @Test
  void constructorShouldRestoreExistingState() {
    ORSet<String> original = new ORSet<>("clientA");
    original.add("milk");

    ORSet<String> restored = new ORSet<>(
        "clientB",
        original.getAdds(),
        original.getRemoves()
    );

    assertTrue(restored.value().contains("milk"));
  }

  @Test
  void gettersShouldReturnCopies() {
    ORSet<String> set = new ORSet<>("clientA");
    set.add("milk");

    Map<String, Set<String>> addsCopy = set.getAdds();
    addsCopy.clear();

    assertTrue(set.value().contains("milk"));
  }
}