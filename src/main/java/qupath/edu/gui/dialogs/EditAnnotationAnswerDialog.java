package qupath.edu.gui.dialogs;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import qupath.edu.gui.FocusingTextFieldTableCell;
import qupath.edu.util.ReflectionUtil;
import qupath.edu.api.EduAPI;
import qupath.edu.api.Roles;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;

import java.util.Collections;

public class EditAnnotationAnswerDialog {

    public static boolean openDialog(SimpleAnnotationPane annotationPane) {
        if (!EduAPI.hasRole(Roles.MANAGE_PROJECTS)) {
            Dialogs.showErrorMessage("No permission", "You don't have permissions to edit answers.");
            return true;
        }

        PathObject annotation = annotationPane.getListAnnotations().getSelectionModel().getSelectedItem();

        var isQuiz = SimpleAnnotationPane.isQuiz(getAnswer(annotation));

        /* Question type */

        RadioButton rbTextAnswer = new RadioButton("Text answer");
        RadioButton rbMultiChoice = new RadioButton("Multi choice question");

        ToggleGroup buttonGroup = new ToggleGroup();
        buttonGroup.getToggles().addAll(rbTextAnswer, rbMultiChoice);
        buttonGroup.selectToggle(isQuiz ? rbMultiChoice : rbTextAnswer);

        /* Text answer */

        VBox textAnswerContainer = new VBox();
        textAnswerContainer.setPrefWidth(600);

        TextArea textAreaAnswer = new TextArea();
        textAreaAnswer.setPrefRowCount(2);
        textAreaAnswer.setPrefColumnCount(25);

        if (!(isQuiz)) {
            textAreaAnswer.setText(getAnswer(annotation));
        }

        textAnswerContainer.getChildren().add(textAreaAnswer);
        textAnswerContainer.visibleProperty().bind(rbTextAnswer.selectedProperty());
        textAnswerContainer.managedProperty().bind(rbTextAnswer.selectedProperty());

        /* Multi-choice answer */

        VBox multiChoiceContainer = new VBox();
        multiChoiceContainer.setPrefWidth(600);
        multiChoiceContainer.setPrefHeight(350);

        TableView<SimpleAnnotationPane.MultichoiceOption> table = createMultiChoiceTable(annotation);

        Button newEntryButton = new Button("Add new");
        newEntryButton.setOnMouseClicked(e -> addRowToTable(table));

        Button deleteEntryButton = new Button("Remove selected");
        deleteEntryButton.setOnMouseClicked(e -> removeRowFromTable(table));

        GridPane controlButtons = PaneTools.createColumnGridControls(newEntryButton, deleteEntryButton);
        controlButtons.setHgap(5);
        controlButtons.setPadding(new Insets(5));

        multiChoiceContainer.getChildren().addAll(table, controlButtons);
        multiChoiceContainer.visibleProperty().bind(rbMultiChoice.selectedProperty());
        multiChoiceContainer.managedProperty().bind(rbMultiChoice.selectedProperty());

        /* Pane */

        int row = 0;

        Separator separator = new Separator();

        GridPane pane = new GridPane();
        pane.setPadding(new Insets(10));
        pane.setVgap(10);
        pane.setHgap(10);

        pane.add(rbTextAnswer, 0, ++row);
        pane.add(rbMultiChoice, 1, row);

        pane.add(separator, 0, ++row);

        pane.add(textAnswerContainer, 0, ++row);
        pane.add(multiChoiceContainer, 0, ++row);

        GridPane.setColumnSpan(separator, 2);
        GridPane.setColumnSpan(textAnswerContainer, 2);
        GridPane.setColumnSpan(multiChoiceContainer, 2);

        GridPane.setFillWidth(separator, true);
        GridPane.setFillWidth(textAnswerContainer, true);
        GridPane.setFillWidth(multiChoiceContainer, true);

        /* Dialog */

        Dialog<ButtonType> dialog = Dialogs.builder()
                .title("Set answer properties")
                .headerText("Select question type")
                .content(pane)
                .buttons(ButtonType.OK, ButtonType.CANCEL)
                .width(600)
                .height(400)
                .build();

        var result = dialog.showAndWait();

        if (result.isEmpty() || !(result.get().equals(ButtonType.OK))) {
            return false;
        }

        /* Save changes */

        if (rbTextAnswer.isSelected()) {
            setAnswer(annotation, textAreaAnswer.getText());
        } else {
            String json = GsonTools.getInstance().toJson(table.getItems());
            setAnswer(annotation, json);
        }

        annotationPane.getHierarchy().fireObjectsChangedEvent(null, Collections.singleton(annotation));
        annotationPane.getHierarchy().getSelectionModel().setSelectedObject(annotation);

        return true;
    }

