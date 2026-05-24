package no.ntnu.crdt.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import no.ntnu.crdt.model.ToDoList;
import no.ntnu.crdt.model.ToDoListSerializer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * WebSocket server for synchronizing CRDT to do list state across clients.
 *
 * <p>The server maintains its own {@link ToDoList} replica that accumulates
 * the merged state from all connected clients. When a client connects it
 * immediately receives the current server state. When a client sends its
 * state the server merges it in and broadcasts the result to every connected
 * client, completing the state-based CRDT sync cycle.</p>
 */
public class ToDoWebSocketServer extends WebSocketServer {

  private static final Logger LOGGER = Logger.getLogger(ToDoWebSocketServer.class.getName());

  private final ToDoList serverState = new ToDoList("server");
  private final ToDoListSerializer serializer = new ToDoListSerializer();

  /**
   * Creates a new WebSocket server bound to the given port.
   *
   * @param port the port to listen on
   */
  public ToDoWebSocketServer(int port) {
    super(new InetSocketAddress(port));
  }

  /**
   * Sends the current server state to a newly connected client.
   *
   * <p>This ensures the client is immediately up to date with all changes
   * that were made before it connected.</p>
   *
   * @param conn      the new client connection
   * @param handshake the client handshake details
   */
  @Override
  public void onOpen(WebSocket conn, ClientHandshake handshake) {
    LOGGER.log(Level.INFO, "Client connected: {0}", conn.getRemoteSocketAddress());
    try {
      conn.send(serializer.serialize(serverState));
    } catch (JsonProcessingException e) {
      LOGGER.log(Level.SEVERE, "Failed to serialize server state for new client", e);
    }
  }

  /**
   * Logs when a client disconnects.
   *
   * @param conn   the closed connection
   * @param code   the WebSocket close code
   * @param reason the reason for closing
   * @param remote whether the close was initiated by the remote peer
   */
  @Override
  public void onClose(WebSocket conn, int code, String reason, boolean remote) {
    LOGGER.log(Level.INFO, "Client disconnected: {0}", conn.getRemoteSocketAddress());
  }

  /**
   * Merges incoming client state into the server state and broadcasts the result.
   *
   * <p>The message is expected to be a JSON-serialized {@link no.ntnu.crdt.dto.ToDoListStateDto}.
   * After merging, the updated server state is broadcast to all connected clients,
   * including the sender, so every replica converges to the same value.</p>
   *
   * <p>The merge and broadcast are performed inside a {@code synchronized} block
   * to prevent concurrent messages from corrupting the shared server state.</p>
   *
   * @param conn    the client connection that sent the message
   * @param message the JSON-serialized to do list state from the client
   */
  @Override
  public void onMessage(WebSocket conn, String message) {
    LOGGER.log(Level.INFO, "Received message from {0}", conn.getRemoteSocketAddress());
    try {
      ToDoList incoming = serializer.deserialize(message);

      synchronized (serverState) {
        serverState.merge(incoming);
        String merged = serializer.serialize(serverState);
        broadcast(merged);
      }
    } catch (JsonProcessingException e) {
      LOGGER.log(Level.WARNING, "Failed to process message from {0}", conn.getRemoteSocketAddress());
    }
  }

  /**
   * Logs WebSocket errors.
   *
   * @param conn the connection on which the error occurred, or {@code null} for server-level errors
   * @param ex   the exception that was thrown
   */
  @Override
  public void onError(WebSocket conn, Exception ex) {
    LOGGER.log(Level.SEVERE, "WebSocket server error", ex);
  }

  /**
   * Logs when the server has successfully started and is ready to accept connections.
   */
  @Override
  public void onStart() {
    LOGGER.info("WebSocket server started");
  }
}