package ninjamica.tasktwig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javafx.beans.property.*;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@JsonIncludeProperties({"name", "start", "end", "interval", "lastDone"})
public record Routine(StringProperty name, ObjectProperty<LocalTime> startTime, ObjectProperty<LocalTime> endTime, ObjectProperty<TwigInterval> interval, ObjectProperty<LocalDate> lastDone) {
    public static final int VERSION = 1;

    @JsonCreator
    public Routine(
        @JsonProperty("name") String name,
        @JsonProperty("start") LocalTime startTime,
        @JsonProperty("end") LocalTime endTime,
        @JsonProperty("interval") TwigInterval interval,
        @JsonProperty("lastDone") LocalDate lastDone)
    {
        this(new SimpleStringProperty(name),
                new SimpleObjectProperty<>(startTime),
                new SimpleObjectProperty<>(endTime),
                new SimpleObjectProperty<>(interval),
                new SimpleObjectProperty<>(lastDone));
    }

    public Routine(String name, LocalTime startTime, LocalTime endTime, TwigInterval interval) {
        this(name, startTime, endTime, interval, null);
    }

    public Routine(TaskTwig.TwigJsonNode twigNode) {
        JsonNode node = twigNode.node();
        String name = null;
        LocalTime start = null, end = null;
        TwigInterval interval = null;
        LocalDate lastDone = null;

        if (twigNode.version() == 1) {
            name = node.get("name").asString();

            JsonNode startNode = node.get("start");
            start = startNode.isNull() ? null : LocalTime.parse(startNode.asString());

            JsonNode endNode = node.get("end");
            end = endNode.isNull() ? null : LocalTime.parse(endNode.asString());

            interval = TwigInterval.parseFromJson(node.get("interval"));

            JsonNode lastDoneNode = node.get("lastDone");
            lastDone = lastDoneNode.isNull() ? null : LocalDate.parse(lastDoneNode.asString());
        }
        else {
            throw new TaskTwig.JsonVersionException("Unsupported Routine version: " + twigNode.version());
        }

        this(name, start, end, interval, lastDone);
    }

    @JsonGetter("name")
    public String getName() {
        return name.get();
    }

    @JsonGetter("start")
    public LocalTime getStart() {
        return startTime.get();
    }

    @JsonGetter("end")
    public LocalTime getEnd() {
        return endTime.get();
    }

    @JsonGetter("interval")
    public TwigInterval getInterval() {
        return interval.get();
    }

    @JsonGetter("lastDone")
    public LocalDate getLastDone() {
        return lastDone.get();
    }

    public boolean isDoneToday() {
        return TaskTwig.effectiveDate().equals(lastDone.get());
    }

    public void setDone(boolean done) {
        List<String> journalRoutines = TaskTwig.instance().todaysJournal().completedRoutines();

        if (done) {
            this.lastDone.set(TaskTwig.effectiveDate());

            if (!journalRoutines.contains(this.getName()))
                journalRoutines.add(this.getName());
        }
        else {
            this.lastDone.set(null);
            journalRoutines.remove(this.getName());
        }
    }
}
