package ninjamica.tasktwig.ui;

import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Modality;
import javafx.stage.Window;
import ninjamica.tasktwig.Task;

import java.util.Objects;

public class TaskDialog extends Dialog<Task> {

    private final TaskContent taskPane;

    public TaskDialog(Window owner) {
        taskPane = new TaskContent();

        DialogPane dialogPane = new DialogPane();
        dialogPane.setContent(taskPane);
        dialogPane.getStylesheets().add(getClass().getResource("fxml/dark-theme.css").toExternalForm());

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);

        setTitle("Create Task");
        dialogPane.setHeaderText("Create Task");
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        setDialogPane(dialogPane);
        setResultConverter(this::createTask);
    }

//    public TaskDialog(Window owner, Task task) {
//        this(owner);
//        inputTask = task;
//        setTitle(task.getName());
//        getDialogPane().setHeaderText(task.getName());
//        updateFromTask();
//
//        getDialogPane().getButtonTypes().add(1, new ButtonType("Delete", ButtonBar.ButtonData.OTHER));
//    }



    private Task createTask(ButtonType button) {
        if (Objects.equals(ButtonBar.ButtonData.OK_DONE, button.getButtonData())) {
            return taskPane.task;
        } else {
            return null;
        }
    }
}
