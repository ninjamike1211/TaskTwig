package ninjamica.tasktwig;

import com.fasterxml.jackson.annotation.*;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record TwigList(StringProperty name, @JsonGetter("items") ObservableList<TwigListItem> items,
                       BooleanProperty expanded) {
    public static final int VERSION = 1;

    public record TwigListItem(StringProperty name, BooleanProperty done) {
        @JsonCreator
        public TwigListItem(@JsonProperty("name") String name, @JsonProperty("done") boolean done) {
            this(new SimpleStringProperty(name), new SimpleBooleanProperty(done));
        }

        public TwigListItem(String name) {
            this(name, false);
        }

        public TwigListItem() {
            this("", false);
        }

        @JsonGetter("name")
        public String getName() {
            return name.get();
        }

        @JsonGetter("done")
        public boolean isDone() {
            return done.get();
        }
    }

    @JsonCreator
    public TwigList(@JsonProperty("name") String name, @JsonProperty("items") List<TwigListItem> items, @JsonProperty("expanded") boolean expanded) {
        this(new SimpleStringProperty(name), FXCollections.observableList(items), new SimpleBooleanProperty(expanded));
    }

    public TwigList(String name) {
        this(new SimpleStringProperty(name), FXCollections.observableArrayList(), new SimpleBooleanProperty(true));
    }

    public TwigList(TaskTwig.TwigJsonNode twigNode) {
        JsonNode node = twigNode.node();
        String name = null;
        List<TwigListItem> items = new ArrayList<>();
        boolean expanded = true;

        if (twigNode.version() == 1) {
            name = node.get("name").asString();

            for (JsonNode item : node.get("items")) {
                items.add(new TwigListItem(item.get("name").asString(), item.get("done").asBoolean()));
            }

            expanded = node.get("expanded").asBoolean();
        }
        else {
            throw new TaskTwig.JsonVersionException("Unsupported List version: " + twigNode.version());
        }

        this(name, items, expanded);
    }

    @JsonGetter("name")
    public String getName() {
        return name.get();
    }

    @JsonGetter("expanded")
    public boolean isExpanded() {
        return expanded.get();
    }

}
