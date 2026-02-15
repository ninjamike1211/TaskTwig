package ninjamica.tasktwig.task;

import com.fasterxml.jackson.annotation.*;
import ninjamica.tasktwig.TaskTwig;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonIncludeProperties({"name", "dueTime", "lastCompleted", "daysOfWeek"})
public class WeeklyTask extends Task {

    protected DayOfWeek[] daysOfWeek;
    protected LocalDate lastCompleted;

    @JsonIgnore
    protected boolean[] dayOfWeekMap;

    @JsonCreator
    public WeeklyTask(
            @JsonProperty("name") String name,
            @JsonProperty("dueTime") LocalTime dueTime,
            @JsonProperty("daysOfWeek") DayOfWeek[] daysOfWeek,
            @JsonProperty("lastCompleted") LocalDate lastCompleted)
    {
        super(name, dueTime);
        this.daysOfWeek = daysOfWeek;
        this.lastCompleted = lastCompleted;
        generateDayMap();
    }

    public WeeklyTask(String name, DayOfWeek[] daysOfWeek, LocalTime dueTime) {
        super(name, dueTime);
        this.daysOfWeek = daysOfWeek;
        this.lastCompleted = null;
        generateDayMap();
    }

    private void generateDayMap() {
        this.dayOfWeekMap = new boolean[7];

        for (DayOfWeek dayOfWeek : this.daysOfWeek) {
            this.dayOfWeekMap[dayOfWeek.getValue()-1] = true;
        }
    }

    @Override
    public boolean isDone() {
        return TaskTwig.effectiveDate().equals(lastCompleted);
    }

    @Override
    public LocalDate nextDueDate() {
        if (this.isDone()) {
            return this.lastCompleted;
        }

        int today = TaskTwig.effectiveDate().getDayOfWeek().getValue() - 1;
        for (int daysPlus = 0; daysPlus < 7; daysPlus++) {
            int index = (today + daysPlus) % 7;

            if (this.dayOfWeekMap[index]) {
                return TaskTwig.effectiveDate().plusDays(daysPlus);
            }
        }

        return null;
    }

    @Override
    public Map<LocalDate, Boolean> getDueDates(LocalDate rangeStart, LocalDate rangeEnd) {

        LocalDate today = TaskTwig.effectiveDate();
        Map<LocalDate, Boolean> dates = new HashMap<>();

        for (LocalDate date = rangeStart; date.isBefore(rangeEnd.plusDays(1)); date = date.plusDays(1)) {
            if (this.dayOfWeekMap[date.getDayOfWeek().getValue()-1]) {
                if (date.isBefore(today)) {
                    dates.put(date, true);
                } else if (date.isAfter(today)) {
                    dates.put(date, false);
                } else {
                    dates.put(date, this.isDone());
                }
            }
        }

        return dates;
    }

    @JsonGetter("daysOfWeek")
    public DayOfWeek[] getDaysOfWeek() {
        return Arrays.copyOf(this.daysOfWeek, this.daysOfWeek.length);
    }

    public boolean[] getDayOfWeekMap() {
        return this.dayOfWeekMap.clone();
    }

    @JsonGetter("lastCompleted")
    public LocalDate getLastCompleted() {
        return this.lastCompleted;
    }

    public void setCompletion(boolean done) {
        if (done) {
            this.lastCompleted = TaskTwig.effectiveDate();;
        }
        else {
            this.lastCompleted = null;
        }
    }
}
