package ninjamica.tasktwig.task;

import com.fasterxml.jackson.annotation.*;
import ninjamica.tasktwig.TaskTwig;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonIncludeProperties({"name", "dueTime", "lastCompleted"})
public class DailyTask extends Task {

    protected LocalDate lastCompleted;

    @JsonCreator
    public DailyTask(
            @JsonProperty("name") String name,
            @JsonProperty("dueTime") LocalTime dueTime,
            @JsonProperty("lastCompleted") LocalDate lastCompleted)
    {
        super(name, dueTime);
        this.lastCompleted = lastCompleted;
    }

    public DailyTask(String name, LocalTime dueTime) {
        super(name, dueTime);
        this.lastCompleted = null;
    }

    @Override
    public boolean isDone() {
        return TaskTwig.effectiveDate().equals(lastCompleted);
    }

    @Override
    public LocalDate nextDueDate() {
        return TaskTwig.effectiveDate();
    }

    @Override
    public Map<LocalDate, Boolean> getDueDates(LocalDate rangeStart, LocalDate rangeEnd) {

        LocalDate today = TaskTwig.effectiveDate();
        Map<LocalDate, Boolean> dates = new HashMap<>();

        for (LocalDate date = rangeStart; date.isBefore(rangeEnd.plusDays(1)); date = date.plusDays(1)) {
            if (date.isBefore(today)) {
                dates.put(date, true);
            }
            else if (date.isAfter(today)) {
                dates.put(date, false);
            }
            else {
                dates.put(date, this.isDone());
            }
        }

        return dates;
    }

    @JsonGetter("lastCompleted")
    public LocalDate getLastCompleted() {
        return this.lastCompleted;
    }

    public void setCompletion(boolean done) {
        if (done) {
            this.lastCompleted = TaskTwig.effectiveDate();
        }
        else {
            this.lastCompleted = null;
        }
    }
}
