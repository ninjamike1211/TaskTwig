package ninjamica.tasktwig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javafx.beans.property.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@JsonIncludeProperties({"name", "start", "end", "interval", "lastDone"})
public class Routine extends TaskTwig.HasVersion {
    public static final int VERSION = 1;

    private final StringProperty name = new SimpleStringProperty();
    private final ObjectProperty<LocalTime> startTime = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalTime> endTime = new SimpleObjectProperty<>();
    private final ObjectProperty<TwigInterval> interval = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate> lastDone = new SimpleObjectProperty<>();

    public Routine(String name, LocalTime startTime, LocalTime endTime, TwigInterval interval) {
        this(name, startTime, endTime, interval, null);
    }

    @JsonCreator
    public Routine(
        @JsonProperty("name") String name,
        @JsonProperty("start") LocalTime startTime,
        @JsonProperty("end") LocalTime endTime,
        @JsonProperty("interval") TwigInterval interval,
        @JsonProperty("lastDone") LocalDate lastDone)
    {
        this.name.set(name);
        this.startTime.set(startTime);
        this.endTime.set(endTime);
        this.interval.set(interval);
        this.lastDone.set(lastDone);
    }

    public StringProperty getName() {
        return name;
    }

    @JsonGetter("name")
    public String name() {
        return name.get();
    }

    public ObjectProperty<LocalTime> getStartTime() {
        return startTime;
    }

    @JsonGetter("start")
    public LocalTime start() {
        return startTime.get();
    }

    public ObjectProperty<LocalTime> getEndTime() {
        return endTime;
    }

    @JsonGetter("end")
    public LocalTime end() {
        return endTime.get();
    }

    public ObjectProperty<TwigInterval> getInterval() {
        return interval;
    }

    @JsonGetter("interval")
    public TwigInterval interval() {
        return interval.get();
    }

    public boolean isDoneToday() {
        return TaskTwig.effectiveDate().equals(lastDone.get());
    }

    public void setDone(boolean done) {
        List<String> journalRoutines = TaskTwig.instance().todaysJournal().completedRoutines();

        if (done) {
            this.lastDone.set(TaskTwig.effectiveDate());

            if (!journalRoutines.contains(this.name()))
                journalRoutines.add(this.name());
        }
        else {
            this.lastDone.set(null);
            journalRoutines.remove(this.name());
        }
    }

    @JsonGetter("lastDone")
    public LocalDate lastDone() {
        return lastDone.get();
    }
}
