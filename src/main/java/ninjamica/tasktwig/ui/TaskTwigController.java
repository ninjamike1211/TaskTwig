package ninjamica.tasktwig.ui;

import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.StringConverter;
import ninjamica.tasktwig.*;
import ninjamica.tasktwig.task.*;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class TaskTwigController {

    @FXML
    private VBox sleepVBox;
    @FXML
    private Button sleepButton;
    @FXML
    private Label sleepStatusLabel;
    @FXML
    private TableView<Sleep> sleepTableView;
    @FXML
    private TableColumn<Sleep, LocalDate> sleepDateCol;
    @FXML
    private TableColumn<Sleep, LocalTime> sleepStartCol;
    @FXML
    private TableColumn<Sleep, LocalTime> sleepEndCol;
    @FXML
    private TableColumn<Sleep, Float> sleepLengthCol;
    @FXML
    private BorderPane sleepLenChartPane;
    @FXML
    private BorderPane sleepTimeChartPane;
    @FXML
    private LineChart<String, Float> sleepTimeChart;
    @FXML
    private AreaChart<String, Float> sleepLenChart;
    @FXML
    private NumberAxis  sleepTimeNumAxis;
    @FXML
    private Button workoutButton;
    @FXML
    private Label workoutStatusLabel;
    @FXML
    private TableView<Workout> workoutTableView;
    @FXML
    private TableColumn<Workout, String> workoutDateCol;
    @FXML
    private TableColumn<Workout, Float> workoutLengthCol;
    @FXML
    private TableColumn<Workout, String> workoutExerciseCol;
    @FXML
    private TableView<Task> taskTableView;
    @FXML
    private TableColumn<Task, Boolean> taskDoneCol;
    @FXML
    private TableColumn<Task, String> taskNameCol;
    @FXML
    private TableColumn<Task, String> taskDateTimeCol;
    @FXML
    private TableColumn<Task, String> taskRepeatCol;
    @FXML
    private TreeView<Object> listTree;

    private final TaskTwig twig = new TaskTwig();
    private Stage stage;
    private final XYChart.Series<String, Float> sleepLenChartData = new XYChart.Series<>();
    private final XYChart.Series<String, Float> sleepStartChartData = new XYChart.Series<>();
    private final XYChart.Series<String, Float> sleepEndChartData = new XYChart.Series<>();

    private static final String darkStylesheet = TaskTwigController.class.getResource("fxml/dark-theme.css").toExternalForm();
    private static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("EEE M/d/yyyy");
    private static final DateTimeFormatter shortDateFormat = DateTimeFormatter.ofPattern("M/d");
    private static final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("h:mm a");
    private static final String[] dayOfWeekShorthand = {"M", "T", "W", "Th", "F", "Sa", "Su"};
    private static final LocalTime timeChartRefTime = LocalTime.of(12, 00);

    @FXML
    public void initialize() {

        sleepVBox.getChildren().remove(sleepTimeChartPane);

        sleepDateCol.setCellValueFactory(sleep -> new SimpleObjectProperty<>(sleep.getValue().end().toLocalDate().minusDays(1)));
        sleepStartCol.setCellValueFactory(sleep -> new SimpleObjectProperty<>(sleep.getValue().start().toLocalTime()));
        sleepEndCol.setCellValueFactory(sleep -> new SimpleObjectProperty<>(sleep.getValue().end().toLocalTime()));
        sleepLengthCol.setCellValueFactory(sleep -> new SimpleFloatProperty(sleep.getValue().length().toMinutes() / 60f).asObject());

        sleepDateCol.setCellFactory(col -> new TableCell<Sleep, LocalDate>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null)
                    setText(null);
                else
                    setText(dateFormat.format(item));
            }
        });
        Callback<TableColumn<Sleep, LocalTime>, TableCell<Sleep, LocalTime>> timeCellFactory = col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null)
                    setText(null);
                else
                    setText(timeFormat.format(item));
            }
        };
        sleepStartCol.setCellFactory(timeCellFactory);
        sleepEndCol.setCellFactory(timeCellFactory);
        sleepTableView.setItems(FXCollections.observableList(new ArrayList<>(twig.sleepRecords().values())));

        sleepTableView.getSortOrder().add(sleepDateCol);
        sleepTimeNumAxis.setTickLabelFormatter(new StringConverter<Number>() {
            @Override
            public String toString(Number num) {
                LocalTime time = LocalTime.of(12,0).minusMinutes((long)(num.floatValue() * 60));
                return time.format(timeFormat);
            }

            @Override
            public Number fromString(String time) {
                LocalTime localTime = LocalTime.parse(time, timeFormat);
                return localTime.until(timeChartRefTime, ChronoUnit.MINUTES) / 60f;
            }
        });
        sleepStartChartData.setName("Sleep Start");
        sleepEndChartData.setName("SleepEnd");  
        refillSleepCharts();

        workoutDateCol.setCellValueFactory(workout -> new SimpleStringProperty(workout.getValue().start().toLocalDate().format(dateFormat)));
        workoutLengthCol.setCellValueFactory(workout -> new SimpleFloatProperty(workout.getValue().length().toSeconds() / 60f).asObject());
        workoutExerciseCol.setCellValueFactory(workout -> genWorkoutExercises(workout.getValue().exercises()));

        workoutTableView.setItems(twig.workoutRecords());

        setSleepStatusLabel();
        setWorkoutStatusLabel();

        taskDoneCol.setCellValueFactory(task -> new SimpleBooleanProperty(task.getValue().isDone()));
        taskDoneCol.setCellFactory(col -> new TaskDoneCell());

        taskNameCol.setCellValueFactory(task -> new SimpleStringProperty(task.getValue().name()));
        taskDateTimeCol.setCellValueFactory(task -> {
            if(task.getValue().nextDueDate() != null) {
                String strVal = task.getValue().nextDueDate().format(dateFormat);

                if (task.getValue().dueTime() != null) {
                    strVal += " " + task.getValue().dueTime().format(timeFormat);
                }

                return new SimpleStringProperty(strVal);
            }
            else {
                return  new SimpleStringProperty("");
            }
        });
        taskRepeatCol.setCellValueFactory(task -> {
            switch (task.getValue()) {
                case DailyTask dailyTask -> {
                    return new SimpleStringProperty("Daily");
                }
                case WeeklyTask weeklyTask -> {
                    StringBuilder dayString = new StringBuilder();
                    for (DayOfWeek day : weeklyTask.getDaysOfWeek()) {
                        dayString.append(dayOfWeekShorthand[day.ordinal()]).append(" ");
                    }
                    dayString.deleteCharAt(dayString.length() - 1);
                    return new SimpleStringProperty(dayString.toString());
                }
                case MonthlyTask monthlyTask -> {
                    StringBuilder monthString = new StringBuilder();
                    for (int day : monthlyTask.getDueDays()) {
                        monthString.append(day).append(", ");
                    }
                    monthString.delete(monthString.length() - 2, monthString.length());
                    return new SimpleStringProperty(monthString.toString());
                }
                case null, default -> {
                    return new SimpleStringProperty("");
                }
            }
        });
        taskTableView.setRowFactory(table -> {
            TableRow<Task> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && row.getIndex() != -1) {
                    if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                        TaskDialog dialog = new TaskDialog(stage, row.getItem());
                        Optional<TaskDialog.TaskReturn> taskResult = dialog.showAndWait();
                        taskResult.ifPresent(task -> {
                            int index = taskTableView.getSelectionModel().getSelectedIndex();

                            if (task.type().equals(ButtonBar.ButtonData.OTHER)) {
                                Alert confirmDialog = createAlert(Alert.AlertType.WARNING, "Delete Task", "Do you want to delete this task?", "This cannot be undone.");

                                confirmDialog.showAndWait().ifPresent(result -> {
                                    if (result == ButtonType.YES) {
                                        taskTableView.getItems().remove(index);
                                        taskTableView.refresh();
                                    }
                                });
                            }
                            else if (task.task() != null){
                                taskTableView.getItems().set(index, task.task());
                                taskTableView.refresh();
                            }
                        });
                    }
                }
            });
            return row;
        });

        taskTableView.setItems(twig.taskList());

