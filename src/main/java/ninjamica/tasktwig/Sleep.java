package ninjamica.tasktwig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

public final class Sleep extends TaskTwig.HasVersion {
    public static final int VERSION = 1;

    private final LocalDateTime start;
    private final LocalDateTime end;

    @JsonCreator
    public Sleep(
            @JsonProperty("start") LocalDateTime start,
            @JsonProperty("end") LocalDateTime end)
    {
        this.start = start;
        this.end = end;
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

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Sleep) obj;
        return Objects.equals(this.start, that.start) &&
                Objects.equals(this.end, that.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public String toString() {
        return "Sleep[" +
                "start=" + start + ", " +
                "end=" + end + ']';
    }

}
