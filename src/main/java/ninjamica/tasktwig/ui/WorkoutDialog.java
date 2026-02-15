package ninjamica.tasktwig.ui;

import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.Callback;
import javafx.util.StringConverter;
import ninjamica.tasktwig.Exercise;
import ninjamica.tasktwig.TaskTwig;

import java.io.IOException;
import java.util.*;

public class WorkoutDialog extends Dialog<Map<Exercise, Integer>> {

    protected static class ExerciseHolder {
        protected SimpleIntegerProperty count;
        protected Exercise exercise;

        ExerciseHolder(Exercise exercise, int count) {
            this.count = new SimpleIntegerProperty(count);
            this.exercise = exercise;
        }

        public SimpleIntegerProperty countProperty() {
            return count;
        }
    }

    protected static class SpinnerTableCell extends TableCell<ExerciseHolder, Integer> {
        public static Callback<TableColumn<ExerciseHolder, Integer>, TableCell<ExerciseHolder, Integer>> forTableColumn() {
            return _ -> new SpinnerTableCell();
        }

        private final Spinner<Integer> spinner;

        public SpinnerTableCell() {
            spinner = new Spinner<>(0, 99999, 0);
            spinner.setEditable(true);
        }

        @Override
        protected void updateItem(Integer item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
            }
            else {
                ObservableValue<Integer> observableValue = this.getTableColumn().getCellObservableValue(this.getIndex());
                spinner.getValueFactory().valueProperty().bindBidirectional((Property<Integer>) observableValue);
                setGraphic(spinner);
            }
        }
    }

    @FXML
    private ChoiceBox<Exercise> dialogChoiceBox;
    @FXML
    private TableView<ExerciseHolder> dialogTable;
    @FXML
    private TableColumn<ExerciseHolder, String> dialogExerciseCol;
    @FXML
    private TableColumn<ExerciseHolder, Integer> dialogCountCol;
    @FXML
    private TableColumn<ExerciseHolder, Boolean> dialogCloseCol;
    @FXML
    private Spinner<Integer> dialogSpinner;
    @FXML
    private Button dialogAddButton;

    private final ObservableList<ExerciseHolder> selectedExercises = new SimpleListProperty<>(FXCollections.observableArrayList());
    private final TaskTwig twig;

    public WorkoutDialog(Window owner, TaskTwig twig) {
        try {
            this.twig = twig;

            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("fxml/workout-dialog.fxml"));
            loader.setController(this);

            DialogPane dialogPane = loader.load();

            initOwner(owner);
            initModality(Modality.APPLICATION_MODAL);

            setTitle("Enter Workout Exercises");
            setDialogPane(dialogPane);
            setResultConverter(buttonType -> {
                if(!Objects.equals(ButtonBar.ButtonData.OK_DONE, buttonType.getButtonData())) {
                    return null;
                }

                Map<Exercise, Integer> exercises = new TreeMap<>();
                for (ExerciseHolder exerciseHolder : selectedExercises) {
                    exercises.put(exerciseHolder.exercise, exerciseHolder.count.getValue());
                }
                return exercises;
            });
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @FXML
    private void initialize() {
        dialogExerciseCol.setCellValueFactory(holder -> new SimpleStringProperty(holder.getValue().exercise.name()));
        dialogCountCol.setCellValueFactory(new PropertyValueFactory<>("count"));
        dialogCountCol.setCellFactory(SpinnerTableCell.forTableColumn());
        dialogCloseCol.setCellFactory(e -> new CloseTableCell<>());
        dialogTable.setItems(selectedExercises);

        dialogChoiceBox.setItems(FXCollections.observableList(twig.getExerciseList()));
        dialogChoiceBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Exercise exercise) {
                if(exercise == null) return "";
                return exercise.name();
            }

            @Override
            public Exercise fromString(String string) {
                for (Exercise exercise : twig.getExerciseList()) {
                    if (exercise.name().equals(string)) {
                        return exercise;
                    }
                }
                return null;
            }
        });

        dialogSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, Integer.MAX_VALUE, 0));

    }

    @FXML
    private void addExercise() {
        Exercise exercise = dialogChoiceBox.getSelectionModel().getSelectedItem();
        int count = dialogSpinner.getValue();
        selectedExercises.add(new ExerciseHolder(exercise, count));
    }

    @FXML
    private void editExerciseCountStart(TableColumn.CellEditEvent<ExerciseHolder, Integer> event) {

    }

    @FXML
    private void editExerciseCommitStart(TableColumn.CellEditEvent<ExerciseHolder, Integer> event) {

    }
}

