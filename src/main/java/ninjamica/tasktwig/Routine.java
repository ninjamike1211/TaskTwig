package ninjamica.tasktwig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@JsonIncludeProperties({"name", "dueAt", "interval"})
public record Routine(StringProperty name, ObjectProperty<LocalTime> dueTime, ObjectProperty<RoutineInterval> interval) {
    public static final int VERSION = 3;

    @JsonCreator
    public Routine(String name, LocalTime dueTime, RoutineInterval interval) {
        this(new SimpleStringProperty(name),
                new SimpleObjectProperty<>(dueTime),
                new SimpleObjectProperty<>(interval));
    }

    public Routine(JsonNode node, int version) {
        String name;
        LocalTime dueTime;
        RoutineInterval interval;
        LocalDate lastDone;
        switch (version) {
            case 2,3 -> {
                name = node.get("name").asString();

                JsonNode endNode = node.get("dueAt");
                dueTime = endNode.isNull() ? null : LocalTime.parse(endNode.asString());

                interval = RoutineInterval.parseFromJson(node.get("interval"), version);
            }
            case 1 -> {
                name = node.get("name").asString();

                JsonNode endNode = node.get("end");
                dueTime = endNode.isNull() ? null : LocalTime.parse(endNode.asString());

                interval = RoutineInterval.parseFromJson(node.get("interval"), version);
            }
            default -> throw new TaskTwig.JsonVersionException("Unsupported Routine version: " + version);
        }

        this(name, dueTime, interval);
    }

    @JsonGetter("name")
    public String getName() {
        return TaskTwig.callWithFXSafety(name::get);
    }

    @JsonGetter("dueAt")
    public LocalTime getDueTime() {
        return TaskTwig.callWithFXSafety(dueTime::get);
    }

    @JsonGetter("interval")
    public RoutineInterval getInterval() {
        return TaskTwig.callWithFXSafety(interval::get);
    }

    public boolean isDoneToday() {
        return interval.get().isDone();
    }

    public void setDone(boolean done) {
        interval.get().setDone(done);

        List<String> journalRoutines = TaskTwig.instance().todaysJournal().completedRoutines();
        if (done) {
            if (!journalRoutines.contains(this.getName()))
                journalRoutines.add(this.getName());
        }
        else {
            journalRoutines.remove(this.getName());
        }
    }

    public void hashContents(MessageDigest digest) {
        digest.update(getName().getBytes(StandardCharsets.UTF_8));

        LocalTime dueAt = getDueTime();
        if (dueAt != null)
            digest.update(dueAt.toString().getBytes(StandardCharsets.UTF_8));

        getInterval().hashContents(digest);
    }
}
