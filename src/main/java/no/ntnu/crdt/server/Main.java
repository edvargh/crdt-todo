package no.ntnu.crdt.server;

import java.util.logging.Logger;

/**
 * Entry point for starting the CRDT to do WebSocket server.
 */
public class Main {

  private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

  public static void main(String[] args) {
    int port = 8080;
    ToDoWebSocketServer server = new ToDoWebSocketServer(port);
    server.start();

    LOGGER.info("Server running on ws://localhost:" + port);
  }
}