package ninjamica.tasktwig.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Modality;
import javafx.stage.Window;
import ninjamica.tasktwig.Routine;

import java.util.Objects;

public class RoutineDialog extends Dialog<Routine> {

    private final static ObservableList<String> types = FXCollections.observableArrayList("Daily", "Day Interval", "Weekly");
    private final RoutineContent routinePane;

    public RoutineDialog(Window owner) {
        routinePane = new RoutineContent();

        DialogPane dialogPane = new DialogPane();
        dialogPane.setContent(routinePane);
        dialogPane.getStylesheets().add(getClass().getResource("fxml/dark-theme.css").toExternalForm());

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);

        setTitle("Create Routine");
        dialogPane.setHeaderText("Create Routine");
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        setDialogPane(dialogPane);
        setResultConverter(this::createRoutine);
    }

    private Routine createRoutine(ButtonType button) {
        if (Objects.equals(ButtonBar.ButtonData.OK_DONE, button.getButtonData())) {
            return routinePane.routine;
        }
        else {
            return null;
        }
    }
}
