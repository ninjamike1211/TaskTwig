package ninjamica.tasktwig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public record TwigList(StringProperty name, ObservableList<TwigListItem> items, BooleanProperty expanded) {
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
            return TaskTwig.callWithFXSafety(name::get);
        }

        @JsonGetter("done")
        public boolean isDone() {
            return TaskTwig.callWithFXSafety(done::get);
        }

        public void hashContents(MessageDigest digest) {
            digest.update(getName().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) (isDone() ? 1 : 0));
        }
    }

    @JsonCreator
    public TwigList(@JsonProperty("name") String name, @JsonProperty("items") List<TwigListItem> items, @JsonProperty("expanded") boolean expanded) {
        this(new SimpleStringProperty(name), FXCollections.observableList(items), new SimpleBooleanProperty(expanded));
    }

    public TwigList(String name) {
        this(new SimpleStringProperty(name), FXCollections.observableArrayList(), new SimpleBooleanProperty(true));
    }

    public TwigList(JsonNode node, int version) {
        String name;
        List<TwigListItem> items = new ArrayList<>();
        boolean expanded;

        if (version == 1) {
            name = node.get("name").asString();

            for (JsonNode item : node.get("items")) {
                items.add(new TwigListItem(item.get("name").asString(), item.get("done").asBoolean()));
            }

            expanded = node.get("expanded").asBoolean();
        }
        else {
            throw new TaskTwig.JsonVersionException("Unsupported List version: " + version);
        }

        this(name, items, expanded);
    }

    @JsonGetter("name")
    public String getName() {
        return TaskTwig.callWithFXSafety(name::get);
    }

    @JsonGetter
    public List<TwigListItem> getItemsJson() {
        if (TaskTwig.notFxThread())
            return TaskTwig.callWithFXSafety(() -> new ArrayList<>(items));
        else
            return items;
    }

    @JsonGetter("expanded")
    public boolean isExpanded() {
        return TaskTwig.callWithFXSafety(expanded::get);
    }

    public void hashContents(MessageDigest digest) {
        digest.update(getName().getBytes(StandardCharsets.UTF_8));
        for (TwigListItem item : getItemsJson()) {
            item.hashContents(digest);
        }
        digest.update((byte) (isExpanded() ? 1 : 0));
    }

}
