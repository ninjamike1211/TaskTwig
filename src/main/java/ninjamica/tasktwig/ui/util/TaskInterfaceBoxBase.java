package ninjamica.tasktwig.ui.util;

import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.Subscription;
import ninjamica.tasktwig.core.TaskInterface;

public class TaskInterfaceBoxBase<T extends TaskInterface> extends VBox {

    protected final HBox nameBox = new HBox();
    protected final Text nameText = new Text();

    private Subscription subscription = Subscription.EMPTY;

    public TaskInterfaceBoxBase() {
        nameBox.setMinWidth(50);
        nameBox.getChildren().add(nameText);
        getChildren().add(nameBox);
    }

    public TaskInterfaceBoxBase(T task) {
        this();
        setTask(task);
    }

    public void setTask(T task) {
        unbind();

        if (task != null) {
            subscription = task.nameProperty().subscribe(this::setName);
        }
    }

    public void unbind() {
        subscription.unsubscribe();
    }

    private void setName(String name) {
        if (name == null || name.isBlank()) {
            nameText.setText("(Un-Named)");
        }
        else {
            nameText.setText(name);
        }
    }
}
