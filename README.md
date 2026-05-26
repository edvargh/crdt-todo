# CRDT Todo

[![CI](https://github.com/edvargh/crdt-todo/actions/workflows/ci.yml/badge.svg)](https://github.com/edvargh/crdt-todo/actions/workflows/ci.yml)

## Introduction

**CRDT Todo** is a collaborative to-do list application that demonstrates the use of
Conflict-free Replicated Data Types (CRDTs) in a client-server architecture.

Multiple clients can edit the same to-do list simultaneously — even while fully
disconnected from the server — and their states will always converge to a consistent
result when they reconnect and sync. No manual conflict resolution is required, and no
edit is ever silently discarded by the synchronisation mechanism itself.

The application is a proof-of-concept built in Java 21. It features:

- A WebSocket server that acts as the central synchronisation hub
- A JavaFX desktop UI with two independent client panes displayed side by side, so
  concurrent edits and convergence can be observed interactively in a single window
- Two hand-written CRDT implementations that compose together to back the full
  to-do list model

The project does **not** use any existing CRDT library. Both `ORSet` and `LWWRegister`
are implemented from scratch.

---

## Implemented Functionality

### CRDTs

#### ORSet — Observed-Remove Set (`crdt/ORSet.java`)

A state-based set CRDT with **add-wins** semantics on concurrent add/remove operations.

Every `add` call tags the element with a unique string (`replicaId + UUID`). A `remove`
moves all locally observed tags for that element into a separate remove-set. An element
is visible if it has at least one add-tag that is not in the remove-set. Two replicas
merge by taking the union of both their add-sets and remove-sets.

Key property: if replica A removes an item at the same moment replica B adds it (neither
has seen the other's operation), the item will be present after merge. Add wins.

#### LWWRegister — Last-Write-Wins Register (`crdt/LWWRegister.java`)

A state-based single-value register CRDT. Every write is stamped with a monotonically
increasing logical timestamp and the writing replica's ID. On merge, the entry with the
higher timestamp wins. Equal timestamps are broken lexicographically by replica ID,
guaranteeing a deterministic total order across all replicas with no coordination.

The timestamp advances by one on every `write`, and is updated to
`max(local, remote)` on every `merge`, ensuring that any subsequent local write will
always produce a strictly higher timestamp than any pre-merge value.

### To-do List Model (`model/ToDoList.java`)

`ToDoList` composes the two CRDTs into a complete replicated to-do list:

| Field | CRDT | Semantics |
|---|---|---|
| Item existence | `ORSet<ToDoItem>` | Add-wins on concurrent add/remove |
| Item text | `Map<id, LWWRegister<String>>` | Last write wins on concurrent edits |
| Item finished state | `Map<id, LWWRegister<Boolean>>` | Last write wins on concurrent toggles |

Item identity (UUID) is kept strictly separate from item properties. The ORSet knows
nothing about text or finished state; those are tracked independently in per-item
registers. This avoids the classic mistake of embedding mutable state inside a set
element, which would break merge semantics.

### Serialisation (`model/ToDoListSerializer.java`)

Full CRDT state — including all internal add-tags, remove-tags, and register timestamps —
is serialised to JSON via Jackson and transmitted over WebSocket. This means any replica
can fully reconstruct its CRDT state from a received message without losing causality
information.

### Client-Server Synchronisation (`server/ToDoWebSocketServer.java`)

The server maintains its own `ToDoList` replica (replicaId `"server"`). On receiving a
message from any client it:

1. Deserialises the client's full state into a temporary `ToDoList`
2. Merges it into the server's own replica
3. Serialises the merged result and broadcasts it to every connected client

On first connect, the server immediately sends its current state to the new client so it
catches up without needing to send anything first.

All server-side state access is synchronised (`synchronized` blocks) to handle
concurrent WebSocket callbacks safely.

### User Interface (`ui/ToDoApplication.java`, `ui/ClientPane.java`)

The JavaFX UI starts the WebSocket server and opens a single window split into two
independent client panes — **Client A** (port 8080) and **Client B** (also port 8080) —
each with its own `ToDoList` replica and WebSocket connection.

Each pane provides:

- A live `ListView` of current items, with a checkbox for finished state and
  strikethrough styling on finished items
- **Add**, **Edit**, and **Remove** buttons
- A coloured connection status indicator (green / red)
- A **Disconnect / Reconnect** toggle for simulating network partitions

Edits made while disconnected are accumulated locally and pushed to the server on
reconnect, demonstrating offline-first behaviour and eventual consistency.

---

## Future Work / Known Limitations

| Limitation | Description |
|---|---|
| **Session-scoped logical clock** | `LWWRegister` timestamps start at `0` on every restart. If a client restarts and re-writes an item, the new write has a lower timestamp than any pre-restart write from another replica that hasn't been synced yet, potentially losing the new write silently. Fix: initialise the clock from `System.currentTimeMillis()`. |
| **No deterministic item ordering** | `ORSet.value()` returns a `HashSet`. Items can appear in a different order after each sync. Fix: sort by item creation timestamp before rendering. |
| **Orphaned registers** | Text and finished registers for removed items are never pruned from `ToDoList`. They accumulate indefinitely. Fix: after merge, remove any register key not present in `ORSet.value()`. |
| **Full-state sync** | The entire CRDT state is sent on every update. For large lists this becomes inefficient. Fix: delta-state CRDTs that only transmit the changed portion. |
| **Single server** | The server is a single point of failure. A true peer-to-peer topology would allow clients to sync directly with each other. |
| **No persistence** | All state is in-memory. The server and clients lose their state on restart. Fix: serialise state to disk after every merge. |
| **LWW on text is lossy** | If two clients concurrently edit the same item's text, one edit silently loses after merge. A Multi-Value Register (MV-Register) backed by vector clocks would preserve both values and surface the conflict to the user. |

---

## External Dependencies

| Dependency | Version | Description |
|---|---|---|
| [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket) | 1.5.6 | Provides the WebSocket server and client implementation. Used in `ToDoWebSocketServer` and `ClientPane` to handle real-time bidirectional communication between clients and server. |
| [Jackson Databind](https://github.com/FasterXML/jackson-databind) | 2.18.6 | JSON serialisation and deserialisation. Used in `ToDoListSerializer` to convert the full CRDT state (including internal add-tags, remove-tags, and register timestamps) to/from JSON for transmission over WebSocket. |
| [JavaFX Controls](https://openjfx.io/) | 21 | UI toolkit. Used to build the interactive desktop application with `ListView`, buttons, labels, and split-pane layout. |
| [JUnit Jupiter](https://junit.org/junit5/) | 5.10.2 | Unit testing framework. Used for all 36 tests covering CRDT semantics, domain model behaviour, serialisation round-trips, and WebSocket server integration. |

Build tooling: **Apache Maven 3** with the Maven Surefire Plugin (test runner) and the
JavaFX Maven Plugin (application launcher).

---

## Installation

### Prerequisites

- **Java 21** or later — [download](https://adoptium.net/)
- **Maven 3.8+** — [download](https://maven.apache.org/download.cgi)

### Steps

```bash
# Clone the repository
git clone https://github.com/edvargh/crdt-todo.git
cd crdt-todo

# Build and download dependencies
mvn compile
```

---

## Usage

Start the full application (server + two client panes) with:

```bash
mvn javafx:run
```

A window opens with **Client A** on the left and **Client B** on the right. Both are
connected to the WebSocket server running on `localhost:8080`.

### Trying out CRDT behaviour

**Basic sync**
1. Type a task name in Client A's text field and click **Add**
2. The item appears in Client B's list automatically via the server broadcast

**Concurrent edits**
1. Click **Disconnect** on both clients
2. Add or edit different items on each client independently
3. Click **Reconnect** on both clients — changes from both sides merge without conflict

**Add-wins (ORSet)**

Add-wins semantics apply when the *same element* (same internal UUID) is concurrently
removed by one replica and re-added by another — in that case the re-add survives the
merge. This cannot be triggered through the UI because every **Add** button press
creates a fresh UUID, so there is no way to re-add the same identity concurrently.
The property is verified directly at the CRDT level in `ORSetTest`
(`addWinsOverConcurrentRemove`).

**Last-write-wins (LWWRegister)**
1. Disconnect both clients
2. Edit the same item's text on both clients
3. Reconnect — the edit with the higher timestamp (the one that happened later, or from
   the lexicographically greater replica ID on a tie) is kept

---

## Running Tests

```bash
mvn test
```

All 36 unit tests are run. Test classes:

| Test class | What it covers |
|---|---|
| `ORSetTest` | Add, remove, merge, add-wins semantics, state restoration, copy safety |
| `LWWRegisterTest` | Read, write, merge with various timestamp scenarios, tie-breaking by replica ID, state restoration |
| `ToDoItemTest` | UUID generation, equality by ID, hash consistency |
| `ToDoListTest` | Add/remove/edit items, merge, timestamp-based conflict resolution |
| `ToDoListSerializerTest` | Round-trip serialisation, full CRDT state preservation including timestamps |
| `ToDoWebSocketServerTest` | Client connect/receive state, broadcast to all clients, reconnection behaviour |

---

## API Documentation

Javadoc can be generated locally with:

```bash
mvn javadoc:javadoc
```

The output is written to `target/reports/apidocs/index.html`. Open it with:

```bash
start target/reports/apidocs/index.html
```

All public classes and methods carry Javadoc comments.

---