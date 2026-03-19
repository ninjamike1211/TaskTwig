package ninjamica.tasktwig;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import tools.jackson.databind.JsonNode;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TaskInterval.NoInterval.class, name = "none"),
        @JsonSubTypes.Type(value = TaskInterval.SingleDateInterval.class, name = "single"),
        @JsonSubTypes.Type(value = TaskInterval.DayInterval.class, name = "day"),
        @JsonSubTypes.Type(value = TaskInterval.WeekInterval.class, name = "week"),
        @JsonSubTypes.Type(value = TaskInterval.MonthInterval.class, name = "month")
})
public interface TaskInterval {

    /**
     * The next due date of this interval, or null if there isn't one
     * @return next due date or null
     */
    LocalDate nextDue();

    /**
     * Whether the current interval is "in-progress", i.e. whether it should appear in the today tab
     * @return whether interval is in-progress
     */
    boolean inProgress();

    /**
     * Whether the current interval is completed or not, effects whether it is checked off in the UI
     * @return whether this interval iteration is completed
     */
    boolean isDone();

    /**
     * Sets the current interval as completed or not based on value of `done`
     * @param done true if the task should be marked as done, false otherwise
     */
    void setDone(boolean done);

    /**
     * Add contents of interval to a MessageDigest
     * @param digest MessageDigest to add hashable contents to
     */
    void hashContents(MessageDigest digest);

    static TaskInterval parseFromJson(JsonNode node, int version) {
        if (version == 4) {
            switch (node.get("@type").asString()) {
                case "none" -> {
                    return new NoInterval(node.get("done").asBoolean());
                }
                case "single" -> {
                    return new SingleDateInterval(LocalDate.parse(node.get("date").asString()), node.get("done").asBoolean());
                }
                case "day" -> {
                    JsonNode lastDoneNode = node.get("lastDone");
                    LocalDate lastDone = lastDoneNode.isNull() ? null : LocalDate.parse(lastDoneNode.asString());
                    return new DayInterval(node.get("interval").asInt(), node.get("fromLastDone").asBoolean(), lastDone, LocalDate.parse(node.get("nextDue").asString()));
                }
                case "week" -> {
                    JsonNode lastDoneNode = node.get("lastDone");
                    LocalDate lastDone = lastDoneNode.isNull() ? null : LocalDate.parse(lastDoneNode.asString());
                    return new WeekInterval((byte) node.get("bitmap").asInt(), lastDone);
                }
                case "month" -> {
                    List<Integer> days = new ArrayList<>();

                    for (JsonNode day : node.get("dates")) {
                        days.add(day.asInt());
                    }
                    return new MonthInterval(days, LocalDate.parse(node.get("lastDone").asString()));
                }
                default -> {
                    return null;
                }
            }
        }
        else if (version == 3) {
            switch (node.get("@type").asString()) {
                case "daily" -> {
                    return new DayInterval(1, false);
                }
                case "weekly" -> {
                    byte bitmap = 0;

                    int i = 0;
                    for (JsonNode day : node.get("daysOfWeek")) {
                        if (day.asBoolean()) {
                            bitmap |= (byte) (1 << i);
                        }
                    }
                    return new WeekInterval(bitmap, null);
                }
                case "monthly" -> {
                    List<Integer> days = new ArrayList<>();

                    for (JsonNode day : node.get("dueDays")) {
                        days.add(day.asInt());
                    }
                    return new MonthInterval(days);
                }
                case "singleDay" -> {
                    return new SingleDateInterval(LocalDate.parse(node.get("date").asString()));
                }
                case "none" -> {
                    return new NoInterval(false);
                }
                default -> {
                    return null;
                }
            }
        }

        throw new TaskTwig.JsonVersionException("Unsupported TaskInterval version: " + version);
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
    @JsonIncludeProperties({"done"})
    class NoInterval implements TaskInterval {
        private boolean done = false;

        public NoInterval() {}

        public NoInterval(boolean done) {
            this.done = done;
        }

        @Override
        public LocalDate nextDue() {
            return null;
        }

        @Override
        public boolean inProgress() {
            return isDone();
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public void setDone(boolean done) {
            this.done = done;
        }

        @Override
        public void hashContents(MessageDigest digest) {
            digest.update("none".getBytes(StandardCharsets.UTF_8));
        }

        @JsonGetter("done")
        public boolean isDoneJson() {
            return TaskTwig.callWithFXSafety(this::isDone);
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
    @JsonIncludeProperties({"date", "done"})
    class SingleDateInterval implements TaskInterval {
        private final ObjectProperty<LocalDate> dueDate = new SimpleObjectProperty<>();
        private boolean done;

        public SingleDateInterval(LocalDate dueDate) {
            this(dueDate, false);
        }

        public SingleDateInterval(LocalDate dueDate, boolean done) {
            this.dueDate.set(dueDate);
            this.done = done;
        }

        @Override
        public LocalDate nextDue() {
            if (TaskTwig.today().isAfter(dueDate.get()))
                return null;
            else
                return dueDate.get();
        }

        @Override
        public boolean inProgress() {
            return !TaskTwig.today().isAfter(dueDate.get());
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public void setDone(boolean done) {
            this.done = done;
        }

        @Override
        public void hashContents(MessageDigest digest) {
            digest.update(dueDate.toString().getBytes(StandardCharsets.UTF_8));
        }

        public ObjectProperty<LocalDate> dueDateProperty() {
            return dueDate;
        }

        @JsonGetter("date")
        public LocalDate getDueDate() {
            return TaskTwig.callWithFXSafety(dueDate::get);
        }

        @JsonGetter("done")
        public boolean isDoneJson() {
            return TaskTwig.callWithFXSafety(this::isDone);
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
    @JsonIncludeProperties({"interval", "fromLastDone", "lastDone", "nextDue"})
    class DayInterval implements TaskInterval {
        private final IntegerProperty intervalDays = new SimpleIntegerProperty();
        private final BooleanProperty repeatFromLastDone = new SimpleBooleanProperty();
        private LocalDate lastDone;
        private LocalDate nextDue;

        public DayInterval(int intervalDays, boolean repeatFromLastDone) {
            this.intervalDays.set(intervalDays);
            this.repeatFromLastDone.set(repeatFromLastDone);
            this.nextDue = TaskTwig.today().plusDays(intervalDays);
        }

        public DayInterval(int intervalDays, boolean repeatFromLastDone, LocalDate firstDue) {
            this.intervalDays.set(intervalDays);
            this.repeatFromLastDone.set(repeatFromLastDone);
            this.nextDue = firstDue;
        }

        public DayInterval(int intervalDays, boolean repeatFromLastDone, LocalDate lastDone, LocalDate nextDue) {
            this.intervalDays.set(intervalDays);
            this.repeatFromLastDone.set(repeatFromLastDone);
            this.lastDone = lastDone;
            this.nextDue = nextDue;

            if (!repeatFromLastDone) {
                while (!TaskTwig.today().isAfter(nextDue))
                    this.nextDue = this.nextDue.plusDays(intervalDays);
            }
        }

        @Override
        public LocalDate nextDue() {
            return nextDue;
        }

        @Override
        public boolean inProgress() {
            return true;
        }

        @Override
        public boolean isDone() {

            if (!repeatFromLastDone.get()) {
                while (!TaskTwig.today().isAfter(nextDue))
                    nextDue = nextDue.plusDays(intervalDays.get());
            }

            if (lastDone == null)
                return false;

            return lastDone.until(nextDue, ChronoUnit.DAYS) <= intervalDays.get();
        }

        @Override
        public void setDone(boolean done) {
            if (done) {
                lastDone = TaskTwig.today();

                if (repeatFromLastDone.get()) {
                    nextDue = lastDone.plusDays(intervalDays.get());
                }
                else {
                    while (!TaskTwig.today().isAfter(nextDue))
                        nextDue = nextDue.plusDays(intervalDays.get());
                }
            }
            else {
                lastDone = null;
            }
        }

        @Override
        public void hashContents(MessageDigest digest) {
            digest.update(ByteBuffer.allocate(4).putInt(intervalDays.get()));
            digest.update((byte) (repeatFromLastDone.get() ? 1 : 0));

            if (lastDone != null)
                digest.update(lastDone.toString().getBytes(StandardCharsets.UTF_8));

            digest.update(nextDue.toString().getBytes(StandardCharsets.UTF_8));
        }

        @JsonGetter("interval")
        public int getInterval() {
            return TaskTwig.callWithFXSafety(intervalDays::get);
        }

        public IntegerProperty intervalProperty() {
            return intervalDays;
        }

        @JsonGetter("fromLastDone")
        public boolean isRepeatFromLastDone() {
            return TaskTwig.callWithFXSafety(repeatFromLastDone::get);
        }

        public BooleanProperty repeatFromLastDoneProperty() {
            return repeatFromLastDone;
        }

        @JsonGetter("lastDone")
        public LocalDate getLastDone() {
            return lastDone;
        }

        @JsonGetter("nextDue")
        public LocalDate getNextDue() {
            return nextDue;
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
    @JsonIncludeProperties({"bitmap", "lastDone"})
    class WeekInterval implements TaskInterval {

        private final ObjectProperty<Byte> dayOfWeekMap = new SimpleObjectProperty<>((byte) 0);
        private LocalDate lastDone;

        public WeekInterval() {}

        public WeekInterval(List<DayOfWeek> days) {
            byte bitmap = 0;
            for (DayOfWeek day : days) {
                bitmap |= (byte) (1 << day.ordinal());
            }
            dayOfWeekMap.set(bitmap);
        }

        public WeekInterval(byte bitmap, LocalDate lastDone) {
            dayOfWeekMap.set(bitmap);
        }

        @Override
        public LocalDate nextDue() {
            LocalDate today = TaskTwig.today();
            int todayIndex = today.getDayOfWeek().ordinal();

            for (int daysPlus = 0; daysPlus < 7; daysPlus++) {
                int index = (todayIndex + daysPlus) % 7;

                if (isDueOn(index)) {
                    return today.plusDays(daysPlus);
                }
            }

            return null;
        }

        @Override
        public boolean inProgress() {
            return true;
        }

        @Override
        public boolean isDone() {
            if (lastDone == null)
                return false;

            return lastDone.isAfter(lastDue());
        }

        @Override
        public void setDone(boolean done) {
            if (done)
                lastDone = TaskTwig.today();
            else
                lastDone = null;
        }

        @Override
        public void hashContents(MessageDigest digest) {
            digest.update(dayOfWeekMap.get());

            if (lastDone != null)
                digest.update(lastDone.toString().getBytes(StandardCharsets.UTF_8));
        }

        public ReadOnlyObjectProperty<Byte> dayOfWeekMapProperty() {
            return dayOfWeekMap;
        }

        public List<DayOfWeek> getDaysOfWeek() {
            List<DayOfWeek> daysOfWeek = new ArrayList<>();

            for (DayOfWeek day : DayOfWeek.values()) {
                if (isDueOn(day))
                    daysOfWeek.add(day);
            }

            return daysOfWeek;
        }

        public boolean isDueOn(DayOfWeek day) {
            return (dayOfWeekMap.get() & (1 << day.ordinal())) != 0;
        }

        private boolean isDueOn(int dayOrdinal) {
            return (dayOfWeekMap.get() & (1 << dayOrdinal)) != 0;
        }

        public void setDueOn(DayOfWeek day, boolean due) {
            if (due) {
                dayOfWeekMap.set((byte) (dayOfWeekMap.get() | (1 << day.ordinal())));
            }
            else {
                dayOfWeekMap.set((byte) (dayOfWeekMap.get() & ~(1 << day.ordinal())));
            }
        }

        @JsonGetter("bitmap")
        public byte getDayOfWeekBitmap() {
            return TaskTwig.callWithFXSafety(dayOfWeekMap::get);
        }

        public LocalDate getLastDone() {
            return lastDone;
        }

        private LocalDate lastDue() {
            LocalDate today = TaskTwig.today();
            int todayIndex = today.getDayOfWeek().ordinal();

            for (int daysMinus = 1; daysMinus < 8; daysMinus++) {
                int index = (todayIndex - daysMinus) % 7;

                if (isDueOn(index)) {
                    return today.plusDays(daysMinus);
                }
            }

            return null;
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
    @JsonIncludeProperties({"dates", "lastDone"})
    class MonthInterval implements TaskInterval {
        private final ObservableList<Integer> dates = FXCollections.observableArrayList();
        private LocalDate lastDone;

        public MonthInterval() {}

        public MonthInterval(List<Integer> dates) {
            this(dates, null);
        }

        public MonthInterval(List<Integer> dates, LocalDate lastDone) {
            this.dates.setAll(dates);
            this.dates.sort(Comparator.naturalOrder());
            this.dates.addListener((ListChangeListener<Integer>) c -> {
                this.dates.sort(Comparator.naturalOrder());
            });

            this.lastDone = lastDone;
        }

        @Override
        public LocalDate nextDue() {
            LocalDate today = TaskTwig.today();
            int todayDate = today.getDayOfMonth();
            int maxDate = today.lengthOfMonth();

            for (int date : this.dates) {
                if (date >= todayDate) {
                    return today.withDayOfMonth(Math.min(date, maxDate));
                }
            }

            return today.withDayOfMonth(Math.min(this.dates.getFirst(), maxDate)).plusMonths(1);
        }

        @Override
        public boolean inProgress() {
            return true;
        }

        @Override
        public boolean isDone() {
            if (lastDone == null)
                return false;

            return lastDone.isAfter(lastDue());
        }

        @Override
        public void setDone(boolean done) {
            if (done)
                lastDone = TaskTwig.today();
            else
                lastDone = null;
        }

        @Override
        public void hashContents(MessageDigest digest) {
            for (int date : this.dates) {
                digest.update((byte) date);
            }

            if (lastDone != null)
                digest.update(lastDone.toString().getBytes(StandardCharsets.UTF_8));
        }

        @JsonGetter("dates")
        public List<Integer> getDates() {
            return TaskTwig.callWithFXSafety(() -> new ArrayList<>(dates));
        }

        public ObservableList<Integer> getDatesObservable() {
            return dates;
        }

        @JsonGetter("lastDone")
        public LocalDate getLastDone() {
            return lastDone;
        }

        private LocalDate lastDue() {
            LocalDate today = TaskTwig.today();
            int todayDate = today.getDayOfMonth();

            if (todayDate <= dates.getFirst()) {
                LocalDate lastMonth = today.minusMonths(1);
                int maxDate = lastMonth.lengthOfMonth();
                return lastMonth.withDayOfMonth(Math.min(dates.getLast(), maxDate));
            }

            int maxDate = today.lengthOfMonth();
            for (int i = 1; i < dates.size()-1; i++) {
                int day =  dates.get(i);
                if (day >= todayDate) {
                    return today.withDayOfMonth(Math.min(dates.get(i-1), maxDate));
                }
            }

            return today.withDayOfMonth(Math.min(dates.getLast(), maxDate));
        }
    }
}


