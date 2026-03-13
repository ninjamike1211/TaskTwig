package ninjamica.tasktwig.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import ninjamica.tasktwig.Routine;
import ninjamica.tasktwig.TwigInterval;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RoutineDialog extends Dialog<Routine> {

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
    private CheckBox dueTimeCheckbox;
    @FXML
    private Spinner<LocalTime> dueTimeSpinner;

    private final static ObservableList<String> types = FXCollections.observableArrayList("Daily", "Weekly");
//    private Routine inputTask;

    public RoutineDialog(Window owner) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("fxml/routine-dialog.fxml"));
            loader.setController(this);

            DialogPane dialogPane = new DialogPane();
            dialogPane.setContent(loader.load());
            dialogPane.getStylesheets().add(getClass().getResource("fxml/dark-theme.css").toExternalForm());

            initOwner(owner);
            initModality(Modality.APPLICATION_MODAL);

            setTitle("Create Routine");
            dialogPane.setHeaderText("Create Routine");
            dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            setDialogPane(dialogPane);
            setResultConverter(this::createRoutine);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @FXML
    private void initialize() {
        typeChoiceBox.setItems(types);
        typeChoiceBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> updateVbox());
        typeChoiceBox.getSelectionModel().select(0);

        new TimeSpinner(dueTimeSpinner);
        dueTimeSpinner.disableProperty().bindBidirectional(dueTimeCheckbox.selectedProperty());

    }

    private void updateVbox() {

        if (typeChoiceBox.getValue().equals("Daily")) {
            contentVbox.getChildren().remove(dayOfWeekPane);
        }
        else {
            if (!contentVbox.getChildren().contains(dayOfWeekPane)) {
                contentVbox.getChildren().add(2, dayOfWeekPane);
            }
        }
    }

    private Routine createRoutine(ButtonType button) {
        if (!Objects.equals(ButtonBar.ButtonData.OK_DONE, button.getButtonData())) {
            return null;
        }

        LocalTime dueTime = dueTimeCheckbox.isSelected() ? null : dueTimeSpinner.getValue();

        TwigInterval interval;
        switch (typeChoiceBox.getValue()) {
            case "Daily" -> {
                interval = new TwigInterval.DailyInterval();
            }
            case "Weekly" -> {
                List<DayOfWeek> days = new ArrayList<>();
                if (dayMButton.isSelected())
                    days.add(DayOfWeek.MONDAY);
                if (dayTButton.isSelected())
                    days.add(DayOfWeek.TUESDAY);
                if (dayWButton.isSelected())
                    days.add(DayOfWeek.WEDNESDAY);
                if (dayThButton.isSelected())
                    days.add(DayOfWeek.THURSDAY);
                if (dayFButton.isSelected())
                    days.add(DayOfWeek.FRIDAY);
                if (daySaButton.isSelected())
                    days.add(DayOfWeek.SATURDAY);
                if (daySuButton.isSelected())
                    days.add(DayOfWeek.SUNDAY);

                interval = new TwigInterval.WeeklyInterval(days.toArray(DayOfWeek[]::new));
            }
            default -> interval = null;
        }

        return new Routine(nameTextField.getText(), dueTime, interval);
    }
}
