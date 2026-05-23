package ninjamica.tasktwig.ui.util;

import atlantafx.base.theme.Styles;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.util.Subscription;
import ninjamica.tasktwig.core.Task;
import ninjamica.tasktwig.core.TaskCategory;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Predicate;
import java.util.function.Supplier;

public class TaskCategoryViewBase extends VBox {

    private final FontIcon expandIcon = new FontIcon();
    private final Text catNameText = new Text();
    protected final ListBoxInterface<Task> taskList;

    protected HBox nameBox;

    protected final BooleanProperty expanded = new SimpleBooleanProperty(true);
    private Subscription subs = Subscription.EMPTY;

    public TaskCategoryViewBase(Supplier<ListBoxInterface<Task>> taskBoxConstructor) {
        super(10);

        HBox expandIconPane = new HBox(expandIcon);
        expandIconPane.setPrefWidth(15);
        expandIconPane.setMinWidth(USE_PREF_SIZE);
        expandIconPane.setMaxWidth(USE_PREF_SIZE);
        expandIconPane.setCursor(Cursor.HAND);

        expandIconPane.setOnMouseEntered(event -> expandIcon.setStyle("-fx-icon-color: -color-fg-default;"));
        expandIconPane.setOnMouseExited(event -> expandIcon.setStyle("-fx-icon-color: -color-fg-muted;"));
        expandIcon.setStyle("-fx-icon-color: -color-fg-muted;");

        catNameText.getStyleClass().add(Styles.TITLE_3);

        nameBox = new HBox(10, expandIconPane, catNameText);
        nameBox.setAlignment(Pos.BASELINE_LEFT);

        taskList = taskBoxConstructor.get();
        taskList.setAfterChangeRunnable(this::updateTaskList);

        getChildren().addAll(nameBox, taskList.getNode());

        nameBox.setOnMouseClicked(event -> {
            if (expandIcon.isVisible())
                expanded.set(!expanded.get());
        });
        expanded.subscribe(_ -> updateTaskList());
    }

    public TaskCategoryViewBase(Supplier<ListBoxInterface<Task>> taskBoxConstructor,
                                TaskCategory category, Predicate<Task> filter) {
        this(taskBoxConstructor);
        setCategory(category, filter);
    }

    public void setCategory(TaskCategory category, Predicate<Task> filter) {
        if (category != null) {
            setCategory(category.getTasks(), filter, category.nameProperty(), category.colorProperty());
        }
        else {
            unbind();
        }
    }

    public void setCategory(ObservableList<Task> tasks, Predicate<Task> filter, StringProperty name, ObjectProperty<Color> color) {
        unbind();

        if (tasks != null) {
            ObservableList<Task> filteredTasks = filter != null ? tasks.filtered(filter) : tasks;

            catNameText.textProperty().bind(name);
            catNameText.fillProperty().bind(color);
            taskList.setItems(filteredTasks);

            updateTaskList();

            subs = Subscription.combine(
                    catNameText.textProperty()::unbind,
                    catNameText.fillProperty()::unbind,
                    taskList::unbind
            );
        }
    }

    public void unbind() {
        subs.unsubscribe();
    }

    protected void updateTaskList() {
        if (taskList.getItems() != null) {
            boolean isEmpty = taskList.getItems().isEmpty();
            expandIcon.setVisible(!isEmpty);
            if (isEmpty) {
                getChildren().remove(taskList.getNode());
            }
            else {
                if (expanded.get()) {
                    expandIcon.setIconCode(FontAwesomeSolid.CARET_DOWN);
                    if (!getChildren().contains(taskList.getNode()))
                        getChildren().add(taskList.getNode());
                }
                else {
                    expandIcon.setIconCode(FontAwesomeSolid.CARET_RIGHT);
                    getChildren().remove(taskList.getNode());
                }
            }
        }
    }
}
