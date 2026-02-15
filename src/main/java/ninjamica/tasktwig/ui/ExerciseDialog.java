package ninjamica.tasktwig.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.StringConverter;
import ninjamica.tasktwig.Exercise;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class ExerciseDialog extends Dialog<List<Exercise>> {

    @FXML
    private TextField nameTextField;
    @FXML
    private ChoiceBox<Exercise.ExerciseUnit> unitChoiceBox;
    @FXML
    private TableView<Exercise> exerciseTable;
    @FXML
    private TableColumn<Exercise, String> nameCol;
    @FXML
    private TableColumn<Exercise, String> unitCol;
    @FXML
    private TableColumn<Exercise, String> closeCol;
    @FXML
    private Button addButton;

    private final List<Exercise> exercises;

    public ExerciseDialog(Window owner, List<Exercise> exercises) {
        try {
            this.exercises = exercises;

            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("fxml/add-exercise-dialog.fxml"));
            loader.setController(this);

            DialogPane dialogPane = loader.load();

            initOwner(owner);
            initModality(Modality.APPLICATION_MODAL);

            setTitle("Create Exercises");
            setDialogPane(dialogPane);
            setResultConverter(buttonType -> {
                if(!Objects.equals(ButtonBar.ButtonData.OK_DONE, buttonType.getButtonData())) {
                    return null;
                }

                return exerciseTable.getItems().stream().toList();
            });
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @FXML
    private void initialize() {
        nameCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().name()));
        unitCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().unit().name()));
        closeCol.setCellFactory(e -> new CloseTableCell<>());
        exerciseTable.setItems(FXCollections.observableList(exercises));

        unitChoiceBox.setConverter(new StringConverter<Exercise.ExerciseUnit>() {
            @Override
            public String toString(Exercise.ExerciseUnit unit) {
                if (unit == null) {
                    return null;
                }
                return unit.name();
            }

            @Override
            public Exercise.ExerciseUnit fromString(String s) {
                return Exercise.ExerciseUnit.valueOf(s);
            }
        });
        unitChoiceBox.setItems(FXCollections.observableArrayList(Exercise.ExerciseUnit.values()));
    }

    @FXML
    protected void onAddButtonAction(ActionEvent event) {
        Exercise exercise = new Exercise(nameTextField.getText(), unitChoiceBox.getValue());
//        exercises.add(exercise);
        exerciseTable.getItems().add(exercise);
        exerciseTable.refresh();
    }
}
