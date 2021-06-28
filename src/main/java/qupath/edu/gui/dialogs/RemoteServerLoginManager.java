package qupath.edu.gui.dialogs;

import com.microsoft.aad.msal4j.*;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;
import org.controlsfx.control.StatusBar;
import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.EduExtension;
import qupath.edu.EduOptions;
import qupath.edu.api.EduAPI;
import qupath.edu.api.Roles;
import qupath.edu.models.ExternalOrganization;
import qupath.edu.models.ServerConfiguration;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.PaneTools;

import java.awt.*;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class RemoteServerLoginManager {

    private static Logger logger = LoggerFactory.getLogger(RemoteServerLoginManager.class);

    private final StringProperty selectedOrganizationProperty = new SimpleStringProperty();

    private BorderPane pane;
    private static Dialog<ButtonType> dialog;

    public static void showLoginDialog() {
        RemoteServerLoginManager loginDialog = new RemoteServerLoginManager();

        dialog = Dialogs.builder()
                .title("Login")
                .content(loginDialog.getPane())
                .build();

        dialog.getDialogPane().getStylesheets().add(RemoteServerLoginManager.class.getClassLoader().getResource("css/remove_buttonbar.css").toExternalForm());
        dialog.setResult(ButtonType.CLOSE);
        dialog.show();
    }

    public synchronized BorderPane getPane() {
        if (pane == null) {
            initializePane();
        }

        return pane;
    }

    private synchronized void initializePane() {
        ServerConfiguration serverConfiguration = EduAPI.getServerConfiguration();
        pane = new BorderPane();

        /* Logos */
        
        ComboBox<ExternalOrganization> cbLogos = new ComboBox<>();
        cbLogos.prefWidthProperty().bind(pane.widthProperty());
        cbLogos.setPlaceholder(new Text("No organizations available."));
        cbLogos.setButtonCell(new ImageViewListCell(false));
        cbLogos.setCellFactory(f -> new ImageViewListCell(true));
        cbLogos.getItems().addAll(EduAPI.getAllOrganizations().orElse(Collections.emptyList()));

        selectPreviousOrganization(cbLogos);

        selectedOrganizationProperty.bind(Bindings.createStringBinding(
            () -> cbLogos.getSelectionModel().getSelectedItem().getId(),
            cbLogos.getSelectionModel().selectedItemProperty()
        ));

        EduOptions.previousOrganization().bind(selectedOrganizationProperty);

        /* Buttons */
        
        Button btnLoginGuest = new Button("Continue as guest");
        btnLoginGuest.setOnAction(e -> loginAsGuest());
        btnLoginGuest.setDisable(!(serverConfiguration.isGuestLoginEnabled()));
        
        Button btnLoginSimple = new Button("Login with credentials");
        btnLoginSimple.setOnAction(e -> showAuthDialog());
        btnLoginSimple.setDisable(!(serverConfiguration.isSimpleLoginEnabled()));
        
        Button btnLoginMicrosoft = new Button("Login using Microsoft");
        btnLoginMicrosoft.setOnAction(e -> showMicrosoftAuthDialog());
        btnLoginMicrosoft.setDisable(!(serverConfiguration.isMicrosoftLoginEnabled()));

        GridPane buttons = PaneTools.createRowGridControls(btnLoginGuest, new Separator(), btnLoginSimple, btnLoginMicrosoft);
        buttons.setPadding(new Insets(10));
        buttons.setVgap(10);

        /* Statusbar */
        
        StatusBar statusBar = new StatusBar();
        statusBar.setText("Host " + EduOptions.host().get());

        /* Borderpane */

        BorderPane.setMargin(cbLogos, new Insets(10));

        pane.setPrefWidth(360);
        pane.setTop(cbLogos);
        pane.setCenter(buttons);
        pane.setBottom(statusBar);
        pane.setPadding(new Insets(0));
    }

    /**
     * Selects the previous organization the user had selected. If no organization was selected
     * (e.g. first startup) then the 1st organization is selected.
     *
     * @param cbLogos list of ComboBoxes
     */
    private void selectPreviousOrganization(ComboBox<ExternalOrganization> cbLogos) {
        cbLogos.getSelectionModel().select(0);

        if (EduOptions.previousOrganization().get() != null) {
            for (ExternalOrganization organization : cbLogos.getItems()) {
                if (organization.getId().equals(EduOptions.previousOrganization().get())) {
                    cbLogos.getSelectionModel().select(organization);
                }
            }
        }
    }

    private void loginAsGuest() {
        EduAPI.setAuthType(EduAPI.AuthType.GUEST);
        EduAPI.setOrganizationId(selectedOrganizationProperty.get());

        dialog.close();

        EduExtension.setWriteAccess(false);
        EduExtension.showWorkspaceOrLoginDialog();
    }

    private void showAuthDialog() {
        /* Textfields */

        Label labEmail = new Label("Email");
        TextField tfEmail = new TextField();
        labEmail.setLabelFor(tfEmail);
        tfEmail.setPromptText("Email");
        Platform.runLater(tfEmail::requestFocus);

        Label labPassword = new Label("Password");
        PasswordField tfPassword = new PasswordField();
        labPassword.setLabelFor(tfPassword);
        tfPassword.setPromptText("********");

        /* Constraints */

        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setPercentWidth(30);

        ColumnConstraints textFieldColumn = new ColumnConstraints();
        textFieldColumn.setPercentWidth(70);

        /* Pane */

        GridPane loginPane = new GridPane();
        loginPane.getColumnConstraints().addAll(labelColumn, textFieldColumn);
        loginPane.setHgap(5);
        loginPane.setVgap(5);

        int row = 0;
        loginPane.add(labEmail, 0, row);
        loginPane.add(tfEmail, 1, row++);
        loginPane.add(labPassword, 0, row);
        loginPane.add(tfPassword, 1, row);

        /* Dialog */

        Optional<ButtonType> choice = Dialogs.builder()
            .buttons(new ButtonType("Login", ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL)
            .title("Authenticate")
            .content(loginPane)
            .build()
            .showAndWait();

        if (choice.isPresent() && choice.get().getButtonData() == ButtonBar.ButtonData.OK_DONE) {
            if (EduAPI.login(tfEmail.getText(), tfPassword.getText())) {
                dialog.close();

                EduExtension.setWriteAccess(EduAPI.hasRole(Roles.MANAGE_PROJECTS));
                EduExtension.showWorkspaceOrLoginDialog();
            } else {
                Dialogs.showErrorNotification("Error", "Wrong username, password or host");
                showAuthDialog();
            }
        }
    }

    private void showMicrosoftAuthDialog() {
        try {
            PublicClientApplication app = PublicClientApplication
                .builder("eccc9211-faa5-40d5-9ff9-7a5087dbcadb")
                .authority("https://login.microsoftonline.com/common/")
                .build();

            Set<String> scopes = Set.of("user.read", "openid", "profile", "email");

            SystemBrowserOptions options = SystemBrowserOptions
                .builder()
                .openBrowserAction(new JavaFXOpenBrowserAction())
                .build();

            InteractiveRequestParameters parameters = InteractiveRequestParameters
                .builder(new URI("http://localhost:51820"))
                .systemBrowserOptions(options)
                .scopes(scopes)
                .build();

            CompletableFuture<IAuthenticationResult> future = app.acquireToken(parameters);

            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    try {
                        IAuthenticationResult result = future.get();

                        if (EduAPI.validate(result.idToken())) {
                            // TODO: Implement Refresh Tokens [MSAL4J has acquireToken(RefreshTokenParameters parameters)]
                            String[] split = result.account().homeAccountId().split("\\.");
                            EduAPI.setUserId(split[0]);
                            EduAPI.setOrganizationId(split[1]);

                            Platform.runLater(() -> {
                                dialog.close();
                                EduExtension.setWriteAccess(EduAPI.hasRole(Roles.MANAGE_PROJECTS));
                                EduExtension.showWorkspaceOrLoginDialog();
                            });
                        }
                    } catch (CancellationException e) {
                        Dialogs.showInfoNotification(
                            "Authentication cancelled",
                            "Authentication was cancelled by user or timed out."
                        );
                    } catch (InterruptedException | ExecutionException e) {
                        Dialogs.showErrorMessage(
                            "Authentication error",
                            "Error while authenticating with Microsoft, try retrying in a few minutes." +
                            "\n\n" +
                            "Possible reason: authentication timed out after two minutes. [Error: " + e.getLocalizedMessage() + "]"
                        );

                        logger.error("Error while authenticating", e);
                    }

                    return null;
                }
            };

            ProgressDialog progress = new ProgressDialog(task);
            progress.setTitle("Logging in ...");
            progress.setHeaderText("Waiting for user");
            progress.setContentText("Follow the instructions in your browser.");
            progress.initOwner(dialog.getOwner());
            progress.getDialogPane().setGraphic(null);
            progress.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
            progress.getDialogPane().lookupButton(ButtonType.CANCEL).addEventFilter(ActionEvent.ACTION, e -> {
                task.cancel(true);
                progress.setHeaderText("Cancelling...");
                progress.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);

                e.consume();
            });

            QuPathGUI.getInstance().submitShortTask(task);
            progress.showAndWait();
        } catch (Exception e) {
            Dialogs.showErrorNotification("Error", "Error while logging in. See log for more details");
            logger.error(e.getLocalizedMessage(), e);
        }
    }

    static class ImageViewListCell extends ListCell<ExternalOrganization> {

        /**
         * Flag to indicate whether this is the factory for the button or cells.
         */
        private boolean buttonCell;

        public ImageViewListCell(boolean buttonCell) {
            this.buttonCell = buttonCell;
        }

        {
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(ExternalOrganization item, boolean empty) {
            super.updateItem(item, empty);

            if (item == null || empty) {
                setGraphic(null);
            } else {
                Image image = new Image(item.getLogoUrl());

                if (image.getException() == null) {
                    setLogoAsGraphic(image);
                } else {
                    setNameAsGraphic(item.getName());
                }
            }
        }

        private void setLogoAsGraphic(Image image) {
            ImageView imageView = new ImageView(image);

            if (buttonCell) {
                imageView.setFitWidth(330);
            } else {
                imageView.setFitWidth(310);
            }

            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            imageView.setCache(true);

            setGraphic(imageView);
        }

        private void setNameAsGraphic(String name) {
            setGraphic(new Text(name));
        }
    }

    static class JavaFXOpenBrowserAction implements OpenBrowserAction {

        @Override
        public void openBrowser(URL url){
            try {
                Desktop.getDesktop().browse(url.toURI());
            } catch (Exception e) {
                logger.error("Error while opening browser", e);

                Dialogs.showInputDialog(
                    "Not supported",
                    "Open this web page to authenticate yourself.",
                    url.toExternalForm()
                );
            }
        }
    }

    /* DEBUG ONLY */
    static class TokenPersistence implements ITokenCacheAccessAspect {
        StringProperty data;

        TokenPersistence(StringProperty data) {
            this.data = data;
        }

        @Override
        public void beforeCacheAccess(ITokenCacheAccessContext iTokenCacheAccessContext) {
            logger.debug("Reading cache");
            logger.debug(data.get());
            iTokenCacheAccessContext.tokenCache().deserialize(data.get());
        }

        @Override
        public void afterCacheAccess(ITokenCacheAccessContext iTokenCacheAccessContext) {
            logger.debug("Saving cache");
            data.set(iTokenCacheAccessContext.tokenCache().serialize());
            logger.debug(data.get());
        }
    }
}
