package qupath.edu.gui;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.Event;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;

/**
 * A TextFieldTableCell that saves changes when the textfield is out-of-focus.
 * Works only when clicking other items / empty items on the TableView.
 *
 * Based on <a href="https://stackoverflow.com/a/33919078>this StackOverflow issue</a>
 */
public class FocusingTextFieldTableCell<T> extends TableCell<T, String> {

    protected TextField textField;
    protected ChangeListener<Boolean> changeListener = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
            if (!newValue) {
                commitEdit(textField.getText());
            }
        }
    };

    @Override
    public void startEdit() {
        if (editableProperty().get()) {
            if (!isEmpty()) {
                super.startEdit();
                createTextField();
                setText(null);
                setGraphic(textField);
                textField.requestFocus();
            }
        }
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        setText(getItem());
        setGraphic(null);
    }

    @Override
    public void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);

        if (empty) {
            setText(null);
            setGraphic(null);
        } else {
            if (isEditing()) {
                if (textField != null) {
                    textField.setText(getString());
                    textField.selectAll();
                }

                setText(null);
                setGraphic(textField);
            } else {
                setText(getString());
                setGraphic(null);
            }
        }
    }

    protected void createTextField() {
        textField = new TextField(getString());
        textField.setMinWidth(this.getWidth() - this.getGraphicTextGap() * 2);
        textField.focusedProperty().addListener(changeListener);
        textField.setOnAction(evt -> commitEdit(textField.getText()));

        textField.setOnKeyPressed((ke) -> {
            if (ke.getCode().equals(KeyCode.ESCAPE)) {
                textField.focusedProperty().removeListener(changeListener);
                cancelEdit();
            }

            if (ke.getCode().equals(KeyCode.TAB)) {
                commitEdit(textField.getText());
            }
        });
    }

    protected String getString() {
        return getItem() == null ? "" : getItem();
    }

    @Override
    public void commitEdit(String item) {
        textField.focusedProperty().removeListener(changeListener);

        if (isEditing()) {
            super.commitEdit(item);
        } else {
            TableView<T> table = getTableView();

            if (table != null) {
                TablePosition<T, String> position = new TablePosition<>(
                        getTableView(),
                        getTableRow().getIndex(),
                        getTableColumn()
                );

                TableColumn.CellEditEvent<T, String> editEvent = new TableColumn.CellEditEvent<>(
                        table,
                        position,
                        TableColumn.editCommitEvent(),
                        item
                );

                Event.fireEvent(getTableColumn(), editEvent);
            }

            updateItem(item, false);

            if (table != null) {
                table.edit(-1, null);
            }
        }
    }
}

