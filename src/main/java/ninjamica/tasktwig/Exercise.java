package ninjamica.tasktwig;

import tools.jackson.databind.JsonNode;

public record Exercise(String name, ExerciseUnit unit) {

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

    public Exercise(TaskTwig.TwigJsonNode twigNode) {
        JsonNode node = twigNode.node();
        this(node.get("name").asString(), ExerciseUnit.valueOf(node.get("unit").asString()));
    }
}
