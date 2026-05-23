package ninjamica.tasktwig.core;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@JsonIncludeProperties({"name", "points", "occurrencePattern", "extend", "interval", "dueTime", "lastDone", "expanded", "subTasks"})
@JsonPropertyOrder({"name", "points", "occurrencePattern", "extend", "interval", "dueTime", "lastDone", "expanded", "subTasks"})
public class Task implements TaskInterface {
    public static final int VERSION = 11;

    public enum OccurrencePattern {
        OCCUR_ON,
        DUE_BY,
        START_ON
    }

    public enum ExtendPattern {
        NO_EXTEND,
        ON_COMPLETION,
        FROM_COMPLETION,
        AUTO
    }

    private final StringProperty name = new SimpleStringProperty();
    private final ReadOnlyObjectWrapper<TaskCategory> category = new ReadOnlyObjectWrapper<>();
    private final IntegerProperty points = new SimpleIntegerProperty();
    private final ObjectProperty<OccurrencePattern> occurrencePattern = new SimpleObjectProperty<>();
    private final ObjectProperty<ExtendPattern> extendPattern = new SimpleObjectProperty<>();
    private final ObjectProperty<TwigInterval> interval = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalTime> dueTime = new SimpleObjectProperty<>();
    private final ObservableList<SubTask> subTasks = FXCollections.observableArrayList();
    private final BooleanProperty subTasksExpanded = new SimpleBooleanProperty();
    private final ObjectProperty<LocalDate> lastDone = new SimpleObjectProperty<>();

    private final BooleanBinding isDoneBinding;
    private final BooleanBinding isTodayBinding;
    private final BooleanBinding isOverdueBinding;
    private final ObservableValue<LocalDate> nextDateBinding;

    public Task(String name, TaskCategory category, int points, @NotNull OccurrencePattern occurrencePattern,
                @NotNull ExtendPattern extendPattern, @NotNull TwigInterval interval, @Nullable LocalTime dueTime,
                @Nullable List<SubTask> subTasks, boolean subTasksExpanded, LocalDate lastDone) {
        this.name.set(name);
        this.points.set(points);
        this.category.set(category);
        this.occurrencePattern.set(occurrencePattern);
        this.extendPattern.set(extendPattern);
        this.interval.set(interval);
        this.dueTime.set(dueTime);
        this.subTasksExpanded.set(subTasksExpanded);
        this.lastDone.set(lastDone);

        if (subTasks != null) {
            this.subTasks.addAll(subTasks);
            this.subTasks.forEach(subTask -> subTask.setParentTask(this));
        }

        this.occurrencePattern.subscribe(occurrence -> updateIntervalPatterns(occurrence, getExtendPattern()));
        this.extendPattern.subscribe(extend -> updateIntervalPatterns(getOccurrencePattern(), extend));

        this.isDoneBinding = Bindings.createBooleanBinding(this::isDone,
                this.interval.flatMap(TwigInterval::occurrenceObservable), this.lastDone, TaskTwig.todayObservable()
        );
        this.isTodayBinding = Bindings.createBooleanBinding(this::isToday,
                this.interval.flatMap(TwigInterval::occurrenceObservable), this.lastDone, TaskTwig.todayObservable(),
                this.occurrencePattern
        );
        this.isOverdueBinding = Bindings.createBooleanBinding(this::isOverdue,
                this.interval.flatMap(TwigInterval::occurrenceObservable), this.lastDone, TaskTwig.todayObservable(),
                this.occurrencePattern
        );
        this.nextDateBinding = this.interval.flatMap(TwigInterval::occurrenceObservable);

//        setCategory(category);
    }

    public Task(String name, TaskCategory category, int points, @NotNull OccurrencePattern occurrencePattern,
                @NotNull ExtendPattern extendPattern, @NotNull TwigInterval interval, @Nullable LocalTime dueTime,
                SubTask... subTasks) {
        this(name, category, points, occurrencePattern, extendPattern, interval, dueTime, List.of(subTasks), false, null);
    }

