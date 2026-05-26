package no.ntnu.crdt.crdt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LWWRegisterTest {

  @Test
  void readShouldReturnInitialValue() {
    LWWRegister<String> register = new LWWRegister<>("hello", "clientA");

    assertEquals("hello", register.read());
  }

  @Test
  void writeShouldUpdateValue() {
    LWWRegister<String> register = new LWWRegister<>("hello", "clientA");

    register.write("world", "clientA");

    assertEquals("world", register.read());
  }

  @Test
  void mergeShouldKeepRemoteValueWhenRemoteTimestampIsHigher() {
    LWWRegister<String> local = new LWWRegister<>("original", "clientA");
    LWWRegister<String> remote = new LWWRegister<>("remote", "clientB");
    remote.write("remote edit", "clientB"); // remote timestamp = 1, local timestamp = 0

    local.merge(remote);

    assertEquals("remote edit", local.read());
  }

  @Test
  void mergeShouldKeepLocalValueWhenLocalTimestampIsHigher() {
    LWWRegister<String> local = new LWWRegister<>("original", "clientA");
    local.write("local edit", "clientA");  // timestamp = 1
    local.write("local edit 2", "clientA"); // timestamp = 2

    LWWRegister<String> remote = new LWWRegister<>("remote", "clientB");
    remote.write("remote edit", "clientB"); // timestamp = 1

    local.merge(remote);

    assertEquals("local edit 2", local.read());
  }

  @Test
  void mergeShouldBreakTimestampTiesByReplicaIdLexicographically() {
    // Both replicas write once, so both reach timestamp = 1
    LWWRegister<String> replicaA = new LWWRegister<>("initial", "clientA");
    replicaA.write("value from A", "clientA"); // timestamp = 1

    LWWRegister<String> replicaB = new LWWRegister<>("initial", "clientB");
    replicaB.write("value from B", "clientB"); // timestamp = 1

    // "clientB" > "clientA" lexicographically, so B wins on both replicas
    replicaA.merge(replicaB);
    replicaB.merge(replicaA);

    assertEquals("value from B", replicaA.read(), "A should converge to B's value");
    assertEquals("value from B", replicaB.read(), "B should keep its own value");
  }

  @Test
  void mergeShouldAdvanceTimestampToMaximum() {
    LWWRegister<String> local = new LWWRegister<>("original", "clientA");
    LWWRegister<String> remote = new LWWRegister<>("remote", "clientB");
    remote.write("edit 1", "clientB"); // timestamp = 1
    remote.write("edit 2", "clientB"); // timestamp = 2

    local.merge(remote); // local timestamp should advance to 2

    // A subsequent local write must beat the pre-merge remote value
    local.write("after merge", "clientA"); // timestamp = 3

    LWWRegister<String> staleRemote = new LWWRegister<>("stale", "clientC");
    staleRemote.write("stale edit", "clientC"); // timestamp = 1

    local.merge(staleRemote); // local (timestamp=3) wins over stale (timestamp=1)

    assertEquals("after merge", local.read());
  }

  @Test
  void mergesShouldConvergeRegardlessOfOrder() {
    LWWRegister<String> replicaA = new LWWRegister<>("v0", "clientA");
    replicaA.write("from A", "clientA"); // timestamp = 1

    LWWRegister<String> replicaB = new LWWRegister<>("v0", "clientB");
    replicaB.write("from B", "clientB"); // timestamp = 1
    replicaB.write("from B again", "clientB"); // timestamp = 2

    // Merge in both directions
    LWWRegister<String> copyA = new LWWRegister<>("from A", 1L, "clientA");
    LWWRegister<String> copyB = new LWWRegister<>("from B again", 2L, "clientB");

    copyA.merge(copyB);
    copyB.merge(copyA);

    assertEquals(copyA.read(), copyB.read(), "Registers must converge to the same value");
    assertEquals("from B again", copyA.read());
  }

  @Test
  void stateConstructorShouldRestoreRegister() {
    LWWRegister<String> register = new LWWRegister<>("restored value", 5L, "clientA");

    assertEquals("restored value", register.read());
    assertEquals(5L, register.getTimestamp());
    assertEquals("clientA", register.getReplicaId());
  }
}