    private static TableView<SimpleAnnotationPane.MultichoiceOption> createMultiChoiceTable(PathObject annotation) {
        /* Table */

        TableView<SimpleAnnotationPane.MultichoiceOption> table = new TableView<>();
        table.setPlaceholder(new Text("No data"));
        table.setEditable(true);

        TableColumn<SimpleAnnotationPane.MultichoiceOption, String> choicesColumn = new TableColumn<>("Choice");
        choicesColumn.setEditable(true);
        choicesColumn.prefWidthProperty().bind(table.widthProperty().multiply(0.75));
        choicesColumn.setCellValueFactory(new PropertyValueFactory<>("choice"));
        choicesColumn.setCellFactory(tc -> new FocusingTextFieldTableCell<>());

        TableColumn<SimpleAnnotationPane.MultichoiceOption, Boolean> answersColumn = new TableColumn<>("Answer(s)");
        answersColumn.setEditable(true);
        answersColumn.prefWidthProperty().bind(table.widthProperty().multiply(0.25));
        answersColumn.setCellValueFactory(new PropertyValueFactory<>("isAnswer"));
        answersColumn.setCellFactory(col -> new CheckBoxTableCell<>(index -> {
            BooleanProperty active = new SimpleBooleanProperty(table.getItems().get(index).getIsAnswer());

            active.addListener((obs, wasActive, isNowActive) -> {
                SimpleAnnotationPane.MultichoiceOption item = table.getItems().get(index);
                item.setIsAnswer(isNowActive);
            });

            return active;
        }));

        choicesColumn.setOnEditCommit(event -> {
            if (event.getNewValue() == null || event.getNewValue().isEmpty()) {
                table.getItems().remove(table.getSelectionModel().getFocusedIndex());
            } else {
                event.getRowValue().setChoice(event.getNewValue());
            }
        });

        table.getColumns().addAll(choicesColumn, answersColumn);

        /* Populate Table */

        String answer = getAnswer(annotation);

        if (SimpleAnnotationPane.isQuiz(answer)) {
            SimpleAnnotationPane.MultichoiceOption[] choices = GsonTools.getInstance().fromJson(answer, SimpleAnnotationPane.MultichoiceOption[].class);

            table.getItems().addAll(choices);
        }

        return table;
    }

    private static void addRowToTable(TableView<SimpleAnnotationPane.MultichoiceOption> table) {
        table.getItems().add(new SimpleAnnotationPane.MultichoiceOption());
        table.layout();
        table.getSelectionModel().selectLast();
        table.edit(table.getItems().size() - 1, table.getColumns().get(0));
    }

    private static void removeRowFromTable(TableView<SimpleAnnotationPane.MultichoiceOption> table) {
        table.getItems().remove(table.getSelectionModel().getFocusedIndex());
    }

    private static void setAnswer(PathObject annotation, String value) {
        ReflectionUtil.storeMetadataValue(annotation, SimpleAnnotationPane.ANSWER_KEY, value);
    }

    private static String getAnswer(PathObject annotation) {
        return (String) ReflectionUtil.retrieveMetadataValue(annotation, SimpleAnnotationPane.ANSWER_KEY);
    }
}