    public Task(JsonNode node, int version, TaskCategory category) {
        String name;
        TaskCategory taskCategory;
        int points;
        OccurrencePattern occurrencePattern;
        ExtendPattern extendPattern;
        TwigInterval interval;
        LocalTime dueTime;
        List<SubTask> subTasks = new ArrayList<>();
        boolean expanded;
        LocalDate lastDone;

        switch (version) {
            case 11 -> {
                taskCategory = category;
            }
            case 10 -> {
                taskCategory = node.optional("category")
                        .map(catNode -> TaskCategory.getCategoryFromName(catNode.asString()))
                        .orElse(null);
            }
            default -> throw new TaskTwig.TwigJsonVersionException("Invalid version for Task: " + version);
        }

        name = node.required("name").asString();
        points = node.required("points").asInt();
        occurrencePattern = OccurrencePattern.valueOf(node.required("occurrencePattern").asString());
        extendPattern = ExtendPattern.valueOf(node.required("extend").asString());
        interval = TwigInterval.parseFromJson(node.required("interval"), version);

        dueTime = node.optional("dueTime")
                .map(dueTimeNode -> LocalTime.parse(dueTimeNode.asString())).orElse(null);

        node.optional("subTasks").ifPresent(subTaskNode -> {
            for (JsonNode subTask : subTaskNode.asArray()) {
                subTasks.add(new SubTask(subTask, version));
            }
        });

        expanded = node.optional("expanded").map(JsonNode::asBoolean).orElse(false);

        lastDone = node.optional("lastDone")
                .map(lastDoneNode -> LocalDate.parse(lastDoneNode.asString())).orElse(null);

        this(name, taskCategory, points, occurrencePattern, extendPattern, interval, dueTime, subTasks, expanded, lastDone);

        if (version == 10) {
            this.category.get().getTasks().add(this);
        }
    }

    public Task(LegacyTask task) {
        ExtendPattern extendPattern;
        TwigInterval interval;
        List<SubTask> subTasks = new ArrayList<>();

        var taskInterval = task.getInterval();

        String name =  task.getName();
        TaskCategory category = task.getCategory();
        OccurrencePattern occurrencePattern = OccurrencePattern.DUE_BY;
        LocalTime dueTime = task.getDueTime();
        boolean expanded = task.isExpanded();
        LocalDate lastDone = taskInterval.getLastDone();

        switch (taskInterval) {
            case TaskInterval.NoInterval noInterval -> {
                extendPattern = ExtendPattern.AUTO;
                interval = new TwigInterval.NoRepeat(TwigInterval.NoRepeat.NO_DATE);
            }
            case TaskInterval.SingleDateInterval singleDateInterval -> {
                extendPattern = ExtendPattern.AUTO;
                interval = new TwigInterval.NoRepeat(singleDateInterval.getDueDate());
            }
            case TaskInterval.DayInterval dayInterval -> {
                if (dayInterval.isRepeatFromLastDone()) {
                    extendPattern = ExtendPattern.FROM_COMPLETION;
                    interval = new TwigInterval.PeriodInterval(
                            Period.ofDays(dayInterval.getInterval()),
                            TwigInterval.RepeatPattern.FROM_REF,
                            false, lastDone
                    );
                }
                else {
                    extendPattern = ExtendPattern.AUTO;
                    interval = new TwigInterval.PeriodInterval(
                            Period.ofDays(dayInterval.getInterval()),
                            TwigInterval.RepeatPattern.REPEAT_ON_AFTER,
                            true, lastDone
                    );
                }
            }
            case TaskInterval.WeekInterval weekInterval -> {
                extendPattern = ExtendPattern.AUTO;
                interval = new TwigInterval.WeekInterval(
                        1, weekInterval.getDayOfWeekBitmap(),
                        TwigInterval.RepeatPattern.REPEAT_ON_AFTER,
                        true, lastDone
                );
            }
            case TaskInterval.MonthInterval monthInterval -> {
                extendPattern = ExtendPattern.AUTO;
                interval = new TwigInterval.MonthInterval(
                        1, TwigInterval.RepeatPattern.REPEAT_ON_AFTER,
                        true, lastDone, monthInterval.getDates().toArray(Integer[]::new)
                );
            }
        }

        for (LegacyTask subTask : task.getChildrenJson()) {
            subTasks.add(new SubTask(
                    subTask.getName(),
                    subTask.isDone() ? TaskTwig.today() : null,
                    subTask.getDueTime(),
                    null
            ));
        }

        this(name, category, 1, occurrencePattern, extendPattern, interval, dueTime, subTasks, expanded, lastDone);
    }

