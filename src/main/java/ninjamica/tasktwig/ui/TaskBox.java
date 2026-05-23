package ninjamica.tasktwig.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.util.Subscription;
import ninjamica.tasktwig.core.Task;
import ninjamica.tasktwig.core.TaskInterface;
import ninjamica.tasktwig.core.TwigInterval;
import ninjamica.tasktwig.ui.util.DraggableListBox;
import ninjamica.tasktwig.ui.util.TaskBoxBase;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class TaskBox extends TaskBoxBase {

    private final FontIcon newSubTaskButton = FontIcon.of(FontAwesomeSolid.PLUS, 5);
    private final Consumer<Task> onNewSubTask;
    private final BiConsumer<MouseEvent, TaskInterface> taskClickHandler;

    Subscription subs = Subscription.EMPTY;

    public TaskBox(Consumer<Task> onNewSubTask, BiConsumer<MouseEvent, TaskInterface> taskClickHandler) {
        super(() -> new DraggableListBox<>(
                subTask -> new SubTaskBox(subTask, taskClickHandler),
                SubTaskBox::unbind,
                Pos.TOP_LEFT
        ));
        this.onNewSubTask = onNewSubTask;
        this.taskClickHandler = taskClickHandler;
        newSubTaskButton.setCursor(Cursor.HAND);
        newSubTaskButton.setOnMouseEntered(event -> newSubTaskButton.setStyle("-fx-icon-color: -color-fg-default;"));
        newSubTaskButton.setOnMouseExited(event -> newSubTaskButton.setStyle("-fx-icon-color: -color-fg-muted;"));
        newSubTaskButton.setStyle("-fx-icon-color: -color-fg-muted;");
        nameBox.getChildren().add(newSubTaskButton);

        VBox.setMargin(subTaskBox.getNode(), new Insets(0, 0, 0, 20));
    }

    public TaskBox(Task task, Consumer<Task> onNewSubTask, BiConsumer<MouseEvent, TaskInterface> taskClickHandler) {
        this(onNewSubTask, taskClickHandler);
        setTask(task);
    }

    @Override
    public void setTask(Task task) {
        super.setTask(task);

        if (task != null) {
            newSubTaskButton.setOnMousePressed(event -> onNewSubTask.accept(task));
            nameText.setOnMouseClicked(event -> taskClickHandler.accept(event, task));

            updateDoneForever(task.isDone(), task.getInterval());

            subs = Subscription.combine(
                    task.intervalProperty().subscribe(interval -> updateDoneForever(task.isDone(), interval)),
                    task.isDoneObservable().subscribe(done -> updateDoneForever(done, task.getInterval())),
                    () -> nameText.setOnMouseClicked(null)
            );
        }
    }

    @Override
    public void unbind() {
        super.unbind();
        subs.unsubscribe();
    }

    private void updateDoneForever(boolean done, TwigInterval interval) {
        if (done && interval instanceof TwigInterval.NoRepeat) {
            nameText.setStrikethrough(true);
            nameText.setOpacity(0.5);
        }
        else {
            nameText.setStrikethrough(false);
            nameText.setOpacity(1);
        }
    }
}
