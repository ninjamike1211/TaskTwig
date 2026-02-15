package ninjamica.tasktwig;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

public final class Workout extends TaskTwig.HasVersion {
    public static final int VERSION = 1;

    private final LocalDateTime start;
    private final LocalDateTime end;
    private final Map<Exercise, Integer> exercises;

    public Workout(
            @JsonProperty("start") LocalDateTime start,
            @JsonProperty("end") LocalDateTime end,
            @JsonProperty("exercises") Map<Exercise, Integer> exercises)
    {
        this.start = start;
        this.end = end;
        this.exercises = exercises;
    }

    public Duration length() {
        return Duration.between(start, end);
    }

    @JsonGetter("start")
    public LocalDateTime start() {
        return start;
    }

    @JsonGetter("end")
    public LocalDateTime end() {
        return end;
    }

    @JsonGetter("exercises")
    public Map<Exercise, Integer> exercises() {
        return exercises;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Workout) obj;
        return Objects.equals(this.start, that.start) &&
                Objects.equals(this.end, that.end) &&
                Objects.equals(this.exercises, that.exercises);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end, exercises);
    }

    @Override
    public String toString() {
        return "Workout[" +
                "start=" + start + ", " +
                "end=" + end + ", " +
                "exercises=" + exercises + ']';
    }

}
