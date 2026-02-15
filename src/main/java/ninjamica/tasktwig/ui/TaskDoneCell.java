package ninjamica.tasktwig.ui;

import javafx.scene.control.TableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import ninjamica.tasktwig.task.Task;

public class TaskDoneCell extends TableCell<Task, Boolean> {
    private static Image todoIcon;
    private static Image doneIcon;

    public TaskDoneCell() {
        if (todoIcon == null)
            todoIcon = new Image(getClass().getResource("todo-16.png").toExternalForm());

        if (doneIcon == null)
            doneIcon = new Image(getClass().getResource("done-16.png").toExternalForm());

        this.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && !this.isEmpty()) {
                boolean done = !this.getItem();
                this.setItem(done);
                updateIcon();
                this.getTableView().getItems().get(this.getIndex()).setCompletion(done);
                this.getTableView().refresh();
            }
        });
    }

    @Override
    protected void updateItem(Boolean item, boolean empty) {
        super.updateItem(item, empty);
        if (!empty) {
            updateIcon();
        }
        else {
            setGraphic(null);
        }
    }

    private void updateIcon() {
        if (this.isEmpty()) { return; }

        if (this.getItem()) {
            setGraphic(new ImageView(doneIcon));
//            getTableRow().setStyle("-fx-background-color: dimgray");
//            getTableRow().setFont(Font.);
//            getTableView().refresh();
//            getTableRow().setDisable(true);
        }
        else {
            setGraphic(new ImageView(todoIcon));
//            getTableRow().setStyle("-fx-background-color: white");
//            getTableRow().setDisable(false);
        }
    }
}
