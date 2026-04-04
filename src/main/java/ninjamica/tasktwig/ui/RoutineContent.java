package ninjamica.tasktwig.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.util.Subscription;
import ninjamica.tasktwig.Routine;
import ninjamica.tasktwig.RoutineInterval.DailyInterval;
import ninjamica.tasktwig.RoutineInterval.DayInterval;
import ninjamica.tasktwig.RoutineInterval.WeekInterval;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class RoutineContent extends AnchorPane {
    @FXML private VBox contentVBox;
    @FXML private Label blankLabel;
    @FXML private AnchorPane namePane;
    @FXML private AnchorPane typePane;
    @FXML private AnchorPane dayOfWeekPane;
    @FXML private AnchorPane dayIntervalPane;
    @FXML private AnchorPane dueTimePane;

    @FXML private TextField nameTextField;
    @FXML private ChoiceBox<String> typeChoiceBox;
    @FXML private Spinner<Integer> dayIntervalSpinner;
    @FXML private CheckBox dayIntervalRepeatCheckbox;
    @FXML private DatePicker dayIntervalNextDuePicker;
    @FXML private ToggleButton dayMButton;
    @FXML private ToggleButton dayTButton;
    @FXML private ToggleButton dayWButton;
    @FXML private ToggleButton dayThButton;
    @FXML private ToggleButton dayFButton;
    @FXML private ToggleButton daySaButton;
    @FXML private ToggleButton daySuButton;
    @FXML private Spinner<LocalTime> dueTimeSpinner;

    private final static ObservableList<String> types = FXCollections.observableArrayList("Daily", "Day Interval", "Week Interval");
    private Subscription subscriptions = Subscription.EMPTY;
    private Subscription typeSubs = Subscription.EMPTY;
    Routine routine;

    public RoutineContent() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("fxml/routine-dialog.fxml"));
            loader.setController(this);
            getChildren().add(loader.load());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        updateType(null, null, false);
    }

    public RoutineContent(Routine routine) {
        this();
        setRoutine(routine);
    }

    @FXML
    protected void initialize() {
        typeChoiceBox.setItems(types);
        dayIntervalSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, Integer.MAX_VALUE, 1));
        new TimeSpinner(dueTimeSpinner, null);
    }

    public void setRoutine(Routine routine) {
        this.routine = routine;
        subscriptions.unsubscribe();
        subscriptions = Subscription.EMPTY;

        if (routine != null) {
            nameTextField.textProperty().bindBidirectional(routine.name());
            subscriptions = subscriptions.and(() -> nameTextField.textProperty().unbindBidirectional(routine.name()));

            String routineType = switch (routine.getInterval()) {
                case DailyInterval daily -> routineType = "Daily";
                case DayInterval day -> routineType = "Day Interval";
                case WeekInterval week -> routineType = "Week Interval";
                default -> routineType = null;
            };
            typeChoiceBox.setValue(routineType);
            subscriptions = typeChoiceBox.getSelectionModel().selectedItemProperty().subscribe((oldItem, newItem) -> updateType(oldItem, newItem, true)).and(subscriptions);
            updateType(null,  routineType, false);

            dueTimeSpinner.getValueFactory().setValue(routine.getDueTime());
            subscriptions = dueTimeSpinner.valueProperty().subscribe(newValue -> routine.dueTime().set(newValue)).and(subscriptions);
        }
        else {
            updateType(null, null, false);
        }
    }

    private void updateType(String oldValue, String newValue, boolean overrideInterval) {
        System.out.println("updateType: oldValue=" + oldValue + " newValue=" + newValue);
        if (oldValue == null || !oldValue.equals(newValue)) {

            typeSubs.unsubscribe();
            typeSubs = Subscription.EMPTY;

            contentVBox.getChildren().clear();

            if (routine == null) {
                contentVBox.getChildren().add(blankLabel);
            }
            else {
                contentVBox.getChildren().addAll(namePane, typePane, dueTimePane);

                switch (newValue) {
                    case "Daily" -> {
                        if (overrideInterval)
                            routine.interval().set(new DailyInterval());
                    }
                    case "Day Interval" -> {
                        System.out.println("Day Interval");
                        contentVBox.getChildren().add(2, dayIntervalPane);

                        if (overrideInterval) {
                            System.out.println("creating new DayInterval");
                            routine.interval().set(new DayInterval(1, false));
                        }
                        System.out.println("Before interval cast");

                        DayInterval interval = (DayInterval) routine.getInterval();
                        System.out.println(interval);

                        dayIntervalSpinner.getValueFactory().setValue(interval.getInterval());
                        dayIntervalRepeatCheckbox.setSelected(interval.isRepeatFromLastDone());
                        dayIntervalNextDuePicker.setValue(interval.getNextDue());

                        typeSubs = dayIntervalSpinner.valueProperty().subscribe(value -> interval.intervalProperty().set(value)).and(typeSubs);
                        typeSubs = dayIntervalRepeatCheckbox.selectedProperty().subscribe(value -> interval.repeatFromLastDoneProperty().set(value)).and(typeSubs);
                        typeSubs = dayIntervalNextDuePicker.valueProperty().subscribe(date -> interval.setNextDue(date)).and(typeSubs);
                    }
                    case "Week Interval" -> {
                        contentVBox.getChildren().add(2, dayOfWeekPane);

                        if (overrideInterval) {
                            routine.interval().set(new WeekInterval());
                        }

                        WeekInterval interval = (WeekInterval) routine.getInterval();

                        dayMButton.setSelected(interval.isIntervalOn(DayOfWeek.MONDAY));
                        dayTButton.setSelected(interval.isIntervalOn(DayOfWeek.TUESDAY));
                        dayWButton.setSelected(interval.isIntervalOn(DayOfWeek.WEDNESDAY));
                        dayThButton.setSelected(interval.isIntervalOn(DayOfWeek.THURSDAY));
                        dayFButton.setSelected(interval.isIntervalOn(DayOfWeek.FRIDAY));
                        daySaButton.setSelected(interval.isIntervalOn(DayOfWeek.SATURDAY));
                        daySuButton.setSelected(interval.isIntervalOn(DayOfWeek.SUNDAY));

                        dayMButton.setOnAction(event -> interval.setOnDay(DayOfWeek.MONDAY, dayMButton.isSelected()));
                        dayTButton.setOnAction(event -> interval.setOnDay(DayOfWeek.TUESDAY, dayTButton.isSelected()));
                        dayWButton.setOnAction(event -> interval.setOnDay(DayOfWeek.WEDNESDAY, dayWButton.isSelected()));
                        dayThButton.setOnAction(event -> interval.setOnDay(DayOfWeek.THURSDAY, dayThButton.isSelected()));
                        dayFButton.setOnAction(event -> interval.setOnDay(DayOfWeek.FRIDAY, dayFButton.isSelected()));
                        daySaButton.setOnAction(event -> interval.setOnDay(DayOfWeek.SATURDAY, daySaButton.isSelected()));
                        daySuButton.setOnAction(event -> interval.setOnDay(DayOfWeek.SUNDAY, daySuButton.isSelected()));
                    }
                    default -> {
                    }
                }
            }
        }
        System.out.println("Finished updateType");
    }
}