//        listTree.setCellFactory(treeView -> );
        listTree.setRoot(new TreeItem<>());
        listTree.setShowRoot(false);
        listTree.getRoot().setExpanded(true);
        listTree.setCellFactory(treeView -> new TreeCell<>() {
            @Override
            public void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setContextMenu(null);
                    setText(null);
                    setGraphic(null);
                }
                else {
                    ContextMenu contextMenu = new ContextMenu();

                    switch (item) {
                        case TwigList list -> {
                            setText(list.getName());
                            setGraphic(null);
                            getTreeItem().expandedProperty().bindBidirectional(list.expanded());

                            MenuItem deleteItem = new MenuItem("Delete");
                            MenuItem addItem = new MenuItem("Add");

                            deleteItem.setOnAction(event -> {
                                getTreeItem().getParent().getChildren().remove(getTreeItem());
                                twig.twigLists().remove(list);
                            });
                            addItem.setOnAction(event -> {
                                TextInputDialog dialog = new TextInputDialog();
                                dialog.setTitle("Add item to " + list.getName());
                                dialog.setHeaderText("Add item to " + list.getName());
                                dialog.showAndWait().ifPresent(result -> {
                                    TwigList.TwigListItem newItem = new TwigList.TwigListItem(result);
                                    list.items().add(newItem);
                                    getTreeItem().getChildren().add(new TreeItem<>(newItem));
                                });
                            });

                            contextMenu.getItems().addAll(deleteItem, addItem);
                        }
                        case TwigList.TwigListItem listItem -> {
                            Label label = new Label();
                            label.textProperty().bind(listItem.name());

                            CheckBox checkBox = new CheckBox();
                            checkBox.selectedProperty().bindBidirectional(listItem.done());
                            label.disableProperty().bind(checkBox.selectedProperty());

                            setText(null);
                            setGraphic(new HBox(checkBox, label));

                            MenuItem deleteItem = new MenuItem("Delete");

                            TwigList list = (TwigList)(getTreeItem().getParent().getValue());
                            deleteItem.setOnAction(event -> {
                                getTreeItem().getParent().getChildren().remove(getTreeItem());
                                list.items().remove(listItem);
                            });
                            contextMenu.getItems().addAll(deleteItem);
                        }
                        default -> {
                            setText(null);
                            setGraphic(null);
                            return;
                        }
                    }

                    setContextMenu(contextMenu);
                }
            }
        });
        MenuItem addItem = new MenuItem("Create New List");
        addItem.setOnAction(event -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Create New List");
            dialog.setHeaderText("Enter title for new list:");
            dialog.showAndWait().ifPresent(result -> {
                TwigList newList = new TwigList(result);
                twig.twigLists().add(newList);
                listTree.getRoot().getChildren().add(new TreeItem<>(newList));
            });
        });
        listTree.setContextMenu(new ContextMenu(addItem));
        populateTwigLists();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void closeTwig() {
        twig.saveToFile();
    }

    private void populateTwigLists() {
        listTree.getRoot().getChildren().clear();
        for (TwigList list : twig.twigLists()) {
            listTree.getRoot().getChildren().add(constructListTree(list));

        }
    }

    private TreeItem<Object> constructListTree(TwigList list) {
        TreeItem<Object> treeItem = new TreeItem<>(list);

        for (TwigList.TwigListItem item : list.items()) {
            treeItem.getChildren().add(new TreeItem<>(item));
        }

        return  treeItem;
    }

    @FXML
    private void showSleepLenChart() {
        sleepVBox.getChildren().remove(sleepTimeChartPane);
        sleepVBox.getChildren().add(sleepLenChartPane);
    }
    
    @FXML
    private void showSleepTimeChart() {
        sleepVBox.getChildren().remove(sleepLenChartPane);
        sleepVBox.getChildren().add(sleepTimeChartPane);
    }

    private float convertTimeToChartNum(LocalTime time) {
        float midnightRefTime = time.getHour() + (time.getMinute()/60f);

        if(time.getHour() >= 18) {
            return 12f + (24f - midnightRefTime);
        }
        else {
            return 12f - midnightRefTime;
        }
    }

    private void refillSleepTable() {
        sleepTableView.setItems(FXCollections.observableList(new ArrayList<>(twig.sleepRecords().values())));
        sleepTableView.refresh();
//        sleepTableView.getSortOrder().clear();
//        sleepTableView.getSortOrder().add(sleepDateCol);
        refillSleepCharts();
    }

    private void refillSleepCharts() {
        sleepLenChartData.getData().clear();
        sleepStartChartData.getData().clear();
        sleepEndChartData.getData().clear();
        for (Map.Entry<LocalDate, Sleep> entry : twig.sleepRecords().entrySet()) {
            String date = entry.getKey().format(shortDateFormat);
            sleepLenChartData.getData().add(new XYChart.Data<>(date, entry.getValue().length().toMinutes()/60f));
            sleepStartChartData.getData().add(new XYChart.Data<>(date, convertTimeToChartNum(entry.getValue().start().toLocalTime())));
            sleepEndChartData.getData().add(new XYChart.Data<>(date, convertTimeToChartNum(entry.getValue().end().toLocalTime())));
        }
        sleepLenChart.getData().clear();
        sleepTimeChart.getData().clear();
        sleepLenChart.getData().add(sleepLenChartData);
        sleepTimeChart.getData().addAll(sleepStartChartData, sleepEndChartData);
    }

    private ObservableValue<String> genWorkoutExercises(Map<Exercise, Integer> exercises) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<Exercise, Integer> entry : exercises.entrySet()) {
            if (!builder.isEmpty())
                builder.append("\n");

            builder.append(entry.getKey().name())
                   .append(": ")
                   .append(entry.getValue())
                   .append(" ")
                   .append(entry.getKey().unit().displayName);
        }

        return new SimpleStringProperty(builder.toString());
    }

    private void setSleepStatusLabel() {
        if (twig.isSleeping()) {
            sleepButton.setText("Finish");
            sleepStatusLabel.setText("Status: sleeping, started "+ twig.sleepStart().format(timeFormat));
        }
        else {
            sleepButton.setText("Start");
            sleepStatusLabel.setText("Status: not sleeping");
        }
    }

    @FXML
    protected void onSleepButtonAction(ActionEvent event) throws IOException {
        if(!twig.isSleeping()) {
            TimeDateDialog dialog = new TimeDateDialog(stage, "Bed");
            Optional<LocalDateTime> timeResult = dialog.showAndWait();

            if(timeResult.isPresent()) {
                LocalDateTime startTime = timeResult.get();
                twig.startSleep(startTime);
                setSleepStatusLabel();
            }
        }
        else {
            TimeDateDialog dialog = new TimeDateDialog(stage, "Wake-Up");
            Optional<LocalDateTime> timeResult = dialog.showAndWait();

            if(timeResult.isPresent()) {
                LocalDateTime finishTime = timeResult.get();

                LocalDate lastNight = finishTime.toLocalDate().minusDays(1);
                if (twig.sleepRecords().containsKey(lastNight)) {
                    Alert confirmation = createAlert(
                            Alert.AlertType.CONFIRMATION, "Overwrite Sleep Record?",
                            "Overwrite Sleep Record?",
                            "An existing sleep record was found for "+lastNight.format(dateFormat)+"\nDo you want to overwrite this record?");
                    Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);

                    Optional<ButtonType> result = confirmDialog.showAndWait();
                    if (result.isPresent() && result.get() != ButtonType.YES) {
                        return;
                    }
                }

                twig.finishSleep(finishTime);
                setSleepStatusLabel();
                refillSleepTable();
            }
        }
    }

    @FXML
    protected void onSleepButtonClick(MouseEvent event) throws IOException {
        if(twig.isSleeping() && event.getButton() == MouseButton.SECONDARY) {
            Alert confirmDialog = createAlert(Alert.AlertType.CONFIRMATION, "Cancel Sleep Record?", "Do you want to cancel this sleep record?", "");

            Optional<ButtonType> result = confirmDialog.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.YES) {
                twig.startSleep(null);
                setSleepStatusLabel();
            }
        }
    }

    private void setWorkoutStatusLabel() {
        if (twig.isWorkingOut()) {
            workoutButton.setText("Finish");
            workoutStatusLabel.setText("Status: working out, started "+twig.workoutStart().format(timeFormat));
        }
        else {
            workoutButton.setText("Start");
            workoutStatusLabel.setText("Status: not working out");
        }
    }

    @FXML
    protected void onWorkoutButtonAction(ActionEvent event) throws IOException {
        if(!twig.isWorkingOut()) {
            TimeDateDialog dialog = new TimeDateDialog(stage, "Workout");
            Optional<LocalDateTime> timeResult = dialog.showAndWait();

            if(timeResult.isPresent()) {
                LocalDateTime startTime = timeResult.get();
                twig.startWorkout(startTime);
                setWorkoutStatusLabel();
            }
        }
        else {
            TimeDateDialog timeDialog = new TimeDateDialog(stage, "Workout");
            Optional<LocalDateTime> timeResult = timeDialog.showAndWait();

            if(timeResult.isPresent()) {
                LocalDateTime finishTime = timeResult.get();

                Map<Exercise, Integer> exercises;
                WorkoutDialog workoutDialog = new WorkoutDialog(stage, twig);
                workoutDialog.showAndWait();
                exercises = workoutDialog.getResult();

                if (exercises != null) {
                    twig.finishWorkout(exercises, finishTime);
                    setWorkoutStatusLabel();

                    workoutTableView.refresh();
                }
            }
        }
    }

    @FXML
    protected void onWorkoutButtonClick(MouseEvent event) throws IOException {
        if(twig.isWorkingOut() && event.getButton() == MouseButton.SECONDARY) {
            Alert confirmDialog = createAlert(Alert.AlertType.CONFIRMATION, "Cancel Workout?", "Do you want to cancel this workout?", "");

            Optional<ButtonType> result = confirmDialog.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.YES) {
                twig.startWorkout(null);
                setWorkoutStatusLabel();
            }
        }
    }

    @FXML
    protected void addExerciseButtonClick(ActionEvent event) throws IOException {
        ExerciseDialog dialog = new ExerciseDialog(stage, twig.getExerciseList());
        Optional<List<Exercise>> exerciseResult = dialog.showAndWait();
        exerciseResult.ifPresent(exerciseList -> twig.exerciseList().setAll(exerciseList));
    }

    @FXML
    protected void onNewTaskButtonClick(ActionEvent event) throws IOException {
        TaskDialog dialog = new TaskDialog(stage);
        Optional<TaskDialog.TaskReturn> taskResult = dialog.showAndWait();
        taskResult.ifPresent(task -> {
            if (task.task() != null) {
                taskTableView.getItems().add(task.task());
            }
        });
    }

    private Alert createAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.initOwner(stage);
        alert.getDialogPane().getStylesheets().add(darkStylesheet);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

        return alert;
    }
}
