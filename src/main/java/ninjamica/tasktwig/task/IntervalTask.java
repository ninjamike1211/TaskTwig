package ninjamica.tasktwig.task;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonIncludeProperties({"name", "dueTime", "lastCompleted", "interval", "fromCompletionDate"})
public class IntervalTask extends Task {

    private Period interval;
    private boolean fromCompletionDate;
    private LocalDate lastCompleted;

    @JsonCreator
    public IntervalTask(
            @JsonProperty("name") String name,
            @JsonProperty("dueTime") LocalTime dueTime,
            @JsonProperty("interval") Period interval,
            @JsonProperty("fromCompletionDate")  boolean fromCompletionDate,
            @JsonProperty("lastCompleted") LocalDate lastCompleted)
    {
        super(name, dueTime);
        this.interval = interval;
        this.fromCompletionDate = fromCompletionDate;
        this.lastCompleted = lastCompleted;
    }

    public IntervalTask(String name, Period interval, LocalTime dueTime) {
        super(name, dueTime);
        this.interval = interval;
        this.lastCompleted = null;
    }

    @Override
    public boolean isDone() {
        return LocalDate.now().equals(this.lastCompleted);
    }

    @Override
    public LocalDate nextDueDate() {
        return null;
    }

    @Override
    public Map<LocalDate, Boolean> getDueDates(LocalDate rangeStart, LocalDate rangeEnd) {
        return Map.of();
    }

    @Override
    public void setCompletion(boolean done) {

    }
}
