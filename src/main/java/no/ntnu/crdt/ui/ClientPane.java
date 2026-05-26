package no.ntnu.crdt.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TextField;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import no.ntnu.crdt.model.ToDoItem;
import no.ntnu.crdt.model.ToDoList;
import no.ntnu.crdt.model.ToDoListSerializer;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A JavaFX pane representing a single CRDT to-do list client.
 *
 * <p>Each pane owns its own {@link ToDoList} replica and {@link WebSocketClient}
 * connection to the server. Changes made locally are sent to the server immediately.
 * Incoming broadcasts from the server are merged into the local state and the list
 * view is refreshed.</p>
 *
 * <p>The pane can be disconnected and reconnected via the toggle button. While
 * disconnected, local changes accumulate in the {@link ToDoList}. On reconnect the
 * accumulated state is sent to the server, which merges it with any changes made by
 * other clients in the meantime.</p>
 *
 * <p>Items can be reordered by drag and drop. Dropping onto a row inserts above it;
 * dropping below all rows moves the item to the end.</p>
 */
public class ClientPane extends VBox {

  private static final Logger LOGGER = Logger.getLogger(ClientPane.class.getName());

  private final String clientId;
  private final int serverPort;
  private final ToDoList todoList;
  private final ToDoListSerializer serializer = new ToDoListSerializer();
  private WebSocketClient wsClient;

  private final ListView<ToDoItem> listView = new ListView<>();
  private final TextField textField = new TextField();
  private final Button addButton = new Button("Add");
  private final Button editButton = new Button("Edit");
  private final Button removeButton = new Button("Remove");
  private final Button toggleButton = new Button("Disconnect");
  private final Label statusLabel = new Label("● Connected");

  /**
   * Creates a new client pane and immediately connects to the server.
   *
   * @param clientId   unique replica ID for this client
   * @param serverPort port the WebSocket server is listening on
   */
  public ClientPane(String clientId, int serverPort) {
    this.clientId = clientId;
    this.serverPort = serverPort;
    this.todoList = new ToDoList(clientId);
    buildUI();
    connect();
  }

  // --- UI setup ---

  private void buildUI() {
    Label titleLabel = new Label(clientId);
    titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
    statusLabel.setStyle("-fx-text-fill: green;");
    toggleButton.setOnAction(e -> toggleConnection());

    HBox header = new HBox(10, titleLabel, statusLabel, toggleButton);
    header.setAlignment(Pos.CENTER_LEFT);

    listView.setCellFactory(lv -> new ToDoItemCell(this));

    // dropped below all cells: move dragged item to the end
    listView.setOnDragOver(event -> {
      if (event.getDragboard().hasString()) {
        event.acceptTransferModes(TransferMode.MOVE);
      }
      event.consume();
    });

    listView.setOnDragDropped(event -> {
      Dragboard db = event.getDragboard();
      boolean success = false;

      if (db.hasString()) {
        String draggedId = db.getString();
        List<ToDoItem> currentItems = listView.getItems();

        if (!currentItems.isEmpty()) {
          ToDoItem lastItem = currentItems.get(currentItems.size() - 1);
          // Skip if the dragged item is already the last one
          if (!lastItem.getId().equals(draggedId)) {
            todoList.moveItem(draggedId, todoList.getPosition(lastItem.getId()), null);
            refreshListView();
            sendState();
            success = true;
          }
        }
      }

      event.setDropCompleted(success);
      event.consume();
    });

    textField.setPromptText("New item...");
    textField.setOnAction(e -> addItem());
    HBox.setHgrow(textField, Priority.ALWAYS);
    addButton.setOnAction(e -> addItem());
    editButton.setOnAction(e -> editSelectedItem());
    removeButton.setOnAction(e -> removeSelectedItem());

    HBox inputRow = new HBox(5, textField, addButton, editButton, removeButton);

    getChildren().addAll(header, listView, inputRow);
    setPadding(new Insets(12));
    setSpacing(8);
    VBox.setVgrow(listView, Priority.ALWAYS);
  }

  // --- user actions ---

  private void addItem() {
    String text = textField.getText().trim();
    if (text.isEmpty()) {
      return;
    }
    todoList.addItem(text);
    textField.clear();
    refreshListView();
    sendState();
  }

  private void editSelectedItem() {
    ToDoItem selected = listView.getSelectionModel().getSelectedItem();
    if (selected == null) {
      return;
    }
    TextInputDialog dialog = new TextInputDialog(todoList.getText(selected.getId()));
    dialog.setTitle("Edit item");
    dialog.setHeaderText(null);
    dialog.setContentText("New text:");
    dialog.showAndWait().ifPresent(newText -> {
      if (!newText.isBlank()) {
        todoList.editItem(selected.getId(), newText);
        refreshListView();
        sendState();
      }
    });
  }

  private void removeSelectedItem() {
    ToDoItem selected = listView.getSelectionModel().getSelectedItem();
    if (selected == null) {
      return;
    }
    todoList.removeItem(selected);
    refreshListView();
    sendState();
  }

