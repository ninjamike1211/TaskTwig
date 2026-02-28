package ninjamica.tasktwig;

import com.fasterxml.jackson.annotation.*;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@JsonIncludeProperties({"name", "interval", "lastDone", "dueTime"})
public class Task extends TaskTwig.HasVersion {
    public static final int VERSION = 3;

    private final StringProperty name = new SimpleStringProperty();
    private final ObjectProperty<TwigInterval> interval = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate> lastDone = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalTime> dueTime = new SimpleObjectProperty<>();

    @JsonCreator
    public Task (
            @JsonProperty("name") String name,
            @JsonProperty("dueTime") LocalTime dueTime,
            @JsonProperty("interval") TwigInterval interval,
            @JsonProperty("lastDone") LocalDate lastDone)
    {
        this.name.set(name);
        this.interval.set(interval);
        this.lastDone.set(lastDone);
        this.dueTime.set(dueTime);
    }

    public Task(String name, LocalTime dueTime, TwigInterval interval) {
        this(name, dueTime, interval, null);
    }

    public StringProperty nameProperty() {
        return name;
    }

    @JsonGetter("name")
    public String name() {
        return this.name.get();
    }

    public ObjectProperty<TwigInterval> intervalProperty() {
        return interval;
    }

    @JsonGetter("interval")
    public TwigInterval interval() {
        return this.interval.get();
    }

    public ObjectProperty<LocalDate> lastDoneProperty() {
        return lastDone;
    }

    @JsonGetter("lastDone")
    public LocalDate lastDone() {
        return this.lastDone.get();
    }

    public ObjectProperty<LocalTime> dueTimeProperty() {
        return dueTime;
    }

    @JsonGetter("dueTime")
    public LocalTime dueTime() {
        return dueTime.get();
    }

    public boolean isDone() {
        if (this.lastDone.get() == null) {
            return false;
        }
        LocalDate previous = this.interval.get().previous();
        if (previous == null) {
            return true;
        }
        else {
            return this.lastDone.get().isAfter(previous);
        }
    }

    public void setDone(boolean done) {
        List<String> journalTasks = TaskTwig.instance().todaysJournal().completedTasks();

        if (done) {
            this.lastDone.set(TaskTwig.effectiveDate());
            
            if (!journalTasks.contains(this.name()))
                journalTasks.add(this.name());
        }
        else {
            this.lastDone.set(null);
            journalTasks.remove(this.name());
        }
    }
}