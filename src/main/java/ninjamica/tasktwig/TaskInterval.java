package ninjamica.tasktwig;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.*;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableObjectValue;
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
import java.util.Collections;
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
@JsonIncludeProperties({"lastDone"})
public abstract class TaskInterval {

    protected final ObjectProperty<LocalDate> lastDoneProperty = new SimpleObjectProperty<>();
    protected BooleanExpression doneBinding;
    protected BooleanExpression inProgressBinding;
    protected ObjectExpression<LocalDate> nextDueBinding;

    protected TaskInterval(LocalDate lastDone) {
        this.lastDoneProperty.set(lastDone);
    }

    @JsonGetter("lastDone")
    public final LocalDate getLastDone() {
        return TaskTwig.callWithFXSafety(lastDoneProperty::get);
    }

    /**
     * An observable value that updates with the interval's current done status
     * @return represents interval's current done status
     */
    public final ObservableBooleanValue doneObservable() {
        return doneBinding;
    }

    /**
     * Whether the current interval is completed or not, effects whether it is checked off in the UI
     * @return whether this interval iteration is completed
     */
    public final boolean isDone() {
        return doneBinding.get();
    }

    public final void hashContents(MessageDigest digest) {
        hashContentsHelper(digest);
        LocalDate lastDone = getLastDone();
        if (lastDone != null) {
            digest.update(lastDone.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    public final ObservableObjectValue<LocalDate> nextDueObservable() {
        return nextDueBinding;
    }

    /**
     * The next due date of this interval, or null if there isn't one
     * @return next due date or null
     */
    public final LocalDate nextDue() {
        return nextDueBinding.get();
    }

    public final ObservableBooleanValue inProgressObservable() {
        return inProgressBinding;
    }

    /**
     * Whether the current interval is "in-progress", i.e. whether it should appear in the today tab
     * @return whether interval is in-progress
     */
    public final boolean inProgress() {
        return inProgressBinding.get();
    }

    /**
     * Whether the current interval is not completed and its due date (if relevant) has already passed, e.g. overdue
     * @return whether interval is overdue
     */
    public abstract boolean isOverdue();

    /**
     * Sets the current interval as completed or not based on value of `done`
     * @param done true if the task should be marked as done, false otherwise
     */
    public abstract void setDone(boolean done);

    /**
     * Add contents of interval to a MessageDigest
     * @param digest MessageDigest to add hashable contents to
     */
    protected abstract void hashContentsHelper(MessageDigest digest);

    static TaskInterval parseFromJson(JsonNode node, int version) {
        switch (version) {
            case 5, 6 -> {
                JsonNode lastDoneNode = node.get("lastDone");
                LocalDate lastDone = lastDoneNode.isNull() ? null : LocalDate.parse(lastDoneNode.asString());
                switch (node.get("@type").asString()) {
                    case "none" -> {
                        return new NoInterval(lastDone);
                    }
                    case "single" -> {
                        return new SingleDateInterval(LocalDate.parse(node.get("date").asString()), lastDone);
                    }
                    case "day" -> {
                        return new DayInterval(node.get("interval").asInt(), node.get("fromLastDone").asBoolean(), lastDone, LocalDate.parse(node.get("nextDue").asString()));
                    }
                    case "week" -> {
                        return new WeekInterval((byte) node.get("bitmap").asInt(), lastDone);
                    }
                    case "month" -> {
                        List<Integer> days = new ArrayList<>();

                        for (JsonNode day : node.get("dates")) {
                            days.add(day.asInt());
                        }
                        return new MonthInterval(days, lastDone);
                    }
                    default -> {
                        return null;
                    }
                }
            }
            case 4 -> {
                switch (node.get("@type").asString()) {
                    case "none" -> {
                        return new NoInterval(node.get("done").asBoolean() ? TaskTwig.today() : null);
                    }
                    case "single" -> {
                        return new SingleDateInterval(LocalDate.parse(node.get("date").asString()), node.get("done").asBoolean() ? TaskTwig.today() : null);
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
            case 3 -> {
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
                        return new NoInterval(null);
                    }
                    default -> {
                        return null;
                    }
                }
            }
            default -> throw new TaskTwig.JsonVersionException("Unsupported TaskInterval version: " + version);
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
    @JsonIncludeProperties({"lastDone"})
    public static class NoInterval extends TaskInterval {

        public NoInterval() {
            this(null);
        }

        public NoInterval(LocalDate lastDone) {
            super(lastDone);
            doneBinding = lastDoneProperty.isNotNull();
            nextDueBinding = ObjectExpression.objectExpression(new SimpleObjectProperty<>(null));
            inProgressBinding = doneBinding.not().or(TaskTwig.todayValue().isEqualTo(lastDoneProperty));
        }

        @Override
        public boolean isOverdue() {
            return false;
        }

        @Override
        public void setDone(boolean done) {
            lastDoneProperty.set(done ? TaskTwig.today() : null);
        }

        @Override
        protected void hashContentsHelper(MessageDigest digest) {
            digest.update("none".getBytes(StandardCharsets.UTF_8));
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
    @JsonIncludeProperties({"date", "lastDone"})
    public static class SingleDateInterval extends TaskInterval {
        private final ObjectProperty<LocalDate> dueDate = new SimpleObjectProperty<>();

        public SingleDateInterval(LocalDate dueDate) {
            this(dueDate, null);
        }

        public SingleDateInterval(LocalDate dueDate, LocalDate lastDone) {
            super(lastDone);
            this.dueDate.set(dueDate);
            doneBinding = lastDoneProperty.isNotNull();
            nextDueBinding = ObjectExpression.objectExpression(this.dueDate);
            inProgressBinding = doneBinding.not().or(TaskTwig.todayValue().isEqualTo(lastDoneProperty));
        }

        @Override
        public boolean isOverdue() {
            return !isDone() && TaskTwig.today().isAfter(dueDate.get());
        }

        @Override
        public void setDone(boolean done) {
            lastDoneProperty.set(done ? TaskTwig.today() : null);
        }

        @Override
        protected void hashContentsHelper(MessageDigest digest) {
            digest.update("single".getBytes(StandardCharsets.UTF_8));
            digest.update(getDueDate().toString().getBytes(StandardCharsets.UTF_8));
        }

        public ObjectProperty<LocalDate> dueDateProperty() {
            return dueDate;
        }

        @JsonGetter("date")
        public LocalDate getDueDate() {
            return TaskTwig.callWithFXSafety(dueDate::get);
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
    @JsonIncludeProperties({"interval", "fromLastDone", "lastDone", "nextDue"})
    public static class DayInterval extends TaskInterval {
        private final IntegerProperty intervalDays = new SimpleIntegerProperty();
        private final BooleanProperty repeatFromLastDone = new SimpleBooleanProperty();
        private final ObjectProperty<LocalDate> nextDue = new SimpleObjectProperty<>();

        public DayInterval(int intervalDays, boolean repeatFromLastDone) {
            this(intervalDays, repeatFromLastDone, TaskTwig.today().plusDays(intervalDays));
        }

        public DayInterval(int intervalDays, boolean repeatFromLastDone, LocalDate firstDue) {
            this(intervalDays, repeatFromLastDone, null, firstDue);
        }

        public DayInterval(int intervalDays, boolean repeatFromLastDone, LocalDate lastDone, LocalDate nextDue) {
            super(lastDone);
            this.intervalDays.set(Math.max(intervalDays, 1));
            this.repeatFromLastDone.set(repeatFromLastDone);
            this.nextDue.set(nextDue);
            generateBinding();
        }

        private void generateBinding() {
            doneBinding = new BooleanBinding() {
                {
                    bind(lastDoneProperty, intervalDays, nextDue, repeatFromLastDone, TaskTwig.todayValue());
                }
                @Override
                protected boolean computeValue() {
                    updateDueDate();
                    LocalDate lastDone = lastDoneProperty.get();
                    return lastDone != null &&
                    (lastDone.until(nextDue.get(), ChronoUnit.DAYS) < intervalDays.get()
                    || lastDone.equals(TaskTwig.today()));
                }
            };
            inProgressBinding = new BooleanBinding() {
                {
                    bind(intervalDays, nextDue, TaskTwig.todayValue());
                }
                @Override
                protected boolean computeValue() {
                    return !TaskTwig.today().isBefore(nextDue.get().minusDays(intervalDays.get()));
                }
            };
            nextDueBinding = ObjectExpression.objectExpression(nextDue);
        }

        private void updateDueDate() {
            if (!repeatFromLastDone.get()) {
                while (!TaskTwig.today().isBefore(nextDue.get()))
                    nextDue.set(nextDue.get().plusDays(intervalDays.get()));
            }
        }

        @Override
        public boolean isOverdue() {
            return false;
        }

        @Override
        public void setDone(boolean done) {
            if (done) {
                lastDoneProperty.set(TaskTwig.today());

                if (repeatFromLastDone.get()) {
                    nextDue.set(lastDoneProperty.get().plusDays(intervalDays.get()));
                }
            }
            else {
                lastDoneProperty.set(null);
            }
        }

        @Override
        protected void hashContentsHelper(MessageDigest digest) {
            digest.update("days".getBytes(StandardCharsets.UTF_8));
            digest.update(ByteBuffer.allocate(4).putInt(getInterval()));
            digest.update((byte) (isRepeatFromLastDone() ? 1 : 0));
            digest.update(getNextDue().toString().getBytes(StandardCharsets.UTF_8));
        }

        public ObjectProperty<LocalDate> nextDueProperty() {
            return nextDue;
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

        @JsonGetter("nextDue")
        public LocalDate getNextDue() {
            return TaskTwig.callWithFXSafety(nextDue::get);
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
    @JsonIncludeProperties({"bitmap", "lastDone"})
    public static class WeekInterval extends TaskInterval {

        private final ReadOnlyObjectWrapper<Byte> dayOfWeekMap = new ReadOnlyObjectWrapper<>((byte) 0);

        public WeekInterval() {
            this(Collections.emptyList());
        }

        public WeekInterval(List<DayOfWeek> days) {
            byte bitmap = 0;
            for (DayOfWeek day : days) {
                bitmap |= (byte) (1 << day.ordinal());
            }
            this(bitmap, null);
        }

        public WeekInterval(byte bitmap, LocalDate lastDone) {
            super(lastDone);
            dayOfWeekMap.set(bitmap);
            generateBinding();
        }

        private void generateBinding() {
            doneBinding = new BooleanBinding() {
                {
                    bind(lastDoneProperty, dayOfWeekMap, TaskTwig.todayValue());
                }

                @Override
                protected boolean computeValue() {
                    LocalDate lastDone = lastDoneProperty.get();
                    LocalDate lastDue = getLastDue();

                    if (lastDone == null)
                        return false;

                    if (lastDue == null)
                        return lastDoneProperty.get() != null;

                    return lastDoneProperty.get().isAfter(lastDue);
                }
            };

            inProgressBinding = doneBinding.not().or(TaskTwig.todayValue().isEqualTo(lastDoneProperty));
            nextDueBinding = new ObjectBinding<LocalDate>() {
                {
                    bind(dayOfWeekMap, TaskTwig.todayValue());
                }

                @Override
                protected LocalDate computeValue() {
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
            };
        }

        @Override
        public boolean isOverdue() {
            return false;
        }

        @Override
        public void setDone(boolean done) {
            if (done)
                lastDoneProperty.set(TaskTwig.today());
            else
                lastDoneProperty.set(null);
        }

        @Override
        protected void hashContentsHelper(MessageDigest digest) {
            digest.update("week".getBytes(StandardCharsets.UTF_8));
            digest.update(getDayOfWeekBitmap());
        }

        public ReadOnlyObjectProperty<Byte> dayOfWeekMapProperty() {
            return dayOfWeekMap.getReadOnlyProperty();
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

        private LocalDate getLastDue() {
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
    public static class MonthInterval extends TaskInterval {
        private final ObservableList<Integer> dates = FXCollections.observableArrayList();

        public MonthInterval() {
            this(Collections.emptyList());
        }

        public MonthInterval(List<Integer> dates) {
            this(dates, null);
        }

        public MonthInterval(List<Integer> dates, LocalDate lastDone) {
            super(lastDone);
            this.dates.setAll(dates);
            this.dates.sort(Comparator.naturalOrder());
            this.dates.addListener((ListChangeListener<Integer>) c ->
                    this.dates.sort(Comparator.naturalOrder()));
            generateBinding();
        }

        private void generateBinding() {
            doneBinding = new BooleanBinding() {
                {
                    bind(lastDoneProperty, dates, TaskTwig.todayValue());
                }

                @Override
                protected boolean computeValue() {
                    LocalDate lastDone = lastDoneProperty.get();

                    if (lastDone == null)
                        return false;

                    return lastDone.isAfter(getLastDue());
                }
            };

            inProgressBinding = doneBinding.not().or(TaskTwig.todayValue().isEqualTo(lastDoneProperty));
            nextDueBinding = new ObjectBinding<LocalDate>() {
                {
                    bind(dates, TaskTwig.todayValue());
                }

                @Override
                protected LocalDate computeValue() {
                    if (dates.isEmpty())
                        return null;

                    LocalDate today = TaskTwig.today();
                    int todayDate = today.getDayOfMonth();
                    int maxDate = today.lengthOfMonth();

                    for (int date : dates) {
                        if (date >= todayDate) {
                            return today.withDayOfMonth(Math.min(date, maxDate));
                        }
                    }

                    return today.withDayOfMonth(Math.min(dates.getFirst(), maxDate)).plusMonths(1);
                }
            };
        }

        @Override
        public boolean isOverdue() {
            return false;
        }

        @Override
        public void setDone(boolean done) {
            if (done)
                lastDoneProperty.set(TaskTwig.today());
            else
                lastDoneProperty.set(null);
        }

        @Override
        protected void hashContentsHelper(MessageDigest digest) {
            digest.update("month".getBytes(StandardCharsets.UTF_8));
            for (int date : getDates()) {
                digest.update((byte) date);
            }
        }

        @JsonGetter("dates")
        public List<Integer> getDates() {
            return TaskTwig.callWithFXSafety(() -> new ArrayList<>(dates));
        }

        public ObservableList<Integer> getDatesObservable() {
            return dates;
        }

        private LocalDate getLastDue() {
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


