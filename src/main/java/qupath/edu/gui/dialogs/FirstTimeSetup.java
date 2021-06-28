package qupath.edu.gui.dialogs;

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import qupath.edu.EduOptions;
import qupath.edu.gui.ServerListCell;
import qupath.edu.api.EduAPI;
import qupath.edu.models.Server;
import qupath.lib.gui.dialogs.Dialogs;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

public class FirstTimeSetup {

    private BorderPane pane;
    private static Dialog<ButtonType> dialog;

    private final SimpleBooleanProperty isValidHost = new SimpleBooleanProperty(false);
    private final SimpleStringProperty hostProperty = new SimpleStringProperty();

    private ComboBox<Server> cbServerMenu;

    private final Server customServer = new Server("Custom server");
    private final List<Server> servers = EduAPI.fetchPublicServers();

    public static void showDialog() {
        FirstTimeSetup firstTimeSetupDialog = new FirstTimeSetup();

        dialog = Dialogs.builder()
                .title("First-time setup")
                .content(firstTimeSetupDialog.getPane())
                .build();

        dialog.getDialogPane().getStylesheets().add(FirstTimeSetup.class.getClassLoader().getResource("css/remove_buttonbar.css").toExternalForm());
        dialog.setResult(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    public synchronized BorderPane getPane() {
        if (pane == null) {
            initializePane();
        }

        return pane;
    }

    private synchronized void initializePane() {
        pane = new BorderPane();

        VBox vbox = new VBox(10);
        vbox.setAlignment(Pos.CENTER);

        /* Header */

        Text title = new Text("Welcome to QuPath Edu");
        title.setFont(Font.font(16));
        title.setTextAlignment(TextAlignment.CENTER);

        Label smaller = new Label("To continue you must first configure your server. Please enter host below or select from the dropdown menu.");
        smaller.setWrapText(true);
        smaller.setMaxWidth(300);

        /* Host selection */

        cbServerMenu = new ComboBox<>();
        cbServerMenu.setPromptText("Select a public host or enter private host below");
        cbServerMenu.setCellFactory(f -> new ServerListCell());
        cbServerMenu.setButtonCell(new ServerListCell());
        cbServerMenu.prefWidthProperty().bind(pane.widthProperty());

        TextField tfHost = new TextField();
        tfHost.textProperty().addListener(this::update);

        cbServerMenu.getItems().add(customServer);
        cbServerMenu.getItems().addAll(servers);
        cbServerMenu.valueProperty().addListener((obs, oldServer, newServer) -> {
            if (newServer != null && newServer != customServer) {
                tfHost.setText(newServer.getHost());
            }
        });

        Text txtValidator = new Text();
        txtValidator.textProperty().bind(Bindings.when(isValidHost).then("Valid host").otherwise("Invalid host"));
        txtValidator.fillProperty().bind(Bindings.when(isValidHost).then(Color.GREEN).otherwise(Color.RED));
        txtValidator.setFont(Font.font(10));

        /* Buttons */

        Button btnSave = new Button("Save preferences");
        btnSave.disableProperty().bind(isValidHost.not());
        btnSave.setOnAction(a -> savePreferences());

        Button btnExit = new Button("Exit");
        btnExit.setOnAction(a -> dialog.close());

        ButtonBar buttonBar = new ButtonBar();
        buttonBar.getButtons().addAll(btnExit, btnSave);

        /* Pane */

        vbox.getChildren().addAll(title, smaller, cbServerMenu, tfHost, txtValidator);

        pane.setCenter(vbox);
        pane.setBottom(buttonBar);
        pane.setPadding(new Insets(10));

        BorderPane.setMargin(vbox, new Insets(10, 0, 10, 0));

        /* Bindings */

        hostProperty.bind(tfHost.textProperty());
    }

    private void savePreferences() {
        EduOptions.host().set(hostProperty.getValue());
        dialog.close();
    }

    /**
     * Update validator text and select appropriate host from ComboBox (Custom Server or one from the public server list).
     */
    private void update(Observable obs, String oldHost, String newHost) {
        isValidHost.set(isValidUrl(newHost));

        if (isValidHost.get()) {
            Server server = servers.stream()
                    .filter(s -> s.getHost().equals(newHost))
                    .findFirst()
                    .orElse(null);

            if (server == null) {
                cbServerMenu.setValue(customServer);
            } else if (cbServerMenu.getValue() != server) {
                cbServerMenu.setValue(server);
            }
        } else {
            cbServerMenu.setValue(customServer);
        }
    }

    private boolean isValidUrl(String url) {
        if (url == null) {
            return false;
        }

        try {
            new URL(url).toURI();
            return true;
        } catch (MalformedURLException | URISyntaxException e) {
            return false;
        }
    }
}
