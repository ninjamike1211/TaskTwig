package ninjamica.tasktwig.core;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.IntegerExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.paint.Color;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonIncludeProperties({"name", "paint", "tasks"})
public class TaskCategory {

    private static final Map<String, TaskCategory> categoryMap = new HashMap<>();

    public static TaskCategory getCategoryFromName(String name) {
        return categoryMap.get(name);
    }

    static void clearCategoryMap() {
        categoryMap.clear();
    }

    private final StringProperty name = new SimpleStringProperty();
    private final ObjectProperty<Color> color = new SimpleObjectProperty<>();
    private final ObservableList<Task> tasks = FXCollections.observableArrayList(
            task -> new Observable[] {task.isTodayObservable(), task.isDoneObservable()}
    );
    private final IntegerExpression todayCount;
    private final IntegerExpression doneTodayCount;

    public TaskCategory(String name, Color color) {
        this.name.set(name);
        this.color.set(color);
        categoryMap.put(name, this);

        todayCount = Bindings.createIntegerBinding(
//                () -> (int) (getTasksSafe().stream().filter(Task::isToday).count()),
                () -> {
                    int count = 0;
                    for (Task task : getTasksSafe()) {
                        if (task.isToday() || task.isDoneToday())
                            count++;
                    }
                    return count;
                },
                tasks, TaskTwig.todayObservable()
        );

        doneTodayCount = Bindings.createIntegerBinding(
//                () -> (int) (getTasksSafe().stream().filter(Task::isDoneToday).count()),
                () -> {
                    int count = 0;
                    for (Task task : getTasksSafe()) {
                        if (task.isDoneToday())
                            count++;
                    }
                    return count;
                },
                tasks, TaskTwig.todayObservable()
        );
    }
    public TaskCategory(JsonNode node, int version) {
        String name;
        Color color;
        switch (version) {
            case 9, 10, 11:
                name = node.get("name").asString();
                color = Color.valueOf(node.get("paint").asString());
                break;

            default:
                throw new TaskTwig.TwigJsonVersionException("Unsupported Task version for having categories: " + version);
        }
        this(name, color);

        switch (version) {
            case 11:
                for (JsonNode taskNode : node.get("tasks").asArray()) {
                    tasks.add(new Task(taskNode, version, this));
                }
        }
    }

    public StringProperty nameProperty() {
        return name;
    }

    @JsonGetter("name")
    public String getName() {
        return TaskTwig.supplyWithFXSafety(name::get);
    }

    public void setName(String name) {
        TaskTwig.setWithFXSafety(this.name::set, name);
    }

    public ObjectProperty<Color> colorProperty() {
        return color;
    }

    public Color getColor() {
        return TaskTwig.supplyWithFXSafety(color::get);
    }

    public void setColor(Color color) {
        TaskTwig.setWithFXSafety(this.color::set, color);
    }

    @JsonGetter("paint")
    public String getPaintJson() {
        return getColor().toString();
    }

    public ObservableList<Task> getTasks() {
        return tasks;
    }

    @JsonGetter("tasks")
    public List<Task> getTasksSafe() {
        return TaskTwig.supplyWithFXSafety(() -> new ArrayList<>(tasks));
    }

    public IntegerExpression todayCountProperty() {
        return todayCount;
    }
    public int getTodayCount() {
        return TaskTwig.supplyWithFXSafety(todayCount::get);
    }

    public IntegerExpression doneTodayCountProperty() {
        return doneTodayCount;
    }
    public int getDoneTodayCount() {
        return TaskTwig.supplyWithFXSafety(doneTodayCount::get);
    }

    public void hashContents(MessageDigest digest) {
        digest.update(getName().getBytes(StandardCharsets.UTF_8));
        digest.update(getColor().toString().getBytes(StandardCharsets.UTF_8));
        getTasksSafe().forEach(task -> task.hashContents(digest));
    }
}
