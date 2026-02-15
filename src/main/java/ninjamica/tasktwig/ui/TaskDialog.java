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
import ninjamica.tasktwig.task.*;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TaskDialog extends Dialog<TaskDialog.TaskReturn> {

    public record TaskReturn(ButtonBar.ButtonData type, Task task) {}

    @FXML
    private VBox contentVbox;
    @FXML
    private AnchorPane namePane;
    @FXML
    private AnchorPane typePane;
    @FXML
    private AnchorPane dueDatePane;
    @FXML
    private AnchorPane dayOfWeekPane;
    @FXML
    private AnchorPane dateOfMonthPane;
    @FXML
    private AnchorPane dueTimePane;

    @FXML
    private TextField nameTextField;
    @FXML
    private ChoiceBox<String> typeChoiceBox;
    @FXML
    private DatePicker dueDatePicker;
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
    private TextField dateOfMonthField;
    @FXML
    private CheckBox dueTimeCheckbox;
    @FXML
    private Spinner<LocalTime> dueTimeSpinner;

    private final static ObservableList<String> types = FXCollections.observableArrayList("Single", "Daily", "Weekly", "Monthly");
    private Task inputTask;

    public TaskDialog(Window owner) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("fxml/task-dialog.fxml"));
            loader.setController(this);

            DialogPane dialogPane = loader.load();

            initOwner(owner);
            initModality(Modality.APPLICATION_MODAL);

            setTitle("Create Task");
            dialogPane.setHeaderText("Create Task");
            setDialogPane(dialogPane);
            setResultConverter(this::createTask);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public TaskDialog(Window owner, Task task) {
        this(owner);
        inputTask = task;
        setTitle(task.name());
        getDialogPane().setHeaderText(task.name());
        updateFromTask();

        getDialogPane().getButtonTypes().add(1, new ButtonType("Delete", ButtonBar.ButtonData.OTHER));
    }

    @FXML
    private void initialize() {
        typeChoiceBox.setItems(types);
        typeChoiceBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {updateVbox();});
        typeChoiceBox.getSelectionModel().select(0);

        new TimeSpinner(dueTimeSpinner);
        dueTimeCheckbox.selectedProperty().bindBidirectional(dueTimeSpinner.disableProperty());

    }

    private void updateFromTask() {
        if (inputTask != null) {
            System.out.println(inputTask.name());
            nameTextField.setText(inputTask.name());
            switch (inputTask) {
                case SingleTask singleTask -> {
                    typeChoiceBox.getSelectionModel().select(0);
                    dueDatePicker.setValue(singleTask.nextDueDate());
                }
                case DailyTask dailyTask -> {
                    typeChoiceBox.getSelectionModel().select(1);
                }
                case WeeklyTask weeklyTask -> {
                    typeChoiceBox.getSelectionModel().select(2);
                    dayMButton.setSelected(weeklyTask.getDayOfWeekMap()[0]);
                    dayTButton.setSelected(weeklyTask.getDayOfWeekMap()[1]);
                    dayWButton.setSelected(weeklyTask.getDayOfWeekMap()[2]);
                    dayThButton.setSelected(weeklyTask.getDayOfWeekMap()[3]);
                    dayFButton.setSelected(weeklyTask.getDayOfWeekMap()[4]);
                    daySaButton.setSelected(weeklyTask.getDayOfWeekMap()[5]);
                    daySuButton.setSelected(weeklyTask.getDayOfWeekMap()[6]);
                }
                case MonthlyTask monthlyTask -> {
                    typeChoiceBox.getSelectionModel().select(3);
                    StringBuilder dateString = new StringBuilder();

                    for (int date : monthlyTask.getDueDays()) {
                        dateString.append(date);
                        dateString.append(", ");
                    }
                    dateString.delete(dateString.length() - 2, dateString.length());
                    dateOfMonthField.setText(dateString.toString());
                }
                default -> typeChoiceBox.getSelectionModel().select(0);
            }

            if (inputTask.dueTime() == null) {
                dueTimeCheckbox.setSelected(true);
            }
            else {
                dueTimeCheckbox.setSelected(false);
                dueTimeSpinner.getEditor().setText(TimeSpinner.timeFormat.format(inputTask.dueTime()));
            }
        }
        else {
            typeChoiceBox.getSelectionModel().select(0);
        }
    }

    private void updateVbox() {
        contentVbox.getChildren().clear();
        contentVbox.getChildren().addAll(namePane, typePane);

        switch (typeChoiceBox.getValue()) {
            case "Single":
                contentVbox.getChildren().add(dueDatePane);
                break;

            case "Daily":
                break;

            case "Weekly":
                contentVbox.getChildren().add(dayOfWeekPane);
                break;

            case "Monthly":
                contentVbox.getChildren().add(dateOfMonthPane);
                break;
        }

        contentVbox.getChildren().add(dueTimePane);
    }

    private TaskReturn createTask(ButtonType button) {
        if (!Objects.equals(ButtonBar.ButtonData.OK_DONE, button.getButtonData())) {
            return new TaskReturn(button.getButtonData(), null);
        }

        LocalTime dueTime = dueTimeCheckbox.isSelected() ? null : dueTimeSpinner.getValue();

        switch (typeChoiceBox.getValue()) {
            case "Single":
                return new TaskReturn(button.getButtonData(), new SingleTask(nameTextField.getText(), dueDatePicker.getValue(), dueTime));

            case "Daily":
                return new TaskReturn(button.getButtonData(), new DailyTask(nameTextField.getText(), dueTime));

            case "Weekly":
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

                return new TaskReturn(button.getButtonData(), new WeeklyTask(nameTextField.getText(), days.toArray(DayOfWeek[]::new), dueTime));

            case "Monthly":
                String[] inputText = dateOfMonthField.getText().split(",");
                int[] dates = new int[inputText.length];

                for (int i = 0; i < inputText.length; i++) {
                    dates[i] = Integer.parseInt(inputText[i].strip());
                }

                return new TaskReturn(button.getButtonData(), new MonthlyTask(nameTextField.getText(), dates,  dueTime));

            default:
                return null;
        }
    }
}
