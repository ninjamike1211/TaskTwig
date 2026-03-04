package ninjamica.tasktwig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record Sleep(@JsonGetter("start") LocalDateTime start, @JsonGetter("end") LocalDateTime end) {
    public static final int VERSION = 1;

    @JsonCreator
    public Sleep(
            @JsonProperty("start") LocalDateTime start,
            @JsonProperty("end") LocalDateTime end) {
        this.start = start;
        this.end = end;
    }

    public Sleep(TaskTwig.TwigJsonNode twigNode) {
        LocalDateTime start = null;
        LocalDateTime end = null;

        JsonNode node = twigNode.node();
        if (twigNode.version() == 1) {
            start = LocalDateTime.parse(node.get("start").asString());
            end = LocalDateTime.parse(node.get("end").asString());
        }
        else {
            throw new TaskTwig.JsonVersionException("Unsupported Sleep version: " + twigNode.version());
        }

        this(start, end);
    }

    public Duration length() {
        return Duration.between(start, end);
    }
}
