package ninjamica.tasktwig;

import java.io.Serializable;

public record Exercise(String name, ExerciseUnit unit) implements Serializable, Comparable<Exercise> {

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

    @Override
    public int compareTo(Exercise other) {
        return this.name.compareTo(other.name);
    }
}
