package ninjamica.tasktwig.ui;

import com.dropbox.core.DbxException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.Background;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.StringConverter;
import ninjamica.tasktwig.*;
import ninjamica.tasktwig.TwigList.TwigListItem;
import ninjamica.tasktwig.ui.PropertySheetItems.ButtonItem;
import ninjamica.tasktwig.ui.PropertySheetItems.LabelItem;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.PropertySheet;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TaskTwigController {

    @FXML private ListView<Routine> todayRoutineListView;
    @FXML private ListView<Task> todayTaskListView;
    @FXML private Button todaySleepButton;
    @FXML private Button todayExerciseButton;
    @FXML private TextArea todayJournalTextArea;

    @FXML private VBox sleepVBox;
    @FXML private Button sleepButton;
    @FXML private Label sleepStatusLabel;
    @FXML private TableView<Sleep> sleepTableView;
    @FXML private TableColumn<Sleep, LocalDate> sleepDateCol;
    @FXML private TableColumn<Sleep, LocalTime> sleepStartCol;
    @FXML private TableColumn<Sleep, LocalTime> sleepEndCol;
    @FXML private TableColumn<Sleep, Float> sleepLengthCol;
    @FXML private HBox sleepLenChartPane;
    @FXML private HBox sleepTimeChartPane;
    @FXML private LineChart<String, Float> sleepTimeChart;
    @FXML private AreaChart<String, Float> sleepLenChart;
    @FXML private NumberAxis sleepTimeNumAxis;

    @FXML private Button workoutButton;
    @FXML private Label workoutStatusLabel;
    @FXML private TableView<Workout> workoutTableView;
    @FXML private TableColumn<Workout, String> workoutDateCol;
    @FXML private TableColumn<Workout, Float> workoutLengthCol;
    @FXML private TableColumn<Workout, String> workoutExerciseCol;

    @FXML private TableView<Task> taskTableView;
    @FXML private TableColumn<Task, Boolean> taskDoneCol;
    @FXML private TableColumn<Task, String> taskNameCol;
    @FXML private TableColumn<Task, String> taskDateTimeCol;
    @FXML private TableColumn<Task, String> taskRepeatCol;
    @FXML private TreeView<Object> listTree;

    @FXML private TableView<Routine> routineTable;
    @FXML private TableColumn<Routine, String> routineNameCol;
    @FXML private TableColumn<Routine, TwigInterval> routineIntervalCol;
    @FXML private TableColumn<Routine, LocalTime> routineStartCol;
    @FXML private TableColumn<Routine, LocalTime> routineEndCol;
    @FXML private Button routineButton;

    @FXML private ListView<LocalDate> journalListView;
    @FXML private TextArea journalTextArea;
    @FXML private ListView<String> journalRoutineList;
    @FXML private ListView<String> journalTaskList;

    @FXML private PropertySheet settingsSheet;

    @FXML private Button syncButton;
    @FXML private Label syncLabel;

    private final TaskTwig twig = new TaskTwig();
    private Application application;
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
    private static final DataFormat DRAG_DROP_MIME_FORMAT = new DataFormat("application/x-java-serialized-object");

    private final ButtonItem.ButtonItemState dbxButtonState = new ButtonItem.ButtonItemState(new SimpleStringProperty(), new SimpleBooleanProperty(true), this::onDbxButton);

    @FXML
    public void initialize() {

        sleepVBox.getChildren().remove(sleepTimeChartPane);
        if (!twig.journalMap().containsKey(TaskTwig.effectiveDate())) {
            twig.journalMap().put(TaskTwig.effectiveDate(), new Journal());
        }
        todayJournalTextArea.textProperty().bindBidirectional(twig.journalMap().get(TaskTwig.effectiveDate()).textProperty());

        todayRoutineListView.setCellFactory(col -> new ListCell<>() {
            private final CheckBox checkBox = new CheckBox();
            private final Text name = new Text();
            private final Text dueText = new Text();
            private final HBox pane = new HBox(7);
            {
                name.setStyle("-fx-fill: lightgray");
                dueText.setStyle("-fx-fill: #a1a1a1");
                this.setOnMouseEntered(event -> {name.setUnderline(true); dueText.setUnderline(true);});
                this.setOnMouseExited(event -> {name.setUnderline(false); dueText.setUnderline(false);});
//                this.setCursor(Cursor.HAND);

                checkBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
                    this.getItem().setDone(newValue);
                    if (newValue) {
                        name.setStyle("-fx-fill: #909090; -fx-strikethrough: true");
                        dueText.setVisible(false);
                    }
                    else {
                        name.setStyle("-fx-fill: lightgray");
                        dueText.setVisible(true);
                    }
                });

                this.setOnMouseClicked(event -> {checkBox.setSelected(!checkBox.isSelected());});

                pane.getChildren().addAll(checkBox, name, dueText);
            }

            @Override
            protected void updateItem(Routine item, boolean empty) {
                super.updateItem(item, empty);

                setText(null);
                name.textProperty().unbind();
                if (item == null || empty) {
                    setGraphic(null);
                }
                else {
                    name.textProperty().bind(item.name());
                    checkBox.setSelected(item.isDoneToday());

                    if (item.getEnd() != null) {
                        dueText.setText("by " + item.getEnd().format(timeFormat));
                    }
                    else {
                        dueText.setText(null);
                    }

                    setGraphic(pane);
                    setBackground(Background.EMPTY);
                }
            }
        });
        todayRoutineListView.setSelectionModel(null);
        todayRoutineListView.setItems(twig.routineList().filtered(item -> item.getInterval().isToday()));

        todayTaskListView.setCellFactory(col -> new ListCell<Task>() {
            private final CheckBox checkBox = new CheckBox();
            private final Text name = new Text();
            private final Text dueText = new Text();
            private final HBox pane = new HBox(7);
            {
                name.setStyle("-fx-fill: lightgray");
                dueText.setStyle("-fx-fill: #a1a1a1");
                this.setOnMouseEntered(event -> {name.setUnderline(true); dueText.setUnderline(true);});
                this.setOnMouseExited(event -> {name.setUnderline(false); dueText.setUnderline(false);});
//                this.setCursor(Cursor.HAND);

                checkBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
                    this.getItem().setDone(newValue);
                    if (newValue) {
                        name.setStyle("-fx-fill: #909090; -fx-strikethrough: true");
                        dueText.setVisible(false);
                    }
                    else {
                        name.setStyle("-fx-fill: lightgray");
                        dueText.setVisible(true);
                    }
                });

                this.setOnMouseClicked(event -> {
                    if (this.getItem() != null)
                        checkBox.setSelected(!checkBox.isSelected());
                });

                pane.getChildren().addAll(checkBox, name, dueText);
            }

            @Override
            protected void updateItem(Task item, boolean empty) {
                super.updateItem(item, empty);

                setText(null);
                name.textProperty().unbind();
                dueText.textProperty().unbind();

                if (item == null || empty) {
                    setGraphic(null);
                }
                else {
                    name.textProperty().bind(item.name());
                    checkBox.setSelected(item.isDone());

                    if (item.getDueTime() != null) {
                        dueText.setText(item.getInterval().next().format(shortDateFormat) + " at " + item.getDueTime().format(timeFormat));
                    }
                    else {
                        dueText.setText(item.getInterval().next().format(shortDateFormat));
                    }

                    setGraphic(pane);
                    setBackground(Background.EMPTY);
                }
            }
        });
        todayTaskListView.setSelectionModel(null);
        todayTaskListView.setItems(twig.taskList().filtered(item -> {
            if (item.getInterval().next() == null) {
                return false;
            }
            return !TaskTwig.effectiveDate().isAfter(item.getInterval().next());
        }));

        twig.sleepStart().addListener((observable, oldValue, newValue) -> setSleepStatusLabel(newValue));

        sleepDateCol.setCellValueFactory(sleep -> new SimpleObjectProperty<>(sleep.getValue().end().toLocalDate().minusDays(1)));
        sleepStartCol.setCellValueFactory(sleep -> new SimpleObjectProperty<>(sleep.getValue().start().toLocalTime()));
        sleepEndCol.setCellValueFactory(sleep -> new SimpleObjectProperty<>(sleep.getValue().end().toLocalTime()));
        sleepLengthCol.setCellValueFactory(sleep -> new SimpleFloatProperty(sleep.getValue().length().toMinutes() / 60f).asObject());

        sleepDateCol.setCellFactory(col -> new TableCell<>() {
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
        sleepTableView.setItems(FXCollections.observableArrayList(twig.sleepRecords().values()).sorted((sleep1, sleep2) -> sleep2.end().compareTo(sleep1.end())));

        // sleepTableView.getSortOrder().add(sleepDateCol);
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
        twig.sleepRecords().addListener((MapChangeListener<LocalDate, Sleep>) change -> {
            refillSleepTable();
            refillSleepCharts();
        });

        twig.workoutStart().addListener((observable, oldValue, newValue) -> setWorkoutStatusLabel(newValue));

        workoutDateCol.setCellValueFactory(workout -> new SimpleStringProperty(workout.getValue().start().toLocalDate().format(dateFormat)));
        workoutLengthCol.setCellValueFactory(workout -> new SimpleFloatProperty(workout.getValue().length().toSeconds() / 60f).asObject());
        workoutExerciseCol.setCellValueFactory(workout -> genWorkoutExercises(workout.getValue().exercises()));

        workoutTableView.setItems(twig.workoutRecords());

        setSleepStatusLabel(twig.sleepStart().getValue());
        setWorkoutStatusLabel(twig.workoutStart().getValue());

        taskDoneCol.setCellValueFactory(task -> new SimpleBooleanProperty(task.getValue().isDone()));
        taskDoneCol.setCellFactory(col -> new TaskDoneCell());

        taskNameCol.setCellValueFactory(task -> new SimpleStringProperty(task.getValue().getName()));
        taskDateTimeCol.setCellValueFactory(task -> {
            if(task.getValue().getInterval().next() != null) {
                String strVal = task.getValue().getInterval().next().format(dateFormat);

                if (task.getValue().getDueTime() != null) {
                    strVal += " " + task.getValue().getDueTime().format(timeFormat);
                }

                return new SimpleStringProperty(strVal);
            }
            else {
                return  new SimpleStringProperty("");
            }
        });
        taskRepeatCol.setCellValueFactory(task -> {
            switch (task.getValue().getInterval()) {
                case TwigInterval.DailyInterval dailyTask -> {
                    return new SimpleStringProperty("Daily");
                }
                case TwigInterval.WeeklyInterval weeklyTask -> {
                    StringBuilder dayString = new StringBuilder();
                    for (DayOfWeek day : weeklyTask.daysOfWeek()) {
                        dayString.append(dayOfWeekShorthand[day.ordinal()]).append(" ");
                    }
                    dayString.deleteCharAt(dayString.length() - 1);
                    return new SimpleStringProperty(dayString.toString());
                }
                case TwigInterval.MonthlyInterval monthlyTask -> {
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
                    setOnMouseEntered(event -> {});
                    setOnMouseExited(event -> {});
                    setOnMouseClicked(event -> {});
                }
                else {
                    ContextMenu contextMenu = new ContextMenu();

                    switch (item) {
                        case TwigList list -> {
                            setText(list.getName());
                            setGraphic(null);
                            getTreeItem().expandedProperty().bindBidirectional(list.expanded());

                            setOnMouseEntered(event -> setStyle("-fx-underline:true"));
                            setOnMouseExited(event -> setStyle("-fx-underline:false"));
                            setOnMouseClicked(event -> {
                                if (event.getButton() == MouseButton.PRIMARY) {
                                    getTreeItem().setExpanded(!getTreeItem().isExpanded());
                                }
                            });

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
                                    list.items().add(new TwigListItem(result));
                                });
                            });
                            contextMenu.getItems().addAll(deleteItem, addItem);
                        }
                        case TwigListItem listItem -> {
                            Label label = new Label();
                            label.textProperty().bind(listItem.name());

                            CheckBox checkBox = new CheckBox();
                            checkBox.selectedProperty().bindBidirectional(listItem.done());
                            label.disableProperty().bind(checkBox.selectedProperty());

                            setOnMouseEntered(event -> label.setUnderline(true));
                            setOnMouseExited(event -> label.setUnderline(false));

                            setOnMouseClicked(event -> {
                                if (event.getButton() == MouseButton.PRIMARY) {
                                    checkBox.setSelected(!checkBox.isSelected());
                                    event.consume();
                                }
                            });
                            setText(null);
                            setGraphic(new HBox(checkBox, label));

                            MenuItem deleteItem = new MenuItem("Delete");

                            TwigList list = (TwigList)(getTreeItem().getParent().getValue());
                            deleteItem.setOnAction(event -> list.items().remove(listItem));
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
        listTree.addEventFilter(MouseEvent.MOUSE_PRESSED, Event::consume);
        populateTwigLists();


        routineTable.setRowFactory(table -> {
            TableRow<Routine> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
               if (!row.isEmpty() && row.getIndex() != -1) {
                   if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                       Routine routine = table.getItems().get(row.getIndex());
                       Node content = new RoutineContent(routine);
                       PopOver popOver = new PopOver(content);
                       popOver.titleProperty().bindBidirectional(routine.name());
                       popOver.setArrowLocation(PopOver.ArrowLocation.TOP_LEFT);
                       popOver.getRoot().getStylesheets().clear();
                       popOver.show(row);
                   }
               }
            });

            row.setOnDragDetected(event -> {
                if (!row.isEmpty() && row.getIndex() != -1) {
                    Dragboard db = row.startDragAndDrop(TransferMode.MOVE);
                    db.setDragView(row.snapshot(null, null));
                    ClipboardContent cc =  new ClipboardContent();
                    cc.put(DRAG_DROP_MIME_FORMAT, row.getIndex());
                    db.setContent(cc);
                    event.consume();
                }
            });

            row.setOnDragOver(event -> {
                Dragboard db = event.getDragboard();
                if (db.hasContent(DRAG_DROP_MIME_FORMAT)) {
                    if (row.getIndex() != (int)db.getContent(DRAG_DROP_MIME_FORMAT)) {
                        event.acceptTransferModes(TransferMode.MOVE);
                        event.consume();

                        if (!row.isEmpty() || row.getIndex() == table.getItems().size()) {
                            row.setStyle("-fx-border-color: -fx-accent; -fx-border-width: 2 0 0 0");
                        }
                    }
                }
            });

            row.setOnDragExited(event -> {
                row.setStyle("");
                event.consume();
            });

            row.setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                if (db.hasContent(DRAG_DROP_MIME_FORMAT)) {
                    int oldIndex =  (int)db.getContent(DRAG_DROP_MIME_FORMAT);
                    Routine draggedItem = table.getItems().remove(oldIndex);

                    int newIndex;
                    if (row.isEmpty()) {
                        newIndex = table.getItems().size();
                    }
                    else {
                        newIndex = row.getIndex();
                        if (newIndex >= oldIndex) {
                            newIndex--;
                        }
                    }
                    table.getItems().add(newIndex, draggedItem);

                    event.setDropCompleted(true);
                    event.consume();
                }
            });

            MenuItem deleteItem = new MenuItem("Delete");
            deleteItem.setOnAction(event -> table.getItems().remove(row.getIndex()));
            row.setContextMenu(new ContextMenu(deleteItem));

            return row;
        });
        routineNameCol.setCellValueFactory(routine -> routine.getValue().name());
        routineIntervalCol.setCellValueFactory(routine -> routine.getValue().interval());
        routineStartCol.setCellValueFactory(routine -> routine.getValue().startTime());
        routineEndCol.setCellValueFactory(routine -> routine.getValue().endTime());
        routineIntervalCol.setCellFactory(col -> new TableCell<Routine, TwigInterval>() {
            @Override
            protected void updateItem(TwigInterval item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setText(null);
                }
                else {
                    switch (item) {
                        case TwigInterval.DailyInterval daily -> {
                            setText("Daily");
                        }
                        case TwigInterval.WeeklyInterval week -> {
                            setWeekText(week);
                            for (BooleanProperty prop : week.dayOfWeekMapProperty()) {
                                prop.addListener((observable, oldValue, newValue) -> setWeekText(week));
                            }
                        }
                        default -> {
                            setText(null);
                        }
                    }
                }
            }

            private void setWeekText(TwigInterval.WeeklyInterval week) {
                StringBuilder retVal = new StringBuilder("weekly:");
                for (DayOfWeek day : DayOfWeek.values()) {
                    if (week.getDayOfWeekMap()[day.ordinal()]) {
                        retVal.append(" ").append(dayOfWeekShorthand[day.ordinal()]);
                    }
                }
                setText(retVal.toString());
            }
        });

        routineStartCol.setCellFactory(column -> new timeTableCell<>(timeFormat) {});
        routineEndCol.setCellFactory(column -> new timeTableCell<>(timeFormat) {});

        routineTable.setItems(twig.routineList());

        journalListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setText(null);
                }
                else {
                    setText(item.format(dateFormat));
                }
            }
        });
        journalListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue != null && twig.journalMap().containsKey(oldValue)) {
                journalTextArea.textProperty().unbindBidirectional(twig.journalMap().get(oldValue).textProperty());
                journalRoutineList.setItems(null);
                journalTaskList.setItems(null);
            }

            if (newValue != null && twig.journalMap().containsKey(newValue)) {
                Journal journal = twig.journalMap().get(newValue);

                journalRoutineList.setItems(journal.completedRoutines());
                journalTaskList.setItems(journal.completedTasks());
                journalTextArea.textProperty().bindBidirectional(journal.textProperty());
                journalTextArea.setPromptText("Type journal here...");
            }
        });
        twig.journalMap().addListener((MapChangeListener<LocalDate, Journal>) change -> {
            journalListView.setItems(FXCollections.observableArrayList(twig.journalMap().keySet()).sorted((date1, date2) -> date2.compareTo(date1)));
        });
        journalListView.setItems(FXCollections.observableArrayList(twig.journalMap().keySet()).sorted((date1, date2) -> date2.compareTo(date1)));
        journalRoutineList.setSelectionModel(null);
        journalTaskList.setSelectionModel(null);

        twig.dbxClient().addListener((observable, oldValue, newValue) -> updateDbxAccountState());
        updateDbxAccountState();

        settingsSheet.getItems().addAll(
                new LabelItem(twig.dbxName(), "DropBox Account", "DropBox", ""),
                new ButtonItem(dbxButtonState, "Connect DropBox Account", "DropBox", "")
        );

    }

    public void setApplication(Application application) {
        this.application = application;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void closeTwig() {
        twig.saveToFile();
        twig.dbxSync();
    }

    private void populateTwigLists() {
        listTree.getRoot().getChildren().clear();
        for (TwigList list : twig.twigLists()) {
            listTree.getRoot().getChildren().add(constructListTree(list));

        }
    }

    private TreeItem<Object> constructListTree(TwigList list) {
        TreeItem<Object> treeItem = new TreeItem<>(list);

        for (TwigListItem item : list.items()) {
            treeItem.getChildren().add(new TreeItem<>(item));
        }

        list.items().addListener((ListChangeListener.Change<? extends TwigList.TwigListItem> change) -> handleItemChange(change, treeItem));

        return treeItem;
    }

    private void handleItemChange(ListChangeListener.Change<? extends TwigList.TwigListItem> change, TreeItem<Object> parent) {
        ObservableList<TreeItem<Object>> treeChildren = parent.getChildren();
        while (change.next()) {
            if (change.wasPermutated()) {
                for (int i = change.getFrom(); i < change.getTo(); ++i) {
                    TreeItem<Object> cell = treeChildren.remove(i);
                    treeChildren.add(change.getPermutation(i), cell);
                }
            }
            else if (!change.wasUpdated()) {

                if (change.wasRemoved()) {
                    treeChildren.remove(change.getFrom(), change.getTo()+1);
                }
                if (change.wasAdded()) {
                    for (int i = change.getFrom(); i < change.getTo(); i++) {
                        treeChildren.add(i, new TreeItem<>(change.getList().get(i)));
                    }
                }
            }
        }
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
        sleepTableView.setItems(FXCollections.observableList(new ArrayList<>(twig.sleepRecords().values())).sorted((sleep1, sleep2) -> sleep2.end().compareTo(sleep1.end())));
        sleepTableView.refresh();
//        sleepTableView.getSortOrder().clear();
//        sleepTableView.getSortOrder().add(sleepDateCol);
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

    private void setSleepStatusLabel(LocalDateTime time) {
        if (time != null) {
            sleepButton.setText("Finish");
            sleepStatusLabel.setText("Status: sleeping, started "+ time.format(timeFormat));
            
            if (TaskTwig.isNight()) {
                todaySleepButton.setText("Sleeping");
                todaySleepButton.setDisable(true);
            }
            else {
                todaySleepButton.setText("Wake Up");
                todaySleepButton.setDisable(false);
            }
        }
        else {
            sleepButton.setText("Start");
            sleepStatusLabel.setText("Status: not sleeping");

            if (TaskTwig.isNight()) {
                todaySleepButton.setText("Go To Bed");
                todaySleepButton.setDisable(false);
            }
            else {
                todaySleepButton.setText("Awake");
                todaySleepButton.setDisable(true);
            }
        }
    }

    @FXML
    protected void onSleepButtonAction(ActionEvent event) throws IOException {
        if(!twig.isSleeping()) {
            TimeDateDialog dialog = new TimeDateDialog(stage, "Bed");
            Optional<LocalDateTime> timeResult = dialog.showAndWait();

            timeResult.ifPresent(twig::startSleep);
        }
        else {
            TimeDateDialog dialog = new TimeDateDialog(stage, "Wake-Up");
            Optional<LocalDateTime> timeResult = dialog.showAndWait();

            if(timeResult.isPresent()) {
                LocalDateTime finishTime = timeResult.get();

                LocalDate lastNight = finishTime.toLocalDate().minusDays(1);
                if (twig.sleepRecords().containsKey(lastNight)) {
                    Alert confirmDialog = createAlert(
                            Alert.AlertType.CONFIRMATION, "Overwrite Sleep Record?",
                            "Overwrite Sleep Record?",
                            "An existing sleep record was found for "+lastNight.format(dateFormat)+"\nDo you want to overwrite this record?");

                    Optional<ButtonType> result = confirmDialog.showAndWait();
                    if (result.isPresent() && result.get() != ButtonType.YES) {
                        return;
                    }
                }

                twig.finishSleep(finishTime);
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
            }
        }
    }

    private void setWorkoutStatusLabel(LocalDateTime time) {
        if (time != null) {
            workoutButton.setText("Finish");
            todayExerciseButton.setText("Finish");
            workoutStatusLabel.setText("Status: working out, started " + time.format(timeFormat));
        }
        else {
            workoutButton.setText("Start");
            todayExerciseButton.setText("Start");
            workoutStatusLabel.setText("Status: not working out");
        }
    }

    @FXML
    protected void onWorkoutButtonAction(ActionEvent event) throws IOException {
        if(!twig.isWorkingOut()) {
            TimeDateDialog dialog = new TimeDateDialog(stage, "Workout");
            Optional<LocalDateTime> timeResult = dialog.showAndWait();

            timeResult.ifPresent(twig::startWorkout);
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

    @FXML
    protected void createRoutine() {
        RoutineDialog dialog = new RoutineDialog(stage);
        Optional<Routine> routineResult = dialog.showAndWait();
        routineResult.ifPresent(routine -> {
            twig.routineList().add(routine);
        });
    }

    private void updateDbxAccountState() {
        if (twig.dbxClient().getValue() == null) {
            dbxButtonState.text().set("Connect Account");
            syncButton.setDisable(true);
            syncLabel.setText("No Dropbox account connected");
        }
        else {
            dbxButtonState.text().set("Logout");
            syncButton.setDisable(false);
            syncLabel.setText("Not yet synced");
        }
    }

    private void dbxAuthorize() {
        String authUrl = twig.genDbxAuthUrl();

        Dialog<String> dialog = new Dialog<>() {
            {
                setApplication(application);
                getDialogPane().getStyleClass().add("confirmation");
                setHeaderText("Open the following URL and paste the provided code below");

                Hyperlink url = new Hyperlink(authUrl);
                url.setOnAction(event -> application.getHostServices().showDocument(authUrl));
                url.setMaxWidth(600);
                url.setWrapText(true);

                TextField codeInput = new TextField();
                codeInput.setPromptText("Enter code here");

                VBox contentBox = new VBox(url, codeInput);
                contentBox.setAlignment(Pos.TOP_CENTER);
                contentBox.setSpacing(15);

                getDialogPane().setContent(contentBox);
                getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
                setWidth(600);

                setResultConverter(buttonType -> {
                    if (buttonType == ButtonType.OK) {
                        return codeInput.getText();
                    }
                    return null;
                });
            }
        };

        dialog.showAndWait().ifPresent(code -> {
            try {
                twig.authDbxFromCode(code);
            } catch (DbxException e) {
                new Alert(Alert.AlertType.ERROR, "Error Authenticating, code not accepted. Make sure you've entered the code properly.", ButtonType.OK).showAndWait();
            }
        });
    }

    public void onDbxButton(ActionEvent event) {
        if (twig.dbxClient().getValue() == null) {
            dbxAuthorize();
        }
        else {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to log out of your Dropbox account?", ButtonType.YES, ButtonType.NO);
            alert.showAndWait().ifPresent(buttonType -> {
                if (buttonType == ButtonType.YES) {
                    twig.dbxLogout();
                }
            });
        }
    }

    @FXML
    protected void onSyncButton() {
        if (twig.dbxClient().getValue() != null) {
            syncLabel.setText("Saving data");

            var thread = new Thread(() -> {
                twig.saveToFileFX();
                Platform.runLater(() -> syncLabel.setText("Syncing"));

                var result = twig.dbxSync();
                String labelText;
                switch (result) {
                    case UPLOAD -> labelText = "Synced local to remote at " + LocalTime.now().format(timeFormat);
                    case DOWNLOAD -> labelText = "Synced local from remote at " + LocalTime.now().format(timeFormat);
                    case NONE -> labelText = "In sync as of " + LocalTime.now().format(timeFormat);
                    default -> labelText = "";
                }
                Platform.runLater(() -> syncLabel.setText(labelText));
            });
            thread.setDaemon(true);
            thread.start();
        }
    }

    static class timeTableCell<T> extends TableCell<T, LocalTime> {
        private final DateTimeFormatter formatter;
        public timeTableCell(DateTimeFormatter formatter) {
            super();
            this.formatter = formatter;
        }

        @Override
        protected void updateItem(LocalTime item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
            }
            else {
                setText(formatter.format(item));
            }
        }
    }
}
