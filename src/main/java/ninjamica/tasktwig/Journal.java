package ninjamica.tasktwig;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

@JsonIncludeProperties({"text", "routines", "tasks"})
public class Journal extends TaskTwig.HasVersion{
    public static final int VERSION = 2;

    private final StringProperty text = new SimpleStringProperty();
    private final ObservableList<String> completedRoutines = FXCollections.observableArrayList();
    private final ObservableList<String> completedTasks = FXCollections.observableArrayList();

    public Journal() {}

    @JsonCreator
    public Journal(
        @JsonProperty("text") String text, 
        @JsonProperty("routines") List<String> routines, 
        @JsonProperty("tasks") List<String> tasks)
    {
        this.text.setValue(text);
        completedRoutines.addAll(routines);
        completedTasks.addAll(tasks);
    }

    public StringProperty textProperty() {
        return text;
    }

    @JsonGetter("text")
    public String getText() {
        return text.getValue();
    }
    
    @JsonGetter("routines")
    public ObservableList<String> completedRoutines() {
        return this.completedRoutines;
    }
    
    @JsonGetter("tasks")
    public ObservableList<String> completedTasks() {
        return this.completedTasks;
    }
}
