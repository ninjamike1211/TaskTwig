package ninjamica.tasktwig;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import tools.jackson.databind.JsonNode;

@JsonIncludeProperties({"text", "routines", "tasks"})
public record Journal(StringProperty text,
                      @JsonGetter("routines") ObservableList<String> completedRoutines,
                      @JsonGetter("tasks") ObservableList<String> completedTasks) {
    public static final int VERSION = 2;

    public Journal() {
        this(new SimpleStringProperty(),
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
        return text.getValue();
    }

}
