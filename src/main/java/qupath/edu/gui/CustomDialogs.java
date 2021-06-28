package qupath.edu.gui;

import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.api.EduAPI;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.GuiTools;

import java.io.IOException;
import java.util.Optional;

public final class CustomDialogs {

	private static final Logger logger = LoggerFactory.getLogger(CustomDialogs.class);

	public static Optional<String> showWYSIWYGEditor(String input) {
		String resourceRoot = QuPathGUI.class.getResource("/ckeditor/ckeditor.js").toString();
		resourceRoot = resourceRoot.substring(0, resourceRoot.length() - 20); // Hacky wacky way to get jar:file: ... URI

		if (input == null) {
			input = "";
		}

		String HTML;

		try {
			HTML = GeneralTools.readInputStreamAsString(QuPathGUI.class.getResourceAsStream("/html/editor.html"));
			HTML = HTML.replace("{{qupath-input}}", input)
					   .replace("{{qupath-resource-root}}", resourceRoot)
			           .replace("{{qupath-upload-url}}", EduAPI.getCKEditorUploadUrl());

			if (EduAPI.getAuthType() == EduAPI.AuthType.TOKEN) {
				HTML = HTML.replace("{{qupath-auth}}", "'Token': '" + EduAPI.getToken() + "'");
			} else if (EduAPI.getAuthType() == EduAPI.AuthType.USERNAME) {
				HTML = HTML.replace("{{qupath-auth}}", "Authorization: '" + EduAPI.getBasicAuthHeader() + "'");
			} else {
				HTML = HTML.replace("{{qupath-auth}}", "");
			}
		} catch (IOException e) {
			logger.error("Error when opening editor", e);
			Dialogs.showErrorNotification("Error when opening editor", e);
			return Optional.empty();
		}

		/* Dialog */

		Browser browser = new Browser(HTML, false);

		ButtonType btnSave = new ButtonType("Save & Close", ButtonBar.ButtonData.OK_DONE);

		Dialog<ButtonType> dialog = Dialogs.builder()
				.title("Editor")
				.content(browser)
				.buttons(btnSave, ButtonType.CLOSE)
				.modality(Modality.APPLICATION_MODAL)
				.resizable()
				.build();

		dialog.getDialogPane().setPrefWidth(Screen.getPrimary().getBounds().getWidth() * 0.8);
		dialog.getDialogPane().setPrefHeight(Screen.getPrimary().getBounds().getHeight() * 0.8);

		dialog.setOnCloseRequest(confirmCloseEventHandler);

		var result = dialog.showAndWait();
		if (result.isPresent() && result.get() == btnSave) {
			return Optional.of(browser.getWebEngine().executeScript("window.editor.getData();").toString());
		}

		return Optional.empty();
	}

	// This does not work when using the 'X' button to close the dialog.
	private static final EventHandler<DialogEvent> confirmCloseEventHandler = event -> {
		boolean cancel = Dialogs.showYesNoDialog("Exit", "Are you sure you want to exit?");

		if (!cancel) {
			event.consume();
		}
	};

	public static String showTextAreaDialog(final String title, final String message, final String initialInput) {
		if (Platform.isFxApplicationThread()) {
			Dialog<String> dialog = new Dialog<>();
			dialog.setTitle(title);
			if (QuPathGUI.getInstance() != null) {
				dialog.initOwner(QuPathGUI.getInstance().getStage());
			}
			dialog.setHeaderText(message);
			dialog.setResizable(false);
			dialog.setContentText(null);

			TextArea textArea = new TextArea();
			textArea.setPrefColumnCount(30);
			textArea.setPrefRowCount(10);
			textArea.setWrapText(true);
			textArea.positionCaret(0);
			textArea.setText(initialInput);

			dialog.setResultConverter((dialogButton) -> {
				ButtonBar.ButtonData data = dialogButton == null ? null : dialogButton.getButtonData();
				return data == ButtonBar.ButtonData.OK_DONE ? textArea.getText() : null;
			});

			dialog.getDialogPane().getStyleClass().add("text-input-dialog");
			dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
			dialog.getDialogPane().setContent(textArea);

			Platform.runLater(textArea::requestFocus);

			Optional<String> result = dialog.showAndWait();
			if (result.isPresent()) {
				return result.get();
			}
		} else {
			return GuiTools.callOnApplicationThread(() -> showTextAreaDialog(title, message, initialInput));
		}

		return null;
	}
}
