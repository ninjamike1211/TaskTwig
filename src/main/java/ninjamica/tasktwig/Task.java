package ninjamica.tasktwig;

import com.fasterxml.jackson.annotation.*;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@JsonIncludeProperties({"name", "interval", "lastDone", "dueTime"})
public record Task (StringProperty name, ObjectProperty<TwigInterval> interval, ObjectProperty<LocalDate> lastDone, ObjectProperty<LocalTime> dueTime) {
    public static final int VERSION = 3;

    @JsonCreator
    public Task (
            @JsonProperty("name") String name,
            @JsonProperty("dueTime") LocalTime dueTime,
            @JsonProperty("interval") TwigInterval interval,
            @JsonProperty("lastDone") LocalDate lastDone)
    {
        this(new SimpleStringProperty(name),
             new SimpleObjectProperty<>(interval),
             new SimpleObjectProperty<>(lastDone),
             new SimpleObjectProperty<>(dueTime));
    }

    public Task(String name, LocalTime dueTime, TwigInterval interval) {
        this(name, dueTime, interval, null);
    }

    public Task(TaskTwig.TwigJsonNode twigNode) {
        JsonNode node = twigNode.node();
        String name;
        TwigInterval interval;
        LocalDate lastDone;
        LocalTime dueTime;

        if (twigNode.version() == 3) {
            name = node.get("name").asString();
            interval = TwigInterval.parseFromJson(node.get("interval"));

            JsonNode startNode = node.get("lastDone");
            lastDone = startNode.isNull() ? null : LocalDate.parse(startNode.asString());

            JsonNode endNode = node.get("dueTime");
            dueTime = endNode.isNull() ? null : LocalTime.parse(endNode.asString());
        }
        else {
            throw new TaskTwig.JsonVersionException("Unsupported Task version: " + twigNode.version());
        }

        this(name, dueTime, interval, lastDone);
    }

    @JsonGetter("name")
    public String getName() {
        return this.name.get();
    }

    @JsonGetter("interval")
    public TwigInterval getInterval() {
        return this.interval.get();
    }

    @JsonGetter("lastDone")
    public LocalDate getLastDone() {
        return this.lastDone.get();
    }

    @JsonGetter("dueTime")
    public LocalTime getDueTime() {
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
            
            if (!journalTasks.contains(this.getName()))
                journalTasks.add(this.getName());
        }
        else {
            this.lastDone.set(null);
            journalTasks.remove(this.getName());
        }
    }
}