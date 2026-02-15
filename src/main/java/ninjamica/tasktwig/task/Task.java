package ninjamica.tasktwig.task;

import com.fasterxml.jackson.annotation.*;
import ninjamica.tasktwig.TaskTwig;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.*;

@JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type", defaultImpl = SingleTask.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SingleTask.class),
        @JsonSubTypes.Type(value = DailyTask.class),
        @JsonSubTypes.Type(value = WeeklyTask.class),
        @JsonSubTypes.Type(value = MonthlyTask.class)
})
public abstract class Task extends TaskTwig.HasVersion {
    public static final int VERSION = 2;

    protected String name;
    protected LocalTime dueTime;

    public Task(String name, LocalTime dueTime) {
        this.name = name;
        this.dueTime = dueTime;
    }

    @JsonGetter("name")
    public String name() { return this.name; }

    @JsonGetter("dueTime")
    public LocalTime dueTime() { return dueTime; }

    public abstract boolean isDone();
    public abstract LocalDate nextDueDate();
    public abstract Map<LocalDate, Boolean> getDueDates(LocalDate rangeStart, LocalDate rangeEnd);

    public void setName(String name) { this.name = name; }
    public void setDueTime(LocalTime dueTime) { this.dueTime = dueTime; }

    public abstract void setCompletion(boolean done);
}