  private void toggleConnection() {
    if (wsClient != null && wsClient.isOpen()) {
      wsClient.close();
    } else {
      connect();
    }
  }

  // --- WebSocket ---

  private void connect() {
    try {
      wsClient = new WebSocketClient(new URI("ws://localhost:" + serverPort)) {

        @Override
        public void onOpen(ServerHandshake handshake) {
          // Send accumulated local state immediately so the server can merge
          // any changes made while this client was offline.
          // Both sendState() and setStatus() must run on the JavaFX thread
          // because sendState() reads todoList, which is otherwise only touched
          // from the JavaFX thread.
          Platform.runLater(() -> {
            sendState();
            setStatus("● Connected", "green", "Disconnect");
          });
        }

        @Override
        public void onMessage(String message) {
          try {
            // Deserialize on the WebSocket thread (no shared state touched).
            // The merge and refresh must run on the JavaFX thread because
            // todoList is not thread-safe and all other accesses happen there.
            ToDoList received = serializer.deserialize(message);
            Platform.runLater(() -> {
              todoList.merge(received);
              refreshListView();
            });
          } catch (JsonProcessingException e) {
            LOGGER.log(Level.WARNING, "Failed to deserialize incoming state", e);
          }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
          Platform.runLater(() -> setStatus("● Disconnected", "red", "Connect"));
        }

        @Override
        public void onError(Exception ex) {
          LOGGER.log(Level.SEVERE, "WebSocket error in " + clientId, ex);
        }
      };
      wsClient.connect();
    } catch (URISyntaxException e) {
      LOGGER.log(Level.SEVERE, "Invalid WebSocket URI for port " + serverPort, e);
    }
  }

  private void sendState() {
    if (wsClient != null && wsClient.isOpen()) {
      try {
        wsClient.send(serializer.serialize(todoList));
      } catch (JsonProcessingException e) {
        LOGGER.log(Level.WARNING, "Failed to serialize local state for sending", e);
      }
    }
  }

  // --- helpers ---

  private void refreshListView() {
    listView.getItems().setAll(
        todoList.getItems().stream()
            .sorted(Comparator.comparingDouble(item -> todoList.getPosition(item.getId())))
            .toList()
    );
  }

  private void setStatus(String text, String color, String buttonLabel) {
    statusLabel.setText(text);
    statusLabel.setStyle("-fx-text-fill: " + color + ";");
    toggleButton.setText(buttonLabel);
  }

  // --- inner classes ---

  /**
   * A list cell that renders a to-do item with a checkbox and drag-and-drop support.
   */
  private static final class ToDoItemCell extends ListCell<ToDoItem> {

    private final ClientPane pane;
    private final CheckBox checkBox = new CheckBox();
    private final Label label = new Label();
    private final HBox cellBox = new HBox(8, checkBox, label);

    ToDoItemCell(ClientPane pane) {
      this.pane = pane;
      cellBox.setAlignment(Pos.CENTER_LEFT);

      checkBox.setOnAction(e -> {
        ToDoItem item = getItem();
        if (item != null) {
          pane.todoList.setFinished(item.getId(), checkBox.isSelected());
          pane.refreshListView();
          pane.sendState();
        }
      });

      // drag source: put the dragged item's UUID onto the dragboard
      setOnDragDetected(event -> {
        if (getItem() == null) {
          return;
        }
        Dragboard db = startDragAndDrop(TransferMode.MOVE);
        ClipboardContent content = new ClipboardContent();
        content.putString(getItem().getId());
        db.setContent(content);
        event.consume();
      });

      // accept only on non-empty cells; empty cells let the event bubble to the ListView
      setOnDragOver(event -> {
        if (getItem() != null
            && event.getGestureSource() != this
            && event.getDragboard().hasString()) {
          event.acceptTransferModes(TransferMode.MOVE);
          event.consume();
        }
      });

      // drop: insert above this cell; bail if cell is empty
      setOnDragDropped(event -> {
        if (getItem() == null) {
          return;
        }
        Dragboard db = event.getDragboard();
        boolean success = false;

        if (db.hasString()) {
          String draggedId = db.getString();
          ToDoItem targetItem = getItem();

          if (!targetItem.getId().equals(draggedId)) {
            List<ToDoItem> currentItems = pane.listView.getItems();
            int targetIndex = currentItems.indexOf(targetItem);

            Double prevPos = targetIndex > 0
                ? pane.todoList.getPosition(currentItems.get(targetIndex - 1).getId())
                : null;
            Double nextPos = pane.todoList.getPosition(targetItem.getId());

            pane.todoList.moveItem(draggedId, prevPos, nextPos);
            pane.refreshListView();
            pane.sendState();
            success = true;
          }
        }

        event.setDropCompleted(success);
        event.consume();
      });
    }

    @Override
    protected void updateItem(ToDoItem item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setGraphic(null);
      } else {
        boolean finished = pane.todoList.getFinished(item.getId());
        checkBox.setSelected(finished);
        label.setText(pane.todoList.getText(item.getId()));
        label.setStyle(finished ? "-fx-strikethrough: true; -fx-text-fill: gray;" : "");
        setGraphic(cellBox);
      }
    }
  }
}
