package ninjamica.tasktwig;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalTime;
import java.util.List;

@JsonIncludeProperties({"name", "interval", "dueTime"})
public record Task (StringProperty name, ObjectProperty<TaskInterval> interval, ObjectProperty<LocalTime> dueTime) {
    public static final int VERSION = 4;

    public Task (String name, TaskInterval interval, LocalTime dueTime)
    {
        this(new SimpleStringProperty(name),
             new SimpleObjectProperty<>(interval),
             new SimpleObjectProperty<>(dueTime));
    }

    public Task(String name, TaskInterval interval) {
        this(name, interval, null);
    }

    public Task(JsonNode node, int version) {
        String name;
        TaskInterval interval;
        LocalTime dueTime;

        switch (version) {
            case 3,4 -> {
                name = node.get("name").asString();
                interval = TaskInterval.parseFromJson(node.get("interval"), version);

                JsonNode endNode = node.get("dueTime");
                dueTime = endNode.isNull() ? null : LocalTime.parse(endNode.asString());
            }
            default -> throw new TaskTwig.JsonVersionException("Unsupported Task version: " + version);
        }

        this(name, interval, dueTime);
    }

    @JsonGetter("name")
    public String getName() {
        return TaskTwig.callWithFXSafety(name::get);
    }

    @JsonGetter("interval")
    public TaskInterval getInterval() {
        return TaskTwig.callWithFXSafety(interval::get);
    }

    @JsonGetter("dueTime")
    public LocalTime getDueTime() {
        return TaskTwig.callWithFXSafety(dueTime::get);
    }

    public boolean isDone() {
        return interval.get().isDone();
    }

    public void setDone(boolean done) {
        interval.get().setDone(done);

        List<String> journalTasks = TaskTwig.instance().todaysJournal().completedTasks();
        if (done) {
            if (!journalTasks.contains(this.getName()))
                journalTasks.add(this.getName());
        }
        else {
            journalTasks.remove(this.getName());
        }
    }

    public void hashContents(MessageDigest digest) {
        digest.update(getName().getBytes(StandardCharsets.UTF_8));
        getInterval().hashContents(digest);

        LocalTime dueTime = getDueTime();
        if (dueTime != null)
            digest.update(getDueTime().toString().getBytes(StandardCharsets.UTF_8));
    }

}