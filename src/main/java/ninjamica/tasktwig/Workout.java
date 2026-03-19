package ninjamica.tasktwig;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;

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

    public Workout(JsonNode node, int version) {
        LocalDateTime start, end;
        Map<Exercise, Integer> exercises = new TreeMap<>();

        if (version == 1) {
            start = LocalDateTime.parse(node.get("start").asString());
            end = LocalDateTime.parse(node.get("end").asString());

            JsonNode exercisesNode = node.get("exercises");
            for (Map.Entry<String, JsonNode> exercise : exercisesNode.properties()) {
                exercises.put(new Exercise(exercise.getKey()), exercise.getValue().asInt());
            }
        }
        else {
            throw new TaskTwig.JsonVersionException("Unsupported Workout version: " + version);
        }

        this(start, end, exercises);
    }

    public Duration length() {
        return Duration.between(start, end);
    }

    public void hashContents(MessageDigest digest) {
        digest.update(start.toString().getBytes(StandardCharsets.UTF_8));
        digest.update(end.toString().getBytes(StandardCharsets.UTF_8));
        for (Map.Entry<Exercise, Integer> exercise : exercises.entrySet()) {
            exercise.getKey().hashContents(digest);
            digest.update(ByteBuffer.allocate(4).putInt(exercise.getValue()));
        }
    }
}
