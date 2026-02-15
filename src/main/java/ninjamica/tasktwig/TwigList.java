package ninjamica.tasktwig;

import com.fasterxml.jackson.annotation.*;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;

//@JsonIncludeProperties({"name", "items", "expanded"})
public record TwigList(StringProperty name, ObservableList<TwigListItem> items, BooleanProperty expanded) {

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
        public String getName() { return name.get(); }
        @JsonGetter("done")
        public boolean isDone() { return done.get(); }
    }

    @JsonCreator
    public TwigList(@JsonProperty("name") String name, @JsonProperty("items") List<TwigListItem> items, @JsonProperty("expanded") boolean expanded) {
        this(new SimpleStringProperty(name), FXCollections.observableList(items), new SimpleBooleanProperty(expanded));
    }

    public TwigList(String name) {
        this(new SimpleStringProperty(name), FXCollections.observableArrayList(), new SimpleBooleanProperty(true));
    }

    @JsonGetter("name")
    public String getName() { return name.get(); }
    @JsonGetter("expanded")
    public boolean isExpanded() { return expanded.get(); }

}
