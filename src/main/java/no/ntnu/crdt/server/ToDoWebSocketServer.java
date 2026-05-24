package no.ntnu.crdt.server;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Basic WebSocket server for the CRDT to do application.
 */
public class ToDoWebSocketServer extends WebSocketServer {

  private static final Logger LOGGER = Logger.getLogger(ToDoWebSocketServer.class.getName());

  public ToDoWebSocketServer(int port) {
    super(new InetSocketAddress(port));
  }

  @Override
  public void onOpen(WebSocket conn, ClientHandshake handshake) {
    LOGGER.log(Level.INFO, "Client connected: {0}", conn.getRemoteSocketAddress());
  }

  @Override
  public void onClose(WebSocket conn, int code, String reason, boolean remote) {
    LOGGER.log(Level.INFO, "Client disconnected: {0}", conn.getRemoteSocketAddress());
  }

  @Override
  public void onMessage(WebSocket conn, String message) {
    LOGGER.log(Level.INFO, "Received message: {0}", message);
    conn.send("Server received: " + message);
  }

  @Override
  public void onError(WebSocket conn, Exception ex) {
    LOGGER.log(Level.SEVERE, "WebSocket server error", ex);
  }

  @Override
  public void onStart() {
    LOGGER.info("WebSocket server started");
  }
}