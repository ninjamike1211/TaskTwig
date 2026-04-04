package ninjamica.tasktwig.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.util.Subscription;
import ninjamica.tasktwig.Task;
import ninjamica.tasktwig.TaskInterval.*;
import ninjamica.tasktwig.TaskTwig;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class TaskContent extends VBox {
    @FXML private VBox contentVBox;
    @FXML private Label blankLabel;
    @FXML private AnchorPane namePane;
    @FXML private AnchorPane typePane;
    @FXML private AnchorPane dueDatePane;
    @FXML private AnchorPane dayIntervalPane;
    @FXML private AnchorPane dayOfWeekPane;
    @FXML private AnchorPane dateOfMonthPane;
    @FXML private AnchorPane dueTimePane;

    @FXML private TextField nameTextField;
    @FXML private ChoiceBox<String> typeChoiceBox;
    @FXML private DatePicker dueDatePicker;
    @FXML private Spinner<Integer> dayIntervalSpinner;
    @FXML private DatePicker dayIntervalNextDuePicker;
    @FXML private CheckBox dayIntervalRepeatCheckbox;
    @FXML private ToggleButton dayMButton;
    @FXML private ToggleButton dayTButton;
    @FXML private ToggleButton dayWButton;
    @FXML private ToggleButton dayThButton;
    @FXML private ToggleButton dayFButton;
    @FXML private ToggleButton daySaButton;
    @FXML private ToggleButton daySuButton;
    @FXML private TextField dateOfMonthField;
    @FXML private Spinner<LocalTime> dueTimeSpinner;

    private final static ObservableList<String> types = FXCollections.observableArrayList("No Due Date", "Single Date", "Day Interval", "Week Interval", "Month Interval");
    private Subscription subscription = Subscription.EMPTY;
    private Subscription typeSubs =  Subscription.EMPTY;
    Task task;

    public TaskContent() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("fxml/task-dialog.fxml"));
            loader.setController(this);
            getChildren().add(loader.load());
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        updateType(null, null, false);
    }

    public TaskContent(Task task) {
        this();
        setTask(task);
    }

    @FXML
    protected void initialize() {
        typeChoiceBox.setItems(types);
        dayIntervalSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, Integer.MAX_VALUE, 1));
        new TimeSpinner(dueTimeSpinner, null);
    }

    public void setTask(Task task) {
        this.task = task;
        subscription.unsubscribe();
        subscription = Subscription.EMPTY;

        if (task != null) {
            nameTextField.textProperty().bindBidirectional(task.nameProperty());
            subscription = subscription.and(() -> nameTextField.textProperty().unbindBidirectional(task.nameProperty()));

            String taskType = switch (task.getInterval()) {
                case NoInterval none -> "No Due Date";
                case SingleDateInterval single -> "Single Date";
                case DayInterval day -> "Day Interval";
                case WeekInterval week -> "Week Interval";
                case MonthInterval month -> "Month Interval";
                default -> null;
            };
            typeChoiceBox.setValue(taskType);
            subscription = typeChoiceBox.getSelectionModel().selectedItemProperty().subscribe((oldItem, newItem) -> updateType(oldItem, newItem, true)).and(subscription);
            updateType(null, taskType, false);

            dueTimeSpinner.getValueFactory().setValue(task.getDueTime());
            subscription = dueTimeSpinner.valueProperty().subscribe(newValue -> task.dueTimeProperty().set(newValue)).and(subscription);
        }
        else {
            updateType(null, null, false);
        }
    }

    private void updateType(String oldValue, String newValue, boolean overrideInterval) {
        if (oldValue == null || !oldValue.equals(newValue)) {

            typeSubs.unsubscribe();
            typeSubs = Subscription.EMPTY;

            contentVBox.getChildren().clear();

            if (task == null) {
                contentVBox.getChildren().add(blankLabel);
            }
            else {
                contentVBox.getChildren().addAll(namePane, typePane, dueTimePane);

                switch (newValue) {
                    case "No Due Date" -> {
                        if (overrideInterval) {
                            task.intervalProperty().set(new NoInterval());
                        }
                    }
                    case "Single Date" -> {
                        contentVBox.getChildren().add(2, dueDatePane);
                        if (overrideInterval) {
                            task.intervalProperty().set(new SingleDateInterval(TaskTwig.today()));
                        }

                        SingleDateInterval interval = (SingleDateInterval) task.getInterval();
                        dueDatePicker.setValue(interval.getDueDate());
                        typeSubs = dueDatePicker.valueProperty().subscribe(date -> interval.dueDateProperty().set(date));
                    }
                    case "Day Interval" -> {
                        contentVBox.getChildren().add(2, dayIntervalPane);

                        if (overrideInterval) {
                            task.intervalProperty().set(new DayInterval(1, false));
                        }

                        DayInterval interval = (DayInterval) task.getInterval();

                        dayIntervalSpinner.getValueFactory().setValue(interval.getInterval());
                        dayIntervalNextDuePicker.setValue(interval.nextDue());
                        dayIntervalRepeatCheckbox.setSelected(interval.isRepeatFromLastDone());

                        typeSubs = dayIntervalSpinner.valueProperty().subscribe(value -> interval.intervalProperty().set(value)).and(typeSubs);
                        typeSubs = dayIntervalRepeatCheckbox.selectedProperty().subscribe(value -> interval.repeatFromLastDoneProperty().set(value)).and(typeSubs);
                        typeSubs = dayIntervalNextDuePicker.valueProperty().subscribe(date -> {
                            System.out.println("Updating next due to: " + date);
                            if (date != null)
                                interval.nextDueProperty().set(date);
                        }).and(typeSubs);
                    }
                    case "Week Interval" -> {
                        contentVBox.getChildren().add(2, dayOfWeekPane);

                        if (overrideInterval) {
                            task.intervalProperty().set(new WeekInterval());
                        }

                        WeekInterval interval = (WeekInterval) task.getInterval();

                        dayMButton.setSelected(interval.isDueOn(DayOfWeek.MONDAY));
                        dayTButton.setSelected(interval.isDueOn(DayOfWeek.TUESDAY));
                        dayWButton.setSelected(interval.isDueOn(DayOfWeek.WEDNESDAY));
                        dayThButton.setSelected(interval.isDueOn(DayOfWeek.THURSDAY));
                        dayFButton.setSelected(interval.isDueOn(DayOfWeek.FRIDAY));
                        daySaButton.setSelected(interval.isDueOn(DayOfWeek.SATURDAY));
                        daySuButton.setSelected(interval.isDueOn(DayOfWeek.SUNDAY));

                        dayMButton.setOnAction(event -> interval.setDueOn(DayOfWeek.MONDAY, dayMButton.isSelected()));
                        dayTButton.setOnAction(event -> interval.setDueOn(DayOfWeek.TUESDAY, dayTButton.isSelected()));
                        dayWButton.setOnAction(event -> interval.setDueOn(DayOfWeek.WEDNESDAY, dayWButton.isSelected()));
                        dayThButton.setOnAction(event -> interval.setDueOn(DayOfWeek.THURSDAY, dayThButton.isSelected()));
                        dayFButton.setOnAction(event -> interval.setDueOn(DayOfWeek.FRIDAY, dayFButton.isSelected()));
                        daySaButton.setOnAction(event -> interval.setDueOn(DayOfWeek.SATURDAY, daySaButton.isSelected()));
                        daySuButton.setOnAction(event -> interval.setDueOn(DayOfWeek.SUNDAY, daySuButton.isSelected()));
                    }
                    case "Month Interval" -> {
                        contentVBox.getChildren().add(2, dateOfMonthPane);

                        if (overrideInterval) {
                            task.intervalProperty().set(new MonthInterval());
                        }

                        MonthInterval interval = (MonthInterval) task.getInterval();
                        dateOfMonthField.setText(datesToString(interval.getDates()));
                        typeSubs = dateOfMonthField.textProperty().subscribe(dates -> interval.getDatesObservable().setAll(stringToDates(dates)));
                    }
                    default -> {
                    }
                }
            }
        }
    }

    private String datesToString(List<Integer> dates) {
        if (dates.isEmpty()) {
            return "";
        }

        StringBuilder dateStr = new StringBuilder();

        for (Integer date : dates) {
            dateStr.append(date);
            dateStr.append(", ");
        }

        return dateStr.substring(0, dateStr.length() - 2);
    }

    private List<Integer> stringToDates(String datesStr) {
        if (datesStr == null || datesStr.isEmpty()) {
            return new ArrayList<>();
        }

        String[] inputText = datesStr.split(",");
        List<Integer> dates = new ArrayList<>();

        for (String date : inputText) {
            dates.add(Integer.parseInt(date.strip()));
        }

        return dates;
    }
}
