package ninjamica.tasktwig.task;

import com.fasterxml.jackson.annotation.*;
import ninjamica.tasktwig.TaskTwig;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonIncludeProperties({"name", "dueTime", "lastCompleted", "dueDays"})
public class MonthlyTask extends Task {
    protected int[] dueDays;
    protected LocalDate lastCompleted;

    @JsonCreator
    public MonthlyTask(
            @JsonProperty("name") String name,
            @JsonProperty("dueTime") LocalTime dueTime,
            @JsonProperty("lastCompleted") LocalDate lastCompleted,
            @JsonProperty("dueDays") int[] days)
    {
        super(name, dueTime);
        this.lastCompleted = lastCompleted;
        this.dueDays = days;
    }

    public MonthlyTask(String name, int[] days, LocalTime dueTime) {
        super(name, dueTime);
        this.lastCompleted = null;
        this.dueDays = days;
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

        int today = TaskTwig.effectiveDate().getDayOfMonth();
        for (int day : dueDays) {
            if (day >= today) {
                return TaskTwig.effectiveDate().withDayOfMonth(day);
            }
        }

        return TaskTwig.effectiveDate().plusMonths(1).withDayOfMonth(this.dueDays[0]);
    }

    @Override
    public Map<LocalDate, Boolean> getDueDates(LocalDate rangeStart, LocalDate rangeEnd) {

        LocalDate today = TaskTwig.effectiveDate();
        Map<LocalDate, Boolean> dates = new HashMap<>();

        int dayIndex = 0;
        for (int index = 1; index < this.dueDays.length;  index++) {
            if (this.dueDays[index] >= rangeStart.getDayOfMonth()) {
                dayIndex = index;
                break;
            }
        }

        for (LocalDate date = rangeStart; date.isBefore(rangeEnd.plusDays(1)); date = date.plusDays(1)) {
            if (date.getDayOfMonth() == this.dueDays[dayIndex]) {
                if (date.isBefore(today)) {
                    dates.put(date, true);
                } else if (date.isAfter(today)) {
                    dates.put(date, false);
                } else {
                    dates.put(date, this.isDone());
                }

                dayIndex = (dayIndex + 1) % this.dueDays.length;
            }
        }

        return dates;
    }

    @JsonGetter("dueDays")
    public int[] getDueDays() {
        return this.dueDays;
    }

    @JsonGetter("lastCompleted")
    public LocalDate  getLastCompleted() {
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
