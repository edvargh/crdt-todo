package no.ntnu.crdt.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.stage.Stage;
import no.ntnu.crdt.server.ToDoWebSocketServer;

/**
 * JavaFX entry point for the CRDT to-do application.
 *
 * <p>Starts the {@link ToDoWebSocketServer} and opens a window with two
 * side-by-side {@link ClientPane} instances, both connected to the same server.
 * This lets you demonstrate CRDT convergence, concurrent edits, and
 * offline/reconnect behaviour all within a single window.</p>
 */
public class ToDoApplication extends Application {

  private static final int PORT = 8080;

  private ToDoWebSocketServer server;

  /** Creates a new {@code ToDoApplication} instance (called by the JavaFX runtime). */
  public ToDoApplication() {
    // default constructor required by JavaFX
  }

  /**
   * Starts the WebSocket server, waits until it is ready, then opens the main window
   * with two client panes side by side.
   *
   * @param stage the primary stage provided by the JavaFX runtime
   * @throws InterruptedException if the thread is interrupted while waiting for the server
   */
  @Override
  public void start(Stage stage) throws InterruptedException {
    server = new ToDoWebSocketServer(PORT);
    server.start();
    server.waitUntilStarted();

    ClientPane clientA = new ClientPane("Client A", PORT);
    ClientPane clientB = new ClientPane("Client B", PORT);

    SplitPane splitPane = new SplitPane(clientA, clientB);
    Scene scene = new Scene(splitPane, 750, 450);

    stage.setTitle("CRDT To-Do List");
    stage.setScene(scene);
    stage.show();
  }

  /**
   * Stops the WebSocket server when the application window is closed.
   *
   * @throws InterruptedException if the thread is interrupted while stopping the server
   */
  @Override
  public void stop() throws InterruptedException {
    server.stop();
  }

  /**
   * Application entry point.
   *
   * @param args command-line arguments (not used)
   */
  public static void main(String[] args) {
    launch(args);
  }
}
