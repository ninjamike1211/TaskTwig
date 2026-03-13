package ninjamica.tasktwig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
            if (TaskTwig.useFxThread())
                return CompletableFuture.supplyAsync(name::getValue, Platform::runLater).join();
            else
                return name.get();
        }

        @JsonGetter("done")
        public boolean isDone() {
            if (TaskTwig.useFxThread())
                return CompletableFuture.supplyAsync(done::get, Platform::runLater).join();
            else
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
        String name;
        List<TwigListItem> items = new ArrayList<>();
        boolean expanded;

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
        if (TaskTwig.useFxThread())
            return CompletableFuture.supplyAsync(name::getValue, Platform::runLater).join();
        else
            return name.get();
    }

    public List<TwigListItem> getItems() {
        return new ArrayList<>(items);
    }

    @JsonGetter
    public List<TwigListItem> getItemsJson() {
        if (TaskTwig.useFxThread())
            return CompletableFuture.supplyAsync(this::getItems, Platform::runLater).join();
        else
            return items;
    }

    @JsonGetter("expanded")
    public boolean isExpanded() {
        if (TaskTwig.useFxThread())
            return CompletableFuture.supplyAsync(expanded::get, Platform::runLater).join();
        else
            return expanded.get();
    }

}
