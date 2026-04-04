package ninjamica.tasktwig;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import javafx.beans.property.*;
import tools.jackson.databind.JsonNode;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
@JsonSubTypes({
        @JsonSubTypes.Type(value = RoutineInterval.DailyInterval.class, name = "daily"),
        @JsonSubTypes.Type(value = RoutineInterval.DayInterval.class, name = "day"),
        @JsonSubTypes.Type(value = RoutineInterval.WeekInterval.class, name = "week")
})
public interface RoutineInterval {

    /**
     * Whether the routine is today, i.e. should show up in the today page
     * @return whether routine is today
     */
    boolean isToday();

    /**
     * Whether the routine is marked as done (for today)
     * @return whether routine is done
     */
    boolean isDone();

    /**
     * Set the routine as done or not done (for today)
     * @param done whether routine is done or not
     */
    void setDone(boolean done);

    /**
     * Add contents of interval to a MessageDigest
     * @param digest MessageDigest to add hashable contents to
     */
    void hashContents(MessageDigest digest);

    static RoutineInterval parseFromJson(JsonNode node, int version) {
        switch (version) {
            case 3 -> {
                switch (node.get("@type").asString()) {
                    case "daily" -> {
                        JsonNode lastDoneNode = node.get("lastDone");
                        LocalDate lastDone = lastDoneNode.isNull() ? null : LocalDate.parse(lastDoneNode.asString());
                        return new DailyInterval(lastDone);
                    }
                    case "day" -> {
                        JsonNode lastDoneNode = node.get("lastDone");
                        LocalDate lastDone = lastDoneNode.isNull() ? null : LocalDate.parse(lastDoneNode.asString());
                        return new DayInterval(node.get("interval").asInt(), node.get("fromLastDone").asBoolean(), lastDone, LocalDate.parse(node.get("nextDue").asString()));
                    }
                    case "week" -> {
                        JsonNode lastDoneNode = node.get("lastDone");
                        LocalDate lastDone = lastDoneNode.isNull() ? null : LocalDate.parse(lastDoneNode.asString());
                        return new WeekInterval((byte) node.get("bitmap").asInt(), node.get("keepTillDone").asBoolean(), lastDone);
                    }
                }
            }
            case 1,2 -> {
                switch (node.get("@type").asString()) {
                    case "daily" -> {
                        return new DailyInterval();
                    }
                    case "weekly" -> {
                        byte bitmap = 0;

                        int i = 0;
                        for (JsonNode day : node.get("daysOfWeek")) {
                            if (day.asBoolean()) {
                                bitmap |= (byte) (1 << i);
                            }
                        }
                        return new WeekInterval(bitmap, false, null);
                    }
                }
            }
            default -> throw new TaskTwig.JsonVersionException("Unsupported RoutineInterval version: " + version);
        }
        throw new TaskTwig.JsonVersionException("Unsupported RoutineInterval type \"" + node.get("@type").asString() + "\" for version: " + version);
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
    @JsonIncludeProperties({"lastDone"})
    class DailyInterval implements RoutineInterval {
        private LocalDate lastDone;

        public DailyInterval() {}

        private DailyInterval(LocalDate lastDone) {
            this.lastDone = lastDone;
        }

        @Override
        public boolean isToday() {
            return true;
        }

        @Override
        public boolean isDone() {
            return TaskTwig.today().equals(lastDone);
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
            digest.update("daily".getBytes(StandardCharsets.UTF_8));

            if (lastDone != null)
                digest.update(lastDone.toString().getBytes(StandardCharsets.UTF_8));
        }

        @JsonGetter("lastDone")
        public LocalDate getLastDone() {
            return lastDone;
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
    @JsonIncludeProperties({"interval", "fromLastDone", "lastDone", "nextDue"})
    class DayInterval implements RoutineInterval {
        private final IntegerProperty intervalDays = new SimpleIntegerProperty();
        private final BooleanProperty repeatFromLastDone = new SimpleBooleanProperty(false);
        private LocalDate lastDone;
        private LocalDate nextDue;

        public DayInterval(int interval, boolean repeatFromLastDone) {
            this(interval, repeatFromLastDone, TaskTwig.today());
        }

        public DayInterval(int interval, boolean repeatFromLastDone, LocalDate firstDate) {
            this(interval, repeatFromLastDone, null, firstDate);
        }

        public DayInterval(int interval, boolean repeatFromLastDone, LocalDate lastDone, LocalDate nextDue) {
            this.intervalDays.set(interval);
            this.repeatFromLastDone.set(repeatFromLastDone);
            this.lastDone = lastDone;
            this.nextDue = nextDue;
        }

        @Override
        public boolean isToday() {
            if (repeatFromLastDone.get()) {
                return !TaskTwig.today().isBefore(nextDue);
            }
            else {
                while (!TaskTwig.today().isBefore(nextDue))
                    this.nextDue = this.nextDue.plusDays(intervalDays.get());

                return TaskTwig.today().equals(nextDue);
            }
        }

        @Override
        public boolean isDone() {
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
            digest.update("day".getBytes(StandardCharsets.UTF_8));
            digest.update(ByteBuffer.allocate(4).putInt(intervalDays.get()));
            digest.update((byte) (repeatFromLastDone.get() ? 1 : 0));

            if (lastDone != null)
                digest.update(lastDone.toString().getBytes(StandardCharsets.UTF_8));

            digest.update(nextDue.toString().getBytes(StandardCharsets.UTF_8));
        }

        public IntegerProperty intervalProperty() {
            return intervalDays;
        }

        @JsonGetter("interval")
        public int getInterval() {
            return TaskTwig.callWithFXSafety(intervalDays::get);
        }

        public BooleanProperty repeatFromLastDoneProperty() {
            return repeatFromLastDone;
        }

        @JsonGetter("fromLastDone")
        public boolean isRepeatFromLastDone() {
            return TaskTwig.callWithFXSafety(repeatFromLastDone::get);
        }

        @JsonGetter("lastDone")
        public LocalDate getLastDone() {
            return lastDone;
        }

        @JsonGetter("nextDue")
        public LocalDate getNextDue() {
            return nextDue;
        }

        public void setNextDue(LocalDate nextDue) {
            this.nextDue = nextDue;
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
    @JsonIncludeProperties({"bitmap", "keepTillDone", "lastDone"})
    class WeekInterval implements RoutineInterval {
        private final ObjectProperty<Byte> dayOfWeekBitmap = new SimpleObjectProperty<>(((byte) 0));
        private final BooleanProperty keepTillDone = new SimpleBooleanProperty(false);
        private LocalDate lastDone;

        public WeekInterval() {}

        public WeekInterval(List<DayOfWeek> days, boolean keepTillDone) {
            byte bitmap = 0;
            for (DayOfWeek day : days) {
                bitmap |= (byte) (1 << day.ordinal());
            }
            this.dayOfWeekBitmap.set(bitmap);
            this.keepTillDone.set(keepTillDone);
        }

        private WeekInterval(byte bitmap, boolean keepTillDone, LocalDate lastDone) {
            this.dayOfWeekBitmap.set(bitmap);
            this.keepTillDone.set(keepTillDone);
            this.lastDone = lastDone;
        }

        @Override
        public boolean isToday() {
            if (keepTillDone.get()) {
                if (lastDone == null)
                    return true;

                return isIntervalToday() || lastDone.isBefore(lastInterval());
            }
            else {
                return isIntervalToday();
            }
        }

        @Override
        public boolean isDone() {
            if (lastDone == null)
                return false;

            if (keepTillDone.get()) {
                if (isToday())
                    return lastDone.equals(TaskTwig.today());
                else
                    return lastDone.isAfter(lastInterval());
            }
            else {
                return lastDone.equals(nextInterval());
            }
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
            digest.update("week".getBytes(StandardCharsets.UTF_8));
            digest.update((byte) (keepTillDone.get() ? 1 : 0));

            if (lastDone != null)
                digest.update(lastDone.toString().getBytes(StandardCharsets.UTF_8));
        }

        public ObjectProperty<Byte> dayOfWeekBitmapProperty() {
            return dayOfWeekBitmap;
        }

        @JsonGetter("bitmap")
        public byte getBitmap() {
            return TaskTwig.callWithFXSafety(dayOfWeekBitmap::get);
        }

        public BooleanProperty keepTillDoneProperty() {
            return keepTillDone;
        }

        @JsonGetter("keepTillDone")
        public boolean isKeepTillDone() {
            return TaskTwig.callWithFXSafety(keepTillDone::get);
        }

        @JsonGetter("lastDone")
        public LocalDate getLastDone() {
            return lastDone;
        }

        public boolean isIntervalOn(DayOfWeek day) {
            return (dayOfWeekBitmap.get() & (1 << day.ordinal())) != 0;
        }

        private boolean isIntervalOn(int dayOrdinal) {
            return (dayOfWeekBitmap.get() & (1 << dayOrdinal)) != 0;
        }

        private boolean isIntervalToday() {
            return isIntervalOn(TaskTwig.today().getDayOfWeek().ordinal());
        }

        public void setOnDay(DayOfWeek day, boolean due) {
            if (due) {
                dayOfWeekBitmap.set((byte) (dayOfWeekBitmap.get() | (1 << day.ordinal())));
            }
            else {
                dayOfWeekBitmap.set((byte) (dayOfWeekBitmap.get() & ~(1 << day.ordinal())));
            }
        }

        private LocalDate nextInterval() {
            LocalDate today = TaskTwig.today();
            int todayIndex = today.getDayOfWeek().ordinal();

            for (int daysPlus = 0; daysPlus < 7; daysPlus++) {
                int index = (todayIndex + daysPlus) % 7;

                if (isIntervalOn(index)) {
                    return today.plusDays(daysPlus);
                }
            }

            return null;
        }

        private LocalDate lastInterval() {
            LocalDate today = TaskTwig.today();
            int todayIndex = today.getDayOfWeek().ordinal();

            for (int daysMinus = 1; daysMinus < 8; daysMinus++) {
                int index = (todayIndex - daysMinus) % 7;

                if (isIntervalOn(index)) {
                    return today.plusDays(daysMinus);
                }
            }

            return null;
        }
    }
}
