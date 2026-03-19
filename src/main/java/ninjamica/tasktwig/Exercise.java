package ninjamica.tasktwig;

import org.jetbrains.annotations.NotNull;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public record Exercise(String name, ExerciseUnit unit) implements Comparable<Exercise> {

    public enum ExerciseUnit {
        COUNT(""),
        SECONDS("seconds"),
        MINUTES("minutes"),
        MILES("miles");

        final public String displayName;
        ExerciseUnit(String displayName) {
            this.displayName = displayName;
        }
    }

    public Exercise(String strVal) {
        String[] split = strVal.substring(9, strVal.length()-1).split("[,=]");
        this(split[1], ExerciseUnit.valueOf(split[3]));
    }

    public Exercise(JsonNode node, int version) {
        this(node.get("name").asString(), ExerciseUnit.valueOf(node.get("unit").asString()));
    }

    public void hashContents(MessageDigest digest) {
        digest.update(name().getBytes(StandardCharsets.UTF_8));
        digest.update(unit().displayName.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public int compareTo(@NotNull Exercise o) {
        int compare = this.name().compareTo(o.name());

        if (compare == 0) {
            compare = this.unit().displayName.compareTo(o.unit().displayName);
        }

        return compare;
    }
}
