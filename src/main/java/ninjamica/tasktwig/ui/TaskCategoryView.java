package ninjamica.tasktwig.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import ninjamica.tasktwig.core.Task;
import ninjamica.tasktwig.core.TaskCategory;
import ninjamica.tasktwig.core.TaskInterface;
import ninjamica.tasktwig.ui.util.DraggableListBox;
import ninjamica.tasktwig.ui.util.TaskCategoryViewBase;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class TaskCategoryView extends TaskCategoryViewBase {

    private final FontIcon newTaskButton = FontIcon.of(FontAwesomeSolid.PLUS, 5);
    private final Consumer<TaskCategory> onNewTask;

    public TaskCategoryView(Consumer<TaskCategory> onNewTask, Consumer<Task> onNewSubTask, BiConsumer<MouseEvent, TaskInterface> taskClickHandler) {
        super(() -> new DraggableListBox<>(
                task -> new TaskBox(task, onNewSubTask, taskClickHandler),
                TaskBox::unbind,
                Pos.TOP_LEFT
        ));
        this.onNewTask = onNewTask;
        nameBox.getChildren().add(newTaskButton);

        newTaskButton.setCursor(Cursor.HAND);
        newTaskButton.setOnMouseEntered(event -> newTaskButton.setStyle("-fx-icon-color: -color-fg-default;"));
        newTaskButton.setOnMouseExited(event -> newTaskButton.setStyle("-fx-icon-color: -color-fg-muted;"));
        newTaskButton.setStyle("-fx-icon-color: -color-fg-muted;");

        VBox.setMargin(taskList.getNode(), new Insets(0, 0, 0, 20));
    }

    public TaskCategoryView(TaskCategory category, Consumer<TaskCategory> onNewTask, Consumer<Task> onNewSubTask, BiConsumer<MouseEvent, TaskInterface> taskClickHandler) {
        this(onNewTask, onNewSubTask, taskClickHandler);
        setCategory(category, null);
    }

    public void setCategory(TaskCategory category, Predicate<Task> filter) {
        super.setCategory(category, filter);
        newTaskButton.setOnMouseClicked(event -> {
            onNewTask.accept(category);
            event.consume();
        });
    }
}