    public Task(Routine routine) {
        TwigInterval interval;
        LocalDate lastDone;

        String name = routine.getName();
        TaskCategory category = TaskCategory.getCategoryFromName("Routines");
        OccurrencePattern occurrencePattern = OccurrencePattern.OCCUR_ON;
        ExtendPattern extendPattern = ExtendPattern.AUTO;
        LocalTime dueTime = routine.getDueTime();

        switch (routine.getInterval()) {
            case RoutineInterval.DailyInterval dailyInterval -> {
                lastDone = dailyInterval.getLastDone();
                interval = new TwigInterval.DailyInterval();
            }
            case RoutineInterval.DayInterval dayInterval -> {
                lastDone = dayInterval.getLastDone();
                interval = new TwigInterval.PeriodInterval(
                        Period.ofDays(dayInterval.getInterval()),
                        TwigInterval.RepeatPattern.REPEAT_ON_AFTER,
                        true, lastDone
                );
            }
            case RoutineInterval.WeekInterval weekInterval -> {
                lastDone = weekInterval.getLastDone();
                interval = new TwigInterval.WeekInterval(
                        1, weekInterval.getBitmap(),
                        TwigInterval.RepeatPattern.REPEAT_ON_AFTER, true, lastDone
                );
            }
        }

        this(name, category, 1, occurrencePattern, extendPattern, interval, dueTime, null, false, lastDone);
    }

    static void setIntervalPatterns(TwigInterval interval, OccurrencePattern occurrence, ExtendPattern extend) {
        if (interval instanceof TwigInterval.ConfigRepeatInterval repeatInterval) {

            repeatInterval.setAutoRepeat(extend == ExtendPattern.AUTO);

            if (Objects.requireNonNull(extend) == ExtendPattern.NO_EXTEND) {
                repeatInterval.setRepeatPattern(TwigInterval.RepeatPattern.FROM_REF);
            }
            else {
                repeatInterval.setRepeatPattern(
                        switch (occurrence) {
                            case OCCUR_ON, DUE_BY -> TwigInterval.RepeatPattern.REPEAT_ON_AFTER;
                            case START_ON -> TwigInterval.RepeatPattern.REPEAT_ON_BEFORE;
                        }
                );
            }
        }
    }

    private void updateIntervalPatterns(OccurrencePattern occurrence, ExtendPattern extend) {
        setIntervalPatterns(getInterval(), occurrence, extend);
    }

    /**
     * Represents whether the task should show up in the Today Pane for `TaskTwig.today()`. Completed tasks are not
     * included here and are checked for separately.
     * @return `true` if the task should appear in Today Pane and is not done
     */
    public boolean isToday() {
        if (isDone())
            return false;

        LocalDate next = getInterval().getNextOccurrence();
        if (next == null)
            return false;

        LocalDate today = TaskTwig.today();
        switch (getOccurrencePattern()) {
            case OCCUR_ON -> {
                return today.equals(next);
            }
            case DUE_BY -> {
                return true;
            }
            case START_ON -> {
                return today.isAfter(next);
            }
            case null -> throw new IllegalStateException("Illegal occurrence pattern (likely null)");
        }
    }

