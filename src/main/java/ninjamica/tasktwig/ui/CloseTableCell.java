package ninjamica.tasktwig.ui;

import javafx.scene.control.TableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * A table cell representing a delete button (or an X).
 * When left-clicked it simply deletes the row containing this cell.
 * Template types do not matter and are ignored
 */
public class CloseTableCell<S, T> extends TableCell<S, T> {
    private static Image closeIcon;

    public CloseTableCell() {

        if (closeIcon == null) {
            closeIcon = new Image(getClass().getResource("close-16.png").toExternalForm());
        }

        // Mouse click listener, deletes row from table's ObservableList
        this.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && !this.isEmpty()) {
                this.getTableView().getItems().remove(this.getIndex());
                this.getTableView().refresh();
            }
        });
    }

    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);

        if (!empty) {
            setGraphic(new ImageView(closeIcon));
        }
        else {
            setGraphic(null);
        }
    }
}