package ninjamica.tasktwig;

import com.fasterxml.jackson.annotation.*;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import tools.jackson.databind.JsonNode;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TwigInterval.DailyInterval.class, name = "daily"),
        @JsonSubTypes.Type(value = TwigInterval.WeeklyInterval.class, name = "weekly"),
        @JsonSubTypes.Type(value = TwigInterval.MonthlyInterval.class, name = "monthly"),
        @JsonSubTypes.Type(value = TwigInterval.SingleDayInterval.class, name = "singleDay"),
        @JsonSubTypes.Type(value = TwigInterval.NoInterval.class, name = "none")
})
public interface TwigInterval {

    /**
     * @return the next LocalDate in the interval, including today
     */
    LocalDate next();

    /**
     * @return the next LocalDate in the interval after today
     */
    LocalDate nextAfter();

    /**
     * @return the LocalDate of the previous interval iteration to this one
     */
    LocalDate previous();

    /**
     *
     * @return whether the interval includes today
     */
    boolean isToday();

    static TwigInterval parseFromJson(JsonNode node) {
        switch (node.get("@type").asString()) {
            case "daily" -> {
                return new DailyInterval();
            }
            case "weekly" -> {
                boolean[] daysOfWeek = new boolean[7];

                int i = 0;
                for (JsonNode day : node.get("daysOfWeek")) {
                    daysOfWeek[i++] = day.asBoolean();
                }
                return new WeeklyInterval(daysOfWeek);
            }
            case "monthly" -> {
                List<Integer> days = new ArrayList<>();

                for (JsonNode day : node.get("dueDays")) {
                    days.add(day.asInt());
                }
                return new MonthlyInterval(days);
            }
            case "singleDay" -> {
                return new SingleDayInterval(LocalDate.parse(node.get("date").asString()));
            }
            case "none" -> {
                return new NoInterval();
            }
            default -> {
                return null;
            }
        }
    }


    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
    @JsonIncludeProperties()
    class DailyInterval implements TwigInterval {

        public LocalDate next() {
            return TaskTwig.effectiveDate();
        }

        public LocalDate nextAfter() {
            return TaskTwig.effectiveDate().plusDays(1);
        }

        public LocalDate previous() {
            return TaskTwig.effectiveDate().minusDays(1);
        }

        public boolean isToday() {
            return true;
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
    @JsonIncludeProperties({"daysOfWeek"})
    class WeeklyInterval implements TwigInterval {

        private final BooleanProperty[] dayOfWeekMap =  new BooleanProperty[7];

        public WeeklyInterval(DayOfWeek... daysOfWeek) {
            for (DayOfWeek day :  daysOfWeek) {
                dayOfWeekMap[day.ordinal()] = new SimpleBooleanProperty(true);
            }

            for (int i = 0; i < dayOfWeekMap.length; i++) {
                if (dayOfWeekMap[i] == null) {
                    dayOfWeekMap[i] = new SimpleBooleanProperty(false);
                }
            }
        }

        public WeeklyInterval(List<DayOfWeek> daysOfWeek) {
            this(daysOfWeek.toArray(DayOfWeek[]::new));
        }

        @JsonCreator
        public WeeklyInterval(@JsonProperty("daysOfWeek") boolean[] daysOfWeekMap) {
            for (int i = 0; i < daysOfWeekMap.length; i++) {
                this.dayOfWeekMap[i] = new SimpleBooleanProperty(daysOfWeekMap[i]);
            }
        }

        public LocalDate next() {
            int today = TaskTwig.effectiveDate().getDayOfWeek().ordinal();

            for (int daysPlus = 0; daysPlus < 7; daysPlus++) {
                int index = (today + daysPlus) % 7;

                if (this.dayOfWeekMap[index].get()) {
                    return TaskTwig.effectiveDate().plusDays(daysPlus);
                }
            }

            return null;
        }

        public LocalDate nextAfter() {
            int today = TaskTwig.effectiveDate().getDayOfWeek().ordinal();

            for (int daysPlus = 1; daysPlus < 8; daysPlus++) {
                int index = (today + daysPlus) % 7;

                if (this.dayOfWeekMap[index].get()) {
                    return TaskTwig.effectiveDate().plusDays(daysPlus);
                }
            }

            return null;
        }

        public LocalDate previous() {
            int today = TaskTwig.effectiveDate().getDayOfWeek().ordinal();

            for (int daysMinus = 1; daysMinus < 8; daysMinus++) {
                int index = (today - daysMinus) % 7;

                if (this.dayOfWeekMap[index].get()) {
                    return TaskTwig.effectiveDate().minusDays(daysMinus);
                }
            }

            return null;
        }

        public boolean isToday() {
            int today =  TaskTwig.effectiveDate().getDayOfWeek().ordinal();
            return dayOfWeekMap[today].get();
        }

        @JsonGetter("daysOfWeek")
        public boolean[]  getDayOfWeekMap() {
            boolean[] result = new boolean[7];
            for (int i = 0; i < dayOfWeekMap.length; i++) {
                result[i] = dayOfWeekMap[i].get();
            }
            return result;
        }

        public BooleanProperty dayOfWeekProperty(int dayIndex) {
            return this.dayOfWeekMap[dayIndex];
        }

        public BooleanProperty[] dayOfWeekMapProperty() {
            return this.dayOfWeekMap;
        }

        public List<DayOfWeek> daysOfWeek() {
            List<DayOfWeek> days = new ArrayList<>();
            for (int i = 0; i < dayOfWeekMap.length; i++) {
                if (dayOfWeekMap[i].get()) {
                    days.add(DayOfWeek.of(i+1));
                }
            }
            return days;
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
    @JsonIncludeProperties({"dueDays"})
    class MonthlyInterval implements TwigInterval {

        private final ObservableList<Integer> dueDays = FXCollections.observableArrayList();

        @JsonCreator
        public MonthlyInterval(@JsonProperty("dueDays") List<Integer> dueDays) {
            this.dueDays.addAll(dueDays);
        }

        public MonthlyInterval(Integer... dueDays) {
            this.dueDays.addAll(dueDays);
        }

        @Override
        public LocalDate next() {
            int today = TaskTwig.effectiveDate().getDayOfMonth();
            int maxDay = TaskTwig.effectiveDate().lengthOfMonth();

            for (int day : this.dueDays) {
                if (day >= today) {
                    if (day > maxDay)
                        return TaskTwig.effectiveDate().withDayOfMonth(maxDay);
                    else
                        return  TaskTwig.effectiveDate().withDayOfMonth(day);
                }
            }

            return TaskTwig.effectiveDate().withDayOfMonth(this.dueDays.getFirst()).plusMonths(1);
        }

        @Override
        public LocalDate nextAfter() {
            int today = TaskTwig.effectiveDate().getDayOfMonth();
            int maxDay = TaskTwig.effectiveDate().lengthOfMonth();

            for (int day : this.dueDays) {
                if (day > today) {
                    if (day > maxDay)
                        return TaskTwig.effectiveDate().withDayOfMonth(maxDay);
                    else
                        return  TaskTwig.effectiveDate().withDayOfMonth(day);
                }
            }

            return TaskTwig.effectiveDate().withDayOfMonth(this.dueDays.getFirst()).plusMonths(1);
        }

        @Override
        public LocalDate previous() {
            int today = TaskTwig.effectiveDate().getDayOfMonth();
            int maxDay = TaskTwig.effectiveDate().lengthOfMonth();

            for (int day : this.dueDays) {
                if (day >= today) {
                    if(day > maxDay) {
                        return TaskTwig.effectiveDate().withDayOfMonth(maxDay);
                    }
                    else {
                        return TaskTwig.effectiveDate().withDayOfMonth(day);
                    }
                }
            }

            int nextMaxDay = TaskTwig.effectiveDate().plusMonths(1).lengthOfMonth();
            if (this.dueDays.getFirst() > nextMaxDay) {
                return TaskTwig.effectiveDate().plusMonths(1).withDayOfMonth(maxDay);
            }
            else {
                return TaskTwig.effectiveDate().plusMonths(1).withDayOfMonth(this.dueDays.getFirst());
            }
        }

        @Override
        public boolean isToday() {
            return TaskTwig.effectiveDate().equals(this.next());
        }

        @JsonGetter("dueDays")
        public ObservableList<Integer> getDueDays() {
            return dueDays;
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
    @JsonIncludeProperties({"date"})
    class SingleDayInterval implements TwigInterval {

        private final ObjectProperty<LocalDate> date = new SimpleObjectProperty<>();

        @JsonCreator
        public SingleDayInterval(@JsonProperty("date") LocalDate date) {
            this.date.set(date);
        }

        @Override
        public LocalDate next() {
            if (date.get().toEpochDay() >= TaskTwig.effectiveDate().toEpochDay()) {
                return date.get();
            }
            else {
                return null;
            }
        }

        @Override
        public LocalDate nextAfter() {
            if (date.get().isAfter(TaskTwig.effectiveDate())) {
                return date.get();
            }
            else {
                return null;
            }
        }

        @Override
        public LocalDate previous() {
            if (date.get().isBefore(TaskTwig.effectiveDate())) {
                return date.get();
            }
            else {
                return null;
            }
        }

        @Override
        public boolean isToday() {
            return TaskTwig.effectiveDate().equals(date.get());
        }

        @JsonGetter("date")
        public LocalDate date() {
            return date.get();
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
    class NoInterval implements TwigInterval {

        @Override
        public LocalDate next() {
            return null;
        }

        @Override
        public LocalDate nextAfter() {
            return null;
        }

        @Override
        public LocalDate previous() {
            return null;
        }

        @Override
        public boolean isToday() {
            return false;
        }
    }
}
