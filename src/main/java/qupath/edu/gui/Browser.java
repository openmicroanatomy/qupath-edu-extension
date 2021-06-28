package qupath.edu.gui;

import com.sun.javafx.webkit.WebConsoleListener;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker.State;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.layout.Region;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;

import java.awt.*;
import java.net.URI;

// Based on: http://java-no-makanaikata.blogspot.com/2012/10/javafx-webview-size-trick.html
public class Browser extends Region {

    private final Logger logger = LoggerFactory.getLogger(Browser.class);

    private WebView webView = new WebView();
    private WebEngine webEngine = webView.getEngine();

    private boolean textHighlightable = true;

    public boolean isTextHighlightable() {
        return textHighlightable;
    }

    public void setTextHighlightable(boolean textHighlightable) {
        this.textHighlightable = textHighlightable;
    }

    public Browser() {
        this("");
    }

    public Browser(String content) {
        this(content, true);
    }

    public Browser(String content, boolean body) {
        webView.setPrefHeight(0);

        widthProperty().addListener((ChangeListener<Object>) (observable, oldWidth, newWidth) -> {
            webView.setPrefWidth((Double) newWidth);
            adjustHeight();
        });

        webView.getEngine().getLoadWorker().stateProperty().addListener((arg0, oldState, newState) -> {
            if (newState == State.SUCCEEDED) {
                adjustHeight();
            }
        });

        webEngine.locationProperty().addListener((observable, oldValue, location) -> {
            if (!location.isEmpty()) {
                Platform.runLater(() -> {
                    try {
                        URI uri = new URI(location);
                        Desktop.getDesktop().browse(uri);
                    } catch (Exception ignored) {}

                    // Works, but isn't perfect. Should maintain the previous URI
                    webEngine.getLoadWorker().cancel();
                });
            }
        });

        webEngine.setOnAlert(event -> showAlert(event.getData()));
        webEngine.setConfirmHandler(this::showConfirm);

        webView.setContextMenuEnabled(false);

        WebConsoleListener.setDefaultListener((webView, message, lineNumber, sourceId) -> {
            logger.info("WebView: " + message + "[at " + lineNumber + "]");
        });

        setContent(content, body);
        getChildren().add(webView);
    }

    public void setContent(String content) {
        setContent(content, true);
    }

    public void setContent(String content, boolean body) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> setContent(content, body));
            return;
        }

        webEngine.loadContent(getHtml(content, body));
        adjustHeight();
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        layoutInArea(webView, 0, 0, w, h, 0, HPos.CENTER, VPos.CENTER);
    }

    private void adjustHeight() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::adjustHeight);
            return;
        }

        try {
            Object result = webEngine.executeScript("document.getElementById('content').clientHeight");
            if (result instanceof Integer) {
                double height = Double.parseDouble(result.toString());
                if (height > 0) {
                    height += 20;
                }

                webView.setPrefHeight(height);
                this.setPrefHeight(height);
            }
        } catch (JSException ignored) {
            webView.setPrefHeight(0);
        }
    }

    private String getHtml(String content, boolean body) {
        content = content == null ? "" : content;

        StringBuilder CSS = new StringBuilder();

        if (!isTextHighlightable()) {
            CSS.append(".ck { -webkit-user-select: none; cursor: default; }");
        }

        String resourceRoot = QuPathGUI.class.getResource("/ckeditor/ckeditor.js").toString();
        resourceRoot = resourceRoot.substring(0, resourceRoot.length() - 20); // Hacky wacky way to get jar:file: ... URI

        if (body) {
            return ("<html>" +
                    "<head>" +
                        "<base href=\"" + resourceRoot + "\" />" +
                        "<style>" + CSS + "</style>" +
                        "<link rel=\"stylesheet\" type=\"text/css\" href=\"css/ckeditor.css\">" +
                    "</head>" +
                    "<body id=\"content\">" +
                        "<div class=\"ck ck-content\">" + content + "</div>" +
                    "</body>" +
                    "</html>");
        }

        return content;
    }

    public WebView getWebView() {
        return webView;
    }

    public WebEngine getWebEngine() {
        return webEngine;
    }

    private void showAlert(String message) {
        Dialog<Void> alert = new Dialog<>();
        alert.getDialogPane().setContentText(message);
        alert.getDialogPane().getButtonTypes().add(ButtonType.OK);
        alert.showAndWait();
    }

    private boolean showConfirm(String message) {
        Dialog<ButtonType> confirm = new Dialog<>();
        confirm.getDialogPane().setContentText(message);
        confirm.getDialogPane().getButtonTypes().addAll(ButtonType.YES, ButtonType.NO);
        boolean result = confirm.showAndWait().filter(ButtonType.YES::equals).isPresent();

        return result;
    }
}
