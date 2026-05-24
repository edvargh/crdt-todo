package no.ntnu.crdt.server;

import no.ntnu.crdt.model.ToDoList;
import no.ntnu.crdt.model.ToDoListSerializer;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ToDoWebSocketServerTest {

  private ToDoWebSocketServer server;
  private int port;
  private final ToDoListSerializer serializer = new ToDoListSerializer();

  @BeforeEach
  void startServer() throws InterruptedException {
    server = new ToDoWebSocketServer(0);
    server.start();
    Thread.sleep(100);
    port = server.getPort();
  }

  @AfterEach
  void stopServer() throws InterruptedException {
    server.stop(100);
  }

  @Test
  void newClientReceivesServerStateOnConnect() throws Exception {
    BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    WebSocketClient client = buildClient(messages);
    client.connectBlocking();

    String initial = messages.poll(2, TimeUnit.SECONDS);

    client.closeBlocking();
    assertNotNull(initial, "Client should receive initial state on connect");
    assertTrue(initial.contains("server"), "Initial state should contain server replicaId");
  }

  @Test
  void serverMergesClientStateAndBroadcastsToSender() throws Exception {
    BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    WebSocketClient client = buildClient(messages);
    client.connectBlocking();

    messages.poll(2, TimeUnit.SECONDS); // discard initial state

    ToDoList clientList = new ToDoList("clientA");
    clientList.addItem("Buy milk");
    client.send(serializer.serialize(clientList));

    String broadcast = messages.poll(2, TimeUnit.SECONDS);

    client.closeBlocking();
    assertNotNull(broadcast, "Sender should receive the broadcast");
    assertTrue(broadcast.contains("Buy milk"), "Broadcast should contain the merged item");
  }

  @Test
  void broadcastReachesAllConnectedClients() throws Exception {
    BlockingQueue<String> messagesA = new LinkedBlockingQueue<>();
    BlockingQueue<String> messagesB = new LinkedBlockingQueue<>();
    WebSocketClient clientA = buildClient(messagesA);
    WebSocketClient clientB = buildClient(messagesB);

    clientA.connectBlocking();
    clientB.connectBlocking();
    messagesA.poll(2, TimeUnit.SECONDS); // discard initial state for A
    messagesB.poll(2, TimeUnit.SECONDS); // discard initial state for B

    ToDoList clientList = new ToDoList("clientA");
    clientList.addItem("Walk the dog");
    clientA.send(serializer.serialize(clientList));

    String broadcastA = messagesA.poll(2, TimeUnit.SECONDS);
    String broadcastB = messagesB.poll(2, TimeUnit.SECONDS);

    clientA.closeBlocking();
    clientB.closeBlocking();
    assertNotNull(broadcastA, "Client A should receive the broadcast");
    assertNotNull(broadcastB, "Client B should receive the broadcast");
    assertTrue(broadcastA.contains("Walk the dog"), "Client A broadcast should contain the item");
    assertTrue(broadcastB.contains("Walk the dog"), "Client B broadcast should contain the item");
  }

  @Test
  void reconnectingClientReceivesMergedState() throws Exception {
    BlockingQueue<String> messagesA = new LinkedBlockingQueue<>();
    BlockingQueue<String> messagesB = new LinkedBlockingQueue<>();

    // Client A connects, adds an item, then disconnects
    WebSocketClient clientA = buildClient(messagesA);
    clientA.connectBlocking();
    messagesA.poll(2, TimeUnit.SECONDS); // discard initial state

    ToDoList listA = new ToDoList("clientA");
    listA.addItem("Task from A");
    clientA.send(serializer.serialize(listA));
    messagesA.poll(2, TimeUnit.SECONDS); // discard broadcast
    clientA.closeBlocking();

    // Client B connects and adds its own item while A is offline
    WebSocketClient clientB = buildClient(messagesB);
    clientB.connectBlocking();
    messagesB.poll(2, TimeUnit.SECONDS); // discard initial state

    ToDoList listB = new ToDoList("clientB");
    listB.addItem("Task from B");
    clientB.send(serializer.serialize(listB));
    messagesB.poll(2, TimeUnit.SECONDS); // discard broadcast
    clientB.closeBlocking();

    // Client A reconnects — should immediately receive state containing both tasks
    BlockingQueue<String> messagesAReconnected = new LinkedBlockingQueue<>();
    WebSocketClient clientAReconnected = buildClient(messagesAReconnected);
    clientAReconnected.connectBlocking();

    String stateOnReconnect = messagesAReconnected.poll(2, TimeUnit.SECONDS);

    clientAReconnected.closeBlocking();
    assertNotNull(stateOnReconnect, "Reconnecting client should receive server state");
    assertTrue(stateOnReconnect.contains("Task from A"), "State should contain A's task");
    assertTrue(stateOnReconnect.contains("Task from B"), "State should contain B's task added while A was offline");
  }

  private WebSocketClient buildClient(BlockingQueue<String> messages) throws Exception {
    return new WebSocketClient(new URI("ws://localhost:" + port)) {
      @Override public void onOpen(ServerHandshake handshake) {}
      @Override public void onMessage(String message) { messages.offer(message); }
      @Override public void onClose(int code, String reason, boolean remote) {}
      @Override public void onError(Exception ex) {}
    };
  }
}