    /**
     * Returns whether the task has been marked as completed <b>as of today</b>
     * @return
     */
    public boolean isDone() {
        return isDone(getLastDone());
    }

    boolean isDone(LocalDate lastDone) {
        if (lastDone == null)
            return false;

        if (lastDone.equals(TaskTwig.today()))
            return true;

        LocalDate prevOccurrence = getInterval().getPrevOccurrence();
        if (prevOccurrence == null)
            return true;

        return lastDone.isAfter(prevOccurrence);
    }

    public boolean isDoneToday() {
        return TaskTwig.today().equals(getLastDone());
    }

    public void setDone(boolean done) {
        LocalDate today = TaskTwig.today();
        setLastDone(done ? today : null);

        TwigInterval interval = getInterval();
        if (done) {
            if(interval instanceof TwigInterval.ConfigRepeatInterval repeatInterval) {
                switch (getExtendPattern()) {
                    case NO_EXTEND, ON_COMPLETION -> repeatInterval.startFromNext();
                    case FROM_COMPLETION -> repeatInterval.startFrom(today);
                }
            }
        }
    }

    public boolean isOverdue() {
        return isOverdue(getLastDone());
    }

    boolean isOverdue(LocalDate lastDone) {
        switch (getOccurrencePattern()) {
            case OCCUR_ON, START_ON -> {
                return false;
            }
            case DUE_BY -> {
                if (isDone(lastDone))
                    return false;

                LocalDate nextDue = getInterval().getNextOccurrence();
                if (nextDue == null)
                    return false;

                return TaskTwig.today().isAfter(nextDue);
            }
            case null -> throw new IllegalStateException("Illegal occurrence pattern (likely null)");
        }
    }

    public BooleanExpression isTodayObservable() {
        return this.isTodayBinding;
    }
    public BooleanExpression isDoneObservable() {
        return this.isDoneBinding;
    }
    public BooleanExpression isOverdueObservable() {
        return this.isOverdueBinding;
    }
    public ObservableValue<LocalDate> nextDateObservable() {
        return nextDateBinding;
    }

    public StringProperty nameProperty() {
        return name;
    }
    public ReadOnlyObjectProperty<TaskCategory> categoryProperty() {
        return category.getReadOnlyProperty();
    }
    public IntegerProperty pointsProperty() {
        return points;
    }
    public ObjectProperty<OccurrencePattern> occurrencePatternProperty() {
        return occurrencePattern;
    }
    public ObjectProperty<ExtendPattern> extendPatternProperty() {
        return extendPattern;
    }
    public ObjectProperty<TwigInterval> intervalProperty() {
        return interval;
    }
    public ObservableList<SubTask> getSubTasks() {
        return subTasks;
    }
    public BooleanProperty subTasksExpandedProperty() {
        return subTasksExpanded;
    }
    public ObjectProperty<LocalTime> dueTimeProperty() {
        return dueTime;
    }

    @JsonGetter("name")
    public String getName() {
        return TaskTwig.supplyWithFXSafety(name::get);
    }

    public TaskCategory getCategory() {
        return TaskTwig.supplyWithFXSafety(category::get);
    }

    @JsonGetter("category")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getCategoryName() {
//        return TaskTwig.supplyWithFXSafety(() -> Optional.ofNullable(category.get()).map(TaskCategory::getName).orElse(""));
        return TaskTwig.supplyWithFXSafety(() -> category.get().getName());
    }

    @JsonGetter("points")
    public int getPoints() {
        return TaskTwig.supplyWithFXSafety(points::get);
    }

    @JsonGetter("occurrencePattern")
    public OccurrencePattern getOccurrencePattern() {
        return TaskTwig.supplyWithFXSafety(occurrencePattern::get);
    }

