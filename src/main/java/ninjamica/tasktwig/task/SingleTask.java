package ninjamica.tasktwig.task;

import com.fasterxml.jackson.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonIncludeProperties({"name", "dueTime", "done", "dueDate"})
public class SingleTask extends Task {
    protected boolean done;
    protected LocalDate dueDate;

    @JsonCreator
    public SingleTask(
            @JsonProperty("name") String name,
            @JsonProperty("done") boolean done,
            @JsonProperty("dueDate") LocalDate dueDate,
            @JsonProperty("dueTime") LocalTime dueTime)
    {
        super(name, dueTime);
        this.done = done;
        this.dueDate = dueDate;
    }

    public SingleTask(String name, LocalDate dueDate, LocalTime dueTime) {
        super(name, dueTime);
        this.dueDate = dueDate;

        this.done = false;
    }

    @JsonGetter("done")
    public boolean isDone() {
        return this.done;
    }

    @JsonGetter("dueDate")
    public LocalDate nextDueDate() {
        return dueDate;
    }

    public Map<LocalDate, Boolean> getDueDates(LocalDate rangeStart, LocalDate rangeEnd) {
        if (this.dueDate == null) {
            return Collections.emptyMap();
        }

        return Map.of(this.dueDate, this.done);
    }

    public void setCompletion(boolean done) {
        this.done = done;
    }
}
