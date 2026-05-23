package ninjamica.tasktwig.ui.util;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.layout.HBox;
import javafx.util.Subscription;
import ninjamica.tasktwig.core.SubTask;
import ninjamica.tasktwig.core.Task;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Supplier;

public class TaskBoxBase extends TaskInterfaceBoxBase<Task> {

    private final FontIcon expandIcon = new FontIcon();
    protected final ListBoxInterface<SubTask> subTaskBox;

    protected final BooleanProperty expanded = new SimpleBooleanProperty();
    private Subscription subs = Subscription.EMPTY;

//    public TaskBoxBase(Function<SubTask, T> subTaskConstructor, Consumer<T> subTaskDestructor) {
    public TaskBoxBase(Supplier<ListBoxInterface<SubTask>> subTaskBoxConstructor) {
        setSpacing(5);

        HBox expandIconPane = new HBox(expandIcon);
        expandIconPane.setPrefSize(15, 20);
        expandIconPane.setMinSize(USE_PREF_SIZE, USE_PREF_SIZE);
        expandIconPane.setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);

        expandIconPane.setOnMouseClicked(event -> {
            if (expandIcon.isVisible())
                expanded.set(!expanded.get());
        });
        expandIconPane.setOnMouseEntered(event -> expandIcon.setStyle("-fx-icon-color: -color-fg-default;"));
        expandIconPane.setOnMouseExited(event -> expandIcon.setStyle("-fx-icon-color: -color-fg-muted;"));
        expandIcon.setStyle("-fx-icon-color: -color-fg-muted;");

        subTaskBox = subTaskBoxConstructor.get();
        subTaskBox.setAfterChangeRunnable(this::updateSubtaskBox);

//        getChildren().setAll(new HBox(5, expandIconPane, nameBox));
        nameBox.setSpacing(5);
        nameBox.setAlignment(Pos.CENTER_LEFT);
        nameBox.getChildren().addFirst(expandIconPane);
    }

//    public TaskBoxBase(Function<SubTask, T> subTaskConstructor, Consumer<T> subTaskDestructor, Task task) {
    public TaskBoxBase(Supplier<ListBoxInterface<SubTask>> subTaskBoxConstructor, Task task) {
        this(subTaskBoxConstructor);
        setTask(task);
    }

    public void setTask(Task task) {
        super.setTask(task);

        if (task != null) {

            expanded.bindBidirectional(task.subTasksExpandedProperty());

            subTaskBox.setItems(task.getSubTasks());
            updateSubtaskBox();

            subs = Subscription.combine(
                    () -> expanded.unbindBidirectional(task.subTasksExpandedProperty()),
                    subTaskBox::unbind,
                    expanded.subscribe(_ -> updateSubtaskBox())
            );
        }
    }

    public void unbind() {
        super.unbind();
        subs.unsubscribe();
    }

    protected void updateSubtaskBox() {
        boolean isEmpty = subTaskBox.getItems().isEmpty();
        expandIcon.setVisible(!isEmpty);
        if (isEmpty) {
            getChildren().remove(subTaskBox.getNode());
            expandIcon.setCursor(Cursor.DEFAULT);
        }
        else {
            expandIcon.setCursor(Cursor.HAND);
            if (expanded.get()) {
                expandIcon.setIconCode(FontAwesomeSolid.CARET_DOWN);
                if(!getChildren().contains(subTaskBox.getNode()))
                    getChildren().add(subTaskBox.getNode());
            }
            else {
                expandIcon.setIconCode(FontAwesomeSolid.CARET_RIGHT);
                getChildren().remove(subTaskBox.getNode());
            }
        }
    }
}
