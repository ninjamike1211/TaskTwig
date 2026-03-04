package ninjamica.tasktwig.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import ninjamica.tasktwig.Routine;
import ninjamica.tasktwig.TwigInterval;

import java.io.IOException;
import java.time.LocalTime;

public class RoutineContent extends AnchorPane {
    @FXML
    private VBox contentVbox;
    @FXML
    private AnchorPane dayOfWeekPane;

    @FXML
    private TextField nameTextField;
    @FXML
    private ChoiceBox<String> typeChoiceBox;
    @FXML
    private ToggleButton dayMButton;
    @FXML
    private ToggleButton dayTButton;
    @FXML
    private ToggleButton dayWButton;
    @FXML
    private ToggleButton dayThButton;
    @FXML
    private ToggleButton dayFButton;
    @FXML
    private ToggleButton daySaButton;
    @FXML
    private ToggleButton daySuButton;
    @FXML
    private CheckBox startTimeCheckbox;
    @FXML
    private Spinner<LocalTime> startTimeSpinner;
    @FXML
    private CheckBox endTimeCheckbox;
    @FXML
    private Spinner<LocalTime> endTimeSpinner;

    private final static ObservableList<String> types = FXCollections.observableArrayList("Daily", "Weekly");

    public RoutineContent(Routine routine) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("fxml/routine-dialog.fxml"));
            loader.setController(this);

            getChildren().add(loader.load());

            nameTextField.textProperty().bindBidirectional(routine.name());
            typeChoiceBox.setItems(types);
            typeChoiceBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> updateType(routine, oldValue, newValue));

            switch (routine.getInterval()) {
                case TwigInterval.DailyInterval daily -> {
                    typeChoiceBox.getSelectionModel().select(0);
                }
                case TwigInterval.WeeklyInterval week -> {
                    typeChoiceBox.getSelectionModel().select(1);
                    dayMButton.selectedProperty().bindBidirectional(week.dayOfWeekProperty(0));
                    dayTButton.selectedProperty().bindBidirectional(week.dayOfWeekProperty(1));
                    dayWButton.selectedProperty().bindBidirectional(week.dayOfWeekProperty(2));
                    dayThButton.selectedProperty().bindBidirectional(week.dayOfWeekProperty(3));
                    dayFButton.selectedProperty().bindBidirectional(week.dayOfWeekProperty(4));
                    daySaButton.selectedProperty().bindBidirectional(week.dayOfWeekProperty(5));
                    daySuButton.selectedProperty().bindBidirectional(week.dayOfWeekProperty(6));
                }
                default -> {}
            }

            startTimeSpinner.disableProperty().bindBidirectional(startTimeCheckbox.selectedProperty());
            endTimeSpinner.disableProperty().bindBidirectional(endTimeCheckbox.selectedProperty());


            if (routine.getStart() == null) {
                startTimeCheckbox.setSelected(true);
                new TimeSpinner(startTimeSpinner);
            }
            else {
                startTimeCheckbox.setSelected(false);
                new TimeSpinner(startTimeSpinner, routine.getStart());
            }

            if (routine.getEnd() == null) {
                endTimeCheckbox.setSelected(true);
                new TimeSpinner(endTimeSpinner);
            }
            else {
                endTimeCheckbox.setSelected(false);
                new TimeSpinner(endTimeSpinner, routine.getEnd());
            }

            startTimeCheckbox.selectedProperty().addListener((observable, oldValue, newValue) -> {
               if (newValue) {
                   routine.startTime().set(null);
               }
               else {
                   routine.startTime().set(startTimeSpinner.getValue());
               }
            });
            startTimeSpinner.valueProperty().addListener(
                    (observable, oldValue, newValue) -> routine.startTime().set(newValue));

            endTimeCheckbox.selectedProperty().addListener((observable, oldValue, newValue) -> {
               if (newValue) {
                   routine.endTime().set(null);
               }
               else {
                   routine.endTime().set(endTimeSpinner.getValue());
               }
            });
            endTimeSpinner.valueProperty().addListener(
                    (observable, oldValue, newValue) -> routine.endTime().set(newValue));

        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateType(Routine routine, String oldValue, String newValue) {

        if (oldValue == null || !oldValue.equals(newValue)) {
            if (typeChoiceBox.getValue().equals("Daily")) {
                contentVbox.getChildren().remove(dayOfWeekPane);

                if ("Weekly".equals(oldValue)) {
                    dayMButton.selectedProperty().unbindBidirectional(((TwigInterval.WeeklyInterval) routine.getInterval()).dayOfWeekProperty(0));
                    dayTButton.selectedProperty().unbindBidirectional(((TwigInterval.WeeklyInterval) routine.getInterval()).dayOfWeekProperty(1));
                    dayWButton.selectedProperty().unbindBidirectional(((TwigInterval.WeeklyInterval) routine.getInterval()).dayOfWeekProperty(2));
                    dayThButton.selectedProperty().unbindBidirectional(((TwigInterval.WeeklyInterval) routine.getInterval()).dayOfWeekProperty(3));
                    dayFButton.selectedProperty().unbindBidirectional(((TwigInterval.WeeklyInterval) routine.getInterval()).dayOfWeekProperty(4));
                    daySaButton.selectedProperty().unbindBidirectional(((TwigInterval.WeeklyInterval) routine.getInterval()).dayOfWeekProperty(5));
                    daySuButton.selectedProperty().unbindBidirectional(((TwigInterval.WeeklyInterval) routine.getInterval()).dayOfWeekProperty(6));
                }

                routine.interval().set(new TwigInterval.DailyInterval());
            }
            else {
                if (!contentVbox.getChildren().contains(dayOfWeekPane)) {
                    contentVbox.getChildren().add(2, dayOfWeekPane);
                    routine.interval().set(new TwigInterval.WeeklyInterval());

                    dayMButton.selectedProperty().bindBidirectional(((TwigInterval.WeeklyInterval)routine.getInterval()).dayOfWeekProperty(0));
                    dayTButton.selectedProperty().bindBidirectional(((TwigInterval.WeeklyInterval)routine.getInterval()).dayOfWeekProperty(1));
                    dayWButton.selectedProperty().bindBidirectional(((TwigInterval.WeeklyInterval)routine.getInterval()).dayOfWeekProperty(2));
                    dayThButton.selectedProperty().bindBidirectional(((TwigInterval.WeeklyInterval)routine.getInterval()).dayOfWeekProperty(3));
                    dayFButton.selectedProperty().bindBidirectional(((TwigInterval.WeeklyInterval)routine.getInterval()).dayOfWeekProperty(4));
                    daySaButton.selectedProperty().bindBidirectional(((TwigInterval.WeeklyInterval)routine.getInterval()).dayOfWeekProperty(5));
                    daySuButton.selectedProperty().bindBidirectional(((TwigInterval.WeeklyInterval)routine.getInterval()).dayOfWeekProperty(6));
                }
            }
        }
    }
}
