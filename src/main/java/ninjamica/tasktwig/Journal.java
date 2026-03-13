package ninjamica.tasktwig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

@JsonIncludeProperties({"text", "routines", "tasks"})
public record Journal(StringProperty text,
                      ObservableList<String> completedRoutines,
                      ObservableList<String> completedTasks) {
    public static final int VERSION = 2;

    public Journal() {
        this(new SimpleStringProperty(""),
                FXCollections.observableArrayList(),
                FXCollections.observableArrayList());
    }

    @JsonCreator
    public Journal(
        @JsonProperty("text") String text, 
        @JsonProperty("routines") List<String> routines, 
        @JsonProperty("tasks") List<String> tasks)
    {
        this(new SimpleStringProperty(text),
             FXCollections.observableArrayList(routines),
             FXCollections.observableArrayList(tasks));
    }

    public Journal(TaskTwig.TwigJsonNode twigNode) {
        JsonNode node = twigNode.node();
        String text;
        List<String> routines = new ArrayList<>();
        List<String> tasks = new ArrayList<>();

        if (twigNode.version() == 2) {
            text = node.get("text").asString();

            for (JsonNode routineNode : node.get("routines")) {
                routines.add(routineNode.asString());
            }

            for (JsonNode taskNode : node.get("tasks")) {
                tasks.add(taskNode.asString());
            }
        }
        else if(twigNode.version() == 1) {
            text = node.get("text").asString();
        }
        else {
            throw new TaskTwig.JsonVersionException("Unsupported Journal version: " + twigNode.version());
        }

        this(text, routines, tasks);
    }

    public StringProperty textProperty() {
        return text;
    }

    @JsonGetter("text")
    public String getText() {
        return TaskTwig.callWithFXSafety(text::getValue);
    }

    @JsonGetter("tasks")
    public List<String> getTasksJson() {
        if (TaskTwig.useFxThread())
            return TaskTwig.callWithFXSafety(() -> new ArrayList<>(completedTasks));
        else
            return completedTasks;
    }

    @JsonGetter("routines")
    public List<String> getRoutinesJson() {
        if (TaskTwig.useFxThread())
            return TaskTwig.callWithFXSafety(() -> new ArrayList<>(completedRoutines));
        else
            return completedRoutines;
    }

    public void hashContents(MessageDigest digest) {
        digest.update(getText().getBytes(StandardCharsets.UTF_8));

        List<String> tasks = getTasksJson();
        for (String task : tasks) {
            digest.update(task.getBytes(StandardCharsets.UTF_8));
        }

        List<String> routines = getRoutinesJson();
        for (String routine : routines) {
            digest.update(routine.getBytes(StandardCharsets.UTF_8));
        }
    }

}
