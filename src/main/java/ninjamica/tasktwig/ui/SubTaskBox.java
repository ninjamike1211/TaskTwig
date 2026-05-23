package ninjamica.tasktwig.ui;

import javafx.scene.input.MouseEvent;
import ninjamica.tasktwig.core.SubTask;
import ninjamica.tasktwig.core.TaskInterface;
import ninjamica.tasktwig.ui.util.TaskInterfaceBoxBase;

import java.util.function.BiConsumer;

public class SubTaskBox extends TaskInterfaceBoxBase<SubTask> {

    private final BiConsumer<MouseEvent, TaskInterface> subTaskClickHandler;

    public SubTaskBox(BiConsumer<MouseEvent, TaskInterface> subTaskClickHandler) {
        this.subTaskClickHandler = subTaskClickHandler;
    }

    public SubTaskBox(SubTask subTask, BiConsumer<MouseEvent, TaskInterface> subTaskClickHandler) {
        this(subTaskClickHandler);
        setTask(subTask);
    }

    @Override
    public void setTask(SubTask subTask) {
        super.setTask(subTask);

        if (subTask != null) {
            nameText.setOnMouseClicked(event -> subTaskClickHandler.accept(event, subTask));
        }
    }

    @Override
    public void unbind() {
        super.unbind();
        nameText.setOnMouseClicked(null);
    }
}
