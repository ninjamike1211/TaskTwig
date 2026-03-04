package ninjamica.tasktwig;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public record Workout(@JsonGetter("start") LocalDateTime start,
                      @JsonGetter("end") LocalDateTime end,
                      @JsonGetter("exercises") Map<Exercise, Integer> exercises) {
    public static final int VERSION = 1;

    public Workout(
            @JsonProperty("start") LocalDateTime start,
            @JsonProperty("end") LocalDateTime end,
            @JsonProperty("exercises") Map<Exercise, Integer> exercises) {
        this.start = start;
        this.end = end;
        this.exercises = exercises;
    }

    public Workout(TaskTwig.TwigJsonNode twigNode) {
        JsonNode node = twigNode.node();
        LocalDateTime start = null, end = null;
        Map<Exercise, Integer> exercises = new HashMap<>();

        if (twigNode.version() == 1) {
            start = LocalDateTime.parse(node.get("start").asString());
            end = LocalDateTime.parse(node.get("end").asString());

            JsonNode exercisesNode = node.get("exercises");
            for (Map.Entry<String, JsonNode> exercise : exercisesNode.properties()) {
                exercises.put(new Exercise(exercise.getKey()), exercise.getValue().asInt());
            }
        }
        else {
            throw new TaskTwig.JsonVersionException("Unsupported Workout version: " + twigNode.version());
        }

        this(start, end, exercises);
    }

    public Duration length() {
        return Duration.between(start, end);
    }
}