    @JsonGetter("extend")
    public ExtendPattern getExtendPattern() {
        return TaskTwig.supplyWithFXSafety(extendPattern::get);
    }

    @JsonGetter("interval")
    public TwigInterval getInterval() {
        return TaskTwig.supplyWithFXSafety(interval::get);
    }

    public LocalDate getNextDate() {
        return nextDateBinding.getValue();
    }

    @JsonGetter("subTasks")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public  List<SubTask> getSubTasksJson() {
        return TaskTwig.supplyWithFXSafety(() -> new ArrayList<>(subTasks));
    }

    @JsonGetter("expanded")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public boolean isExpanded() {
        return TaskTwig.supplyWithFXSafety(subTasksExpanded::get);
    }

    @JsonGetter("dueTime")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public LocalTime getDueTime() {
        return TaskTwig.supplyWithFXSafety(dueTime::get);
    }

    public void setName(String name) {
        TaskTwig.setWithFXSafety(this.name::set, name);
    }
    public void setCategory(@Nullable TaskCategory category) {
        TaskTwig.runWithFXSafety(() -> {
            if (this.category.get() != category) {
                if (this.category.get() != null)
                    this.category.get().getTasks().remove(this);

                this.category.set(category);

                if (category != null)
                    category.getTasks().add(this);
            }
        });
    }
    public void setPoints(int points) {
        TaskTwig.setWithFXSafety(this.points::set, points);
    }
    public void setOccurrencePattern(@NotNull Task.OccurrencePattern occurrencePattern) {
        TaskTwig.setWithFXSafety(this.occurrencePattern::set, occurrencePattern);
    }
    public void setExtendPattern(@NotNull ExtendPattern extendPattern) {
        TaskTwig.setWithFXSafety(this.extendPattern::set, extendPattern);
    }
    public void setInterval(@NotNull TwigInterval interval) {
        TaskTwig.setWithFXSafety(this.interval::set, interval);
    }
    public void setSubTasksExpanded(boolean expanded) {
        TaskTwig.setWithFXSafety(this.subTasksExpanded::set, expanded);
    }
    public void setDueTime(@Nullable LocalTime dueTime) {
        TaskTwig.setWithFXSafety(this.dueTime::set, dueTime);
    }

    @JsonGetter("lastDone")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public LocalDate getLastDone() {
        return TaskTwig.supplyWithFXSafety(lastDone::get);
    }
    private void setLastDone(@Nullable LocalDate lastDone) {
        TaskTwig.setWithFXSafety(this.lastDone::set, lastDone);
    }

    public void hashContents(MessageDigest digest) {
        digest.update(getName().getBytes(StandardCharsets.UTF_8));
        digest.update(getCategoryName().getBytes(StandardCharsets.UTF_8));
        getInterval().hashContents(digest);
        digest.update(ByteBuffer.allocate(4).putInt(getPoints()).array());
        digest.update((byte) getOccurrencePattern().ordinal());
        digest.update((byte) getExtendPattern().ordinal());

        Optional.ofNullable(getDueTime()).ifPresent(
                time -> digest.update(time.toString().getBytes(StandardCharsets.UTF_8)));
        Optional.ofNullable(getLastDone()).ifPresent(
                date -> digest.update(date.toString().getBytes(StandardCharsets.UTF_8)));

        getSubTasksJson().forEach(subTask -> subTask.hashContents(digest));

        digest.update(isExpanded() ? (byte) 1 : (byte) 0);
    }

    public String toString() {
        return "Task[" +
                "name=" + getName() +
                ", category=" + Optional.ofNullable(getCategory()).map(TaskCategory::getName).orElse("null") +
                ", interval=" + getInterval() +
                ", lastDone=" + getLastDone() +
                ", subTasks=" + getSubTasks() +
                ", occurrencePattern=" + getOccurrencePattern() +
                ", extendPattern=" + getExtendPattern() +
                ", dueTime=" + getDueTime() +
                "]";
    }
}
