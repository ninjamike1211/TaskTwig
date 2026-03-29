package ninjamica.tasktwig.ui;

import com.dropbox.core.DbxException;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.input.*;
import javafx.scene.layout.Background;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Callback;
import javafx.util.Duration;
import javafx.util.StringConverter;
import javafx.util.Subscription;
import ninjamica.tasktwig.*;
import ninjamica.tasktwig.TaskTwig.CommitDiff;
import ninjamica.tasktwig.TaskTwig.FileAction;
import ninjamica.tasktwig.TwigList.TwigListItem;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.ToggleSwitch;
import org.controlsfx.glyphfont.Glyph;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class TaskTwigController {

    @FXML private Pane rootPane;

    @FXML private ListView<Routine> todayRoutineListView;
    @FXML private TreeView<Task> todayTaskTreeView;
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

    @FXML private TreeTableView<Task> taskTreeTable;
    @FXML private TreeTableColumn<Task, Task> taskNameCol;
    @FXML private TreeTableColumn<Task, TaskInterval> taskDateTimeCol;
    @FXML private TreeTableColumn<Task, TaskInterval> taskRepeatCol;
    @FXML private TreeView<Object> listTree;

    @FXML private TableView<Routine> routineTable;
    @FXML private TableColumn<Routine, String> routineNameCol;
    @FXML private TableColumn<Routine, RoutineInterval> routineIntervalCol;
    @FXML private TableColumn<Routine, LocalTime> routineDueCol;
    @FXML private Button routineButton;

    @FXML private ListView<LocalDate> journalListView;
    @FXML private TextArea journalTextArea;
    @FXML private ListView<String> journalRoutineList;
    @FXML private ListView<String> journalTaskList;

    @FXML private Spinner<LocalTime> settingsDayStartSpinner;
    @FXML private Spinner<LocalTime> settingsNightStartSpinner;
    @FXML private Spinner<Integer> settingsIntervalSpinner;
    @FXML private ToggleSwitch settingsAutoSyncToggle;
    @FXML private Label settingsDbxName;
    @FXML private Button settingsDbxButton;

    @FXML private Button syncButton;
    @FXML private Label syncLabel;
    
    private static final String darkStylesheet = TaskTwigController.class.getResource("fxml/dark-theme.css").toExternalForm();
    private static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("EEE M/d/yyyy");
    private static final DateTimeFormatter shortDateFormat = DateTimeFormatter.ofPattern("M/d");
    private static final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("h:mm a");
    private static final String[] dayOfWeekShorthand = {"M", "T", "W", "Th", "F", "Sa", "Su"};
    private static final LocalTime timeChartRefTime = LocalTime.of(12, 00);
    private static final DataFormat DRAG_DROP_MIME_FORMAT = new DataFormat("application/x-java-serialized-object");

    private final TaskTwig twig = new TaskTwig();
    private Application application;
    private Stage stage;
    private Subscription subscriptions = Subscription.EMPTY;
    private SaveSyncService backgroundService;

    private final XYChart.Series<String, Float> sleepLenChartData = new XYChart.Series<>();
    private final XYChart.Series<String, Float> sleepStartChartData = new XYChart.Series<>();
    private final XYChart.Series<String, Float> sleepEndChartData = new XYChart.Series<>();


    @FXML
    public void initialize() {

        rootPane.setDisable(true);

        todayRoutineListView.setCellFactory(col -> new ListCell<>() {
            private final CheckBox checkBox = new CheckBox();
            private final Text name = new Text();
            private final Text dueText = new Text();
            private final HBox pane = new HBox(7);
            {
                setStyle("-fx-background: transparent");
                checkBox.setFocusTraversable(false);
                dueText.setStyle("-fx-fill: #a1a1a1");

                setOnMouseEntered(event -> {name.setUnderline(true); dueText.setUnderline(true);});
                setOnMouseExited(event -> {name.setUnderline(false); dueText.setUnderline(false);});

                EventHandler<MouseEvent> setDoneCallback = event -> {
                    var item = getItem();
                    if (item != null) {
                        item.setDone(!item.isDoneToday());
                        updateFormatting(item);
                    }
                };
                setOnMouseClicked(setDoneCallback);
                checkBox.setOnMouseClicked(setDoneCallback);

                pane.getChildren().addAll(checkBox, name, dueText);
            }

            @Override
            protected void updateItem(Routine item, boolean empty) {
                super.updateItem(item, empty);

                setText(null);
                setBackground(Background.EMPTY);

                name.textProperty().unbind();
                dueText.textProperty().unbind();

                if (item == null || empty) {
                    setGraphic(null);
                }
                else {
                    name.textProperty().bind(item.name());
                    dueText.textProperty().bind(item.dueTime().map(time -> {
                        if (time == null)
                            return "";
                        else
                            return "by " + timeFormat.format(time);
                    }));

                    updateFormatting(item);
                    setGraphic(pane);
                }
            }

            private void updateFormatting(Routine item) {
                boolean done = item.isDoneToday();
                checkBox.setSelected(done);

                if (done) {
                    name.setStyle("-fx-fill: #909090; -fx-strikethrough: true");
                    pane.getChildren().remove(dueText);
                }
                else {
                    name.setStyle("-fx-fill: lightgray");
                    if (!pane.getChildren().contains(dueText))
                        pane.getChildren().add(dueText);
                }
            }
        });

        todayTaskTreeView.setCellFactory(treeView -> new TreeCell<>() {
            private Subscription sub = Subscription.EMPTY;

            private final Text name = new Text();
            private final Text dueText = new Text();
            private final HBox pane = new HBox(7);
            private final DoneCheckBox checkBox = new DoneCheckBox(done -> {
                if (getItem() != null && !isEmpty()) {
                    getItem().setDone(done);
                }
            });

            {
                checkBox.setFocusTraversable(false);
                checkBox.selectedProperty().subscribe(selected -> updateFormatting(getItem()));

                setOnMouseEntered(event -> { name.setUnderline(true); dueText.setUnderline(true); });
                setOnMouseExited(event -> { name.setUnderline(false); dueText.setUnderline(false); });
                setOnMouseClicked(event -> {if (getItem() != null) getItem().toggleDone();});

                pane.getChildren().addAll(checkBox, name, dueText);
            }

            @Override
            protected void updateItem(Task item, boolean empty) {
                super.updateItem(item, empty);

                setText(null);
                setBackground(Background.EMPTY);
                name.textProperty().unbind();
                checkBox.selectedProperty().unbind();

                sub.unsubscribe();
                sub = Subscription.EMPTY;

                if (item == null || empty) {
                    setGraphic(null);
                }
                else {
                    name.textProperty().bind(item.nameProperty());
                    checkBox.selectedProperty().bind(item.doneObservable());
                    updateFormatting(item);
                    setGraphic(pane);

                    sub = Subscription.combine(
                        item.dueTimeProperty().subscribe(() -> updateFormatting(item)),
                        item.nextDueObservable().subscribe(() -> updateFormatting(item))
                    );
                }
            }

            private void updateFormatting(Task item) {
                if (item != null) {
                    if (item.isDone()) {
                        name.setStyle("-fx-fill: #909090; -fx-strikethrough: true");
                        dueText.setText(null);
                    } else {
                        name.setStyle("-fx-fill: lightgray");
                        if (item.getInterval().isOverdue()) {
                            dueText.setStyle("-fx-fill: #ff0000");
                            dueText.textProperty().unbind();
                            dueText.setText("Overdue!");
                        } else {
                            dueText.setStyle("-fx-fill: #a1a1a1");
                            if (item.getInterval().nextDue() != null) {
                                if (item.getDueTime() != null)
                                    dueText.setText(shortDateFormat.format(item.getInterval().nextDue()) + " at " + timeFormat.format(item.getDueTime()));
                                else
                                    dueText.setText(shortDateFormat.format(item.getInterval().nextDue()));
                            } else {
                                dueText.setText(null);
                            }
                        }
                    }
                }
            }
        });
        todayTaskTreeView.setRoot(new TreeItem<Task>(new Task("rootTask", new TaskInterval.NoInterval())));

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

        sleepTimeNumAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number num) {
                LocalTime time = LocalTime.of(12, 0).minusMinutes((long) (num.floatValue() * 60));
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
        sleepVBox.getChildren().remove(sleepTimeChartPane);

        workoutDateCol.setCellValueFactory(workout -> new SimpleStringProperty(workout.getValue().start().toLocalDate().format(dateFormat)));
        workoutLengthCol.setCellValueFactory(workout -> new SimpleFloatProperty(workout.getValue().length().toSeconds() / 60f).asObject());
        workoutExerciseCol.setCellValueFactory(workout -> genWorkoutExercises(workout.getValue().exercises()));

//        taskDoneCol.setCellValueFactory(task -> new SimpleBooleanProperty(task.getValue().getValue().isDone()));
//        taskDoneCol.setCellFactory(col -> new TaskDoneCell());
        taskNameCol.setCellValueFactory(task -> task.getValue().valueProperty());
        taskNameCol.setCellFactory(col -> new TreeTableCell<>() {

            private final DoneCheckBox checkBox = new DoneCheckBox(done -> {
                if (getItem() != null && !isEmpty()) {
                    getItem().setDone(done);
                }
            });

            @Override
            protected void updateItem(Task item, boolean empty) {
                super.updateItem(item, empty);

                textProperty().unbind();
                checkBox.selectedProperty().unbind();

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                }
                else {
                    textProperty().bind(item.nameProperty());
                    checkBox.selectedProperty().bind(item.doneObservable());
                    setGraphic(checkBox);
                }
            }
        });
        taskDateTimeCol.setCellFactory(column -> new TreeTableCell<>() {
            private Subscription sub = Subscription.EMPTY;

            @Override
            protected void updateItem(TaskInterval item, boolean empty) {
                super.updateItem(item, empty);

                sub.unsubscribe();
                sub = Subscription.EMPTY;

                if (empty || item == null)
                    setText(null);

                else {
                    Task task = getTableRow().getItem();
                    setDateTimeText(task);
                    sub = task.dueTimeProperty().subscribe(dueTime -> setDateTimeText(task)).and(sub);
                    sub = item.nextDueObservable().subscribe(nextDue -> setDateTimeText(task)).and(sub);
                }
            }

            private void setDateTimeText(Task task) {
                if (task.getInterval().nextDue() == null) {
                    setText(null);
                }
                else {
                    String strVal = dateFormat.format(task.getInterval().nextDue());

                    if (task.getDueTime() != null) {
                        strVal += " " + timeFormat.format(task.getDueTime());
                    }

                    setText(strVal);
                }
            }
        });
        taskDateTimeCol.setCellValueFactory(cell -> cell.getValue().getValue().intervalProperty());
        taskRepeatCol.setCellFactory(column -> new TreeTableCell<>() {
            private Subscription itemSubs = Subscription.EMPTY;

            @Override
            protected void updateItem(TaskInterval item, boolean empty) {
                super.updateItem(item, empty);

                itemSubs.unsubscribe();
                itemSubs = Subscription.EMPTY;

                if (empty || item == null)
                    setText(null);

                else {
                    switch (item) {
                        case TaskInterval.SingleDateInterval single -> {
                            setText(dateFormat.format(single.getDueDate()));
                            itemSubs = single.dueDateProperty().subscribe(dueDate -> setText(dateFormat.format(dueDate))).and(itemSubs);
                        }
                        case TaskInterval.DayInterval day -> {
                            setDayText(day);
                            itemSubs = day.intervalProperty().subscribe(newValue -> setDayText(day)).and(itemSubs);
                            itemSubs = day.repeatFromLastDoneProperty().subscribe(newValue -> setDayText(day)).and(itemSubs);
                        }
                        case TaskInterval.WeekInterval week -> {
                            setWeekText(week);
                            itemSubs = week.dayOfWeekMapProperty().subscribe(newValue -> setWeekText(week)).and(itemSubs);
                        }
                        case TaskInterval.MonthInterval month -> {
                            setMonthText(month);
                            ListChangeListener<Integer> listener = change -> setMonthText(month);
                            month.getDatesObservable().addListener(listener);
                            itemSubs = itemSubs.and(() -> month.getDatesObservable().removeListener(listener));
                        }
                        default -> setText(null);
                    }
                }
            }

            private void setDayText(TaskInterval.DayInterval day) {
                if (day.isRepeatFromLastDone())
                    setText("Every " + day.getInterval() + " days after done");
                else
                    setText("Every " + day.getInterval() + " days");
            }

            private void setWeekText(TaskInterval.WeekInterval week) {
                StringBuilder retVal = new StringBuilder("weekly:");
                for (DayOfWeek day : DayOfWeek.values()) {
                    if (week.isDueOn(day)) {
                        retVal.append(" ").append(dayOfWeekShorthand[day.ordinal()]);
                    }
                }
                setText(retVal.toString());
            }

            private void setMonthText(TaskInterval.MonthInterval month) {
                if (month.getDates().isEmpty()) {
                    setText(null);
                } else {
                    StringBuilder dateStr = new StringBuilder();

                    for (Integer date : month.getDates()) {
                        dateStr.append(date);
                        dateStr.append(", ");
                    }

                    setText(dateStr.substring(0, dateStr.length() - 2));
                }
            }
        });
        taskRepeatCol.setCellValueFactory(cell -> cell.getValue().getValue().intervalProperty());
        taskTreeTable.setRowFactory(table -> new TreeTableRow<>() {
            private final ContextMenu contextMenu;
            {
                setOnMouseClicked(event -> {
                    if (!isEmpty() && getItem() != null) {
                        if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                            Task task = getTreeItem().getValue();
                            Node content = new TaskContent(task);
                            PopOver popOver = new PopOver(content);
                            popOver.titleProperty().bind(task.nameProperty());
                            popOver.setArrowLocation(PopOver.ArrowLocation.TOP_LEFT);
                            popOver.getRoot().getStylesheets().clear();
                            popOver.show(this);
                        }
                    }
                });
                MenuItem deleteItem = new MenuItem("Delete");
                deleteItem.setOnAction(event -> {
                    if (getTreeItem().getParent() == taskTreeTable.getRoot())
                        twig.taskList().remove(getItem());
                    else
                        getTreeItem().getParent().getValue().getChildren().remove(getItem());
                });

                MenuItem addItem = new MenuItem("Add subtask");
                addItem.setOnAction(event -> addSubtask(getTreeItem()));

                contextMenu = new ContextMenu(deleteItem, addItem);
            }

            @Override
            protected void updateItem(Task task, boolean empty) {
                super.updateItem(task, empty);

                if (empty || task == null) {
                    setContextMenu(null);
                }
                else {
                    setContextMenu(contextMenu);
                }
            }
        });
        taskTreeTable.setRoot(new TreeItem<>(new Task("placeholder", new TaskInterval.NoInterval())));

        listTree.setRoot(new TreeItem<>());
        listTree.getRoot().setExpanded(true);
        listTree.setCellFactory(treeView -> new TreeCell<>() {
            private Subscription subscription = Subscription.EMPTY;

            @Override
            public void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);

                subscription.unsubscribe();
                subscription = Subscription.EMPTY;

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
                            getTreeItem().setExpanded(list.isExpanded());
                            subscription = getTreeItem().expandedProperty().subscribe(expanded -> list.expanded().set(expanded)).and(subscription);

                            setOnMouseEntered(event -> setStyle("-fx-underline:true"));
                            setOnMouseExited(event -> setStyle("-fx-underline:false"));
                            setOnMouseClicked(event -> {
                                if (event.getButton() == MouseButton.PRIMARY) {
                                    getTreeItem().setExpanded(!getTreeItem().isExpanded());
                                }
                            });

                            MenuItem addItem = new MenuItem("Add");
                            MenuItem clearDoneItem = new MenuItem("Clear Checked");
                            MenuItem deleteItem = new MenuItem("Delete");

                            addItem.setOnAction(event -> {
                                TextInputDialog dialog = new TextInputDialog();
                                dialog.setTitle("Add item to " + list.getName());
                                dialog.setHeaderText("Add item to " + list.getName());
                                dialog.showAndWait().ifPresent(result -> list.items().add(new TwigListItem(result)));
                            });
                            clearDoneItem.setOnAction(event -> {
                                for (int i = 0; i < list.items().size(); i++) {
                                    if (list.items().get(i).isDone()) {
                                        list.items().remove(i--);
                                    }
                                }
                            });
                            deleteItem.setOnAction(event -> {
                                getTreeItem().getParent().getChildren().remove(getTreeItem());
                                twig.twigLists().remove(list);
                            });
                            contextMenu.getItems().addAll(addItem, clearDoneItem, deleteItem);
                        }
                        case TwigListItem listItem -> {
                            Label label = new Label();
                            subscription = listItem.name().subscribe(name -> label.setText(name)).and(subscription);

                            CheckBox checkBox = new CheckBox();
                            checkBox.setSelected(listItem.isDone());
                            subscription = checkBox.selectedProperty().subscribe(selected -> listItem.done().set(selected)).and(subscription);
                            label.disableProperty().bind(checkBox.selectedProperty());

                            setOnMouseEntered(event -> label.setUnderline(true));
                            setOnMouseExited(event -> label.setUnderline(false));

                            setOnMouseClicked(event -> {
                                if (event.getButton() == MouseButton.PRIMARY) {
                                    checkBox.fire();
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


        routineTable.setRowFactory(table -> {
            TableRow<Routine> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && row.getIndex() != -1) {
                    if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                        Routine routine = table.getItems().get(row.getIndex());
                        Node content = new RoutineContent(routine);
                        PopOver popOver = new PopOver(content);
                        popOver.titleProperty().bind(routine.name());
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
        routineDueCol.setCellValueFactory(routine -> routine.getValue().dueTime());
        routineIntervalCol.setCellFactory(col -> new TableCell<>() {
            private Subscription itemSubs = Subscription.EMPTY;

            @Override
            protected void updateItem(RoutineInterval item, boolean empty) {
                super.updateItem(item, empty);

                itemSubs.unsubscribe();
                itemSubs = Subscription.EMPTY;

                if (item == null || empty) {
                    setText(null);
                }
                else {
                    switch (item) {
                        case RoutineInterval.DailyInterval daily -> setText("Daily");
                        case RoutineInterval.DayInterval day -> {
                            itemSubs = day.intervalProperty().subscribe(newValue -> setDayText(day)).and(itemSubs);
                            itemSubs = day.repeatFromLastDoneProperty().subscribe(newValue -> setDayText(day)).and(itemSubs);
                        }
                        case RoutineInterval.WeekInterval week -> {
                            setWeekText(week);
                            itemSubs = week.dayOfWeekBitmapProperty().subscribe(newValue -> setWeekText(week)).and(itemSubs);
                        }
                        default -> setText(null);
                    }
                }
            }

            private void setWeekText(RoutineInterval.WeekInterval week) {
                StringBuilder retVal = new StringBuilder("weekly:");
                for (DayOfWeek day : DayOfWeek.values()) {
                    if (week.isIntervalOn(day)) {
                        retVal.append(" ").append(dayOfWeekShorthand[day.ordinal()]);
                    }
                }
                setText(retVal.toString());
            }

            private void setDayText(RoutineInterval.DayInterval day) {
                if (day.isRepeatFromLastDone())
                    setText("Every " + day.getInterval() + " days after done");
                else
                    setText("Every " + day.getInterval() + " days");
            }
        });
        routineDueCol.setCellFactory(column -> new timeTableCell<>(timeFormat) {});

        journalListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setText(null);
                }
                else {
                    setText(dateFormat.format(item));
                }
            }
        });
        journalListView.getSelectionModel().selectedItemProperty().subscribe((oldValue, newValue) -> {
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
        
        journalRoutineList.setSelectionModel(null);
        journalTaskList.setSelectionModel(null);

        backgroundService = new SaveSyncService(this);

        new TimeSpinner(settingsDayStartSpinner, null);
        new TimeSpinner(settingsNightStartSpinner, null);

        settingsIntervalSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 30*60));
        settingsIntervalSpinner.getValueFactory().valueProperty().subscribe(interval ->
                backgroundService.setPeriod(Duration.seconds(interval)));

        settingsAutoSyncToggle.selectedProperty().subscribe((autoSync) -> twig.autoSyncProperty().set(autoSync));


        twig.dbxClient().subscribe(_ -> updateDbxAccountState());
        updateDbxAccountState();

        Thread twigInitThread = new Thread(() -> {
            twig.authDbxFromFile();
            Platform.runLater(() -> {
                rootPane.setDisable(false);
                attachTwigData();
            });
        });
        twigInitThread.start();
    }

    private void attachTwigData() {
        Journal todaysJournal = twig.todaysJournal();
        todayJournalTextArea.textProperty().bindBidirectional(todaysJournal.textProperty());
        subscriptions = subscriptions.and(() -> todayJournalTextArea.textProperty().unbindBidirectional(todaysJournal.textProperty()));

        todayRoutineListView.setItems(twig.routineList().filtered(item -> item.getInterval().isToday()));
        subscriptions = subscriptions.and(() -> todayRoutineListView.setItems(null));

        var filteredTasks = FXCollections.observableList(twig.taskList(), task -> new Observable[] {task.inProgressObservable()}).filtered(Task::inProgress);
        populateTaskTree(todayTaskTreeView.getRoot(), filteredTasks);

        Runnable updateDayNight = () -> {
            updateTodaySleepPane();
        };
        subscriptions = twig.getDayStart().subscribe(updateDayNight).and(subscriptions);
        subscriptions = twig.getNightStart().subscribe(updateDayNight).and(subscriptions);

        subscriptions = twig.sleepStart().subscribe(this::setSleepStatusLabel).and(subscriptions);
        setSleepStatusLabel(twig.sleepStart().getValue());

        MapChangeListener<LocalDate, Sleep> sleepChangeListener = change -> { refillSleepTable(); refillSleepCharts(); };
        twig.sleepRecords().addListener(sleepChangeListener);
        subscriptions = subscriptions.and(() -> twig.sleepRecords().removeListener(sleepChangeListener));
        refillSleepTable();
        refillSleepCharts();

        subscriptions = twig.workoutStart().subscribe(this::setWorkoutStatusLabel).and(subscriptions);
        setWorkoutStatusLabel(twig.workoutStart().getValue());
        workoutTableView.setItems(twig.workoutRecords());
        subscriptions = subscriptions.and(() -> workoutTableView.setItems(null));

//        taskTableView.setItems(twig.taskList().sorted((task1, task2) -> {
//            if (task1.isDone() ^ task2.isDone()) {
//                return task1.isDone() ? 1 : -1;
//            }
//            else {
//                return task1.getName().compareTo(task2.getName());
//            }
//        }));

        populateTaskTree(taskTreeTable.getRoot(), twig.taskList());
        populateTwigLists();

        routineTable.setItems(twig.routineList());
        subscriptions = subscriptions.and(() -> routineTable.setItems(null));

        MapChangeListener<LocalDate, Journal> journalChangeListener = change -> {
            journalListView.setItems(FXCollections.observableArrayList(twig.journalMap().keySet()).sorted(Comparator.reverseOrder()));
        };
        twig.journalMap().addListener(journalChangeListener);
        subscriptions = subscriptions.and(() -> twig.journalMap().removeListener(journalChangeListener));

        journalListView.setItems(FXCollections.observableArrayList(twig.journalMap().keySet()).sorted(Comparator.reverseOrder()));
        subscriptions = subscriptions.and(() -> journalListView.setItems(null));

        settingsDayStartSpinner.getValueFactory().setValue(twig.getDayStart().getValue());
        settingsNightStartSpinner.getValueFactory().setValue(twig.getNightStart().getValue());
        settingsIntervalSpinner.getValueFactory().setValue(twig.syncIntervalProperty().getValue());

        subscriptions = settingsDayStartSpinner.getValueFactory().valueProperty().subscribe(twig::setDayStart).and(subscriptions);
        subscriptions = settingsNightStartSpinner.getValueFactory().valueProperty().subscribe(twig::setNightStart).and(subscriptions);
        subscriptions = settingsIntervalSpinner.getValueFactory().valueProperty().subscribe(twig.syncIntervalProperty()::setValue).and(subscriptions);

//        backgroundThread.setDaemon(true);
//        backgroundThread.start();
        System.out.println("Service period: " + backgroundService.getPeriod());
        backgroundService.restart();
    }

    private void detachTwigData() {
        subscriptions.unsubscribe();
        subscriptions = Subscription.EMPTY;

        if (backgroundService.getState() != Worker.State.RUNNING)
            backgroundService.cancel();
//        else {
//            backgroundService.setOnScheduled(event -> {
//                backgroundService.cancel();
//                backgroundService.setOnScheduled(null);
//            });
//        }

//        backgroundThread.interrupt();
    }

    public void setApplication(Application application) {
        this.application = application;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void closeTwig(WindowEvent event) {
        event.consume();
//        runSyncAndExit();
        backgroundService.syncAndExit();
    }

    private void populateTaskTree(TreeItem<Task> root, ObservableList<Task> topLevelTasks) {
        root.getChildren().clear();
        for (Task task : topLevelTasks) {
            root.getChildren().add(constructTaskTree(task));
        }

        ListChangeListener<Task> listener = change -> handleTaskItemChange(change, root);
        topLevelTasks.addListener(listener);

        subscriptions = Subscription.combine(
                () -> root.getChildren().clear(),
                () -> topLevelTasks.removeListener(listener)
        ).and(subscriptions);
    }

    private TreeItem<Task> constructTaskTree(Task task) {
        TreeItem<Task> treeItem = new TreeItem<>(task);
        treeItem.setExpanded(true);

        for (Task subTask : task.getChildren()) {
            treeItem.getChildren().add(constructTaskTree(subTask));
        }

        ListChangeListener<Task> listener = change -> handleTaskItemChange(change, treeItem);
        task.getChildren().addListener(listener);

        subscriptions = subscriptions.and(() -> task.getChildren().removeListener(listener));

        return treeItem;
    }

    private void handleTaskItemChange(ListChangeListener.Change<? extends Task> change, TreeItem<Task> parent) {
        System.out.println("Tasks change: " + change.toString());

        ObservableList<TreeItem<Task>> treeChildren = parent.getChildren();
        while (change.next()) {
            if (change.wasPermutated()) {
                for (int i = change.getFrom(); i < change.getTo(); ++i) {
                    TreeItem<Task> cell = treeChildren.remove(i);
                    treeChildren.add(change.getPermutation(i), cell);
                }
            }
            else if (!change.wasUpdated()) {

                if (change.wasRemoved()) {
                    treeChildren.remove(change.getFrom(), change.getTo()+1);
                }
                if (change.wasAdded()) {
                    for (int i = change.getFrom(); i < change.getTo(); i++) {
                        treeChildren.add(i, constructTaskTree(change.getList().get(i)));
                    }
                }
            }
        }
    }

    private void populateTwigLists() {
        listTree.getRoot().getChildren().clear();
        for (TwigList list : twig.twigLists()) {
            listTree.getRoot().getChildren().add(constructListTree(list));

        }

        subscriptions = subscriptions.and(listTree.getRoot().getChildren()::clear);
    }

    private TreeItem<Object> constructListTree(TwigList list) {
        TreeItem<Object> treeItem = new TreeItem<>(list);

        for (TwigListItem item : list.items()) {
            treeItem.getChildren().add(new TreeItem<>(item));
        }

        ListChangeListener<TwigListItem> changeListener = change -> handleListItemChange(change, treeItem);
        list.items().addListener(changeListener);
        subscriptions = subscriptions.and(() -> list.items().removeListener(changeListener));

        return treeItem;
    }

    private void handleListItemChange(ListChangeListener.Change<? extends TwigList.TwigListItem> change, TreeItem<Object> parent) {
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

    private void updateTodaySleepPane() {
        if (TaskTwig.isNight()) {
            if (twig.isSleeping()) {
                todaySleepButton.setText("Sleeping");
                todaySleepButton.setDisable(true);
            }
            else {
                todaySleepButton.setText("Go To Bed");
                todaySleepButton.setDisable(false);
            }
        }
        else {
            if (twig.isSleeping()) {
                todaySleepButton.setText("Wake Up");
                todaySleepButton.setDisable(false);
            }
            else {
                todaySleepButton.setText("Awake");
                todaySleepButton.setDisable(true);
            }
        }
    }

    private void setSleepStatusLabel(LocalDateTime time) {
        if (time != null) {
            sleepButton.setText("Finish");
            sleepStatusLabel.setText("Status: sleeping, started "+ time.format(timeFormat));
        }
        else {
            sleepButton.setText("Start");
            sleepStatusLabel.setText("Status: not sleeping");
        }

        updateTodaySleepPane();
    }

    @FXML
    protected void onSleepButtonAction(ActionEvent event) {
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
    protected void onSleepButtonClick(MouseEvent event) {
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
    protected void onWorkoutButtonAction(ActionEvent event) {
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

                SortedMap<Exercise, Integer> exercises;
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
    protected void onWorkoutButtonClick(MouseEvent event) {
        if(twig.isWorkingOut() && event.getButton() == MouseButton.SECONDARY) {
            Alert confirmDialog = createAlert(Alert.AlertType.CONFIRMATION, "Cancel Workout?", "Do you want to cancel this workout?", "");

            Optional<ButtonType> result = confirmDialog.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.YES) {
                twig.startWorkout(null);
            }
        }
    }

    @FXML
    protected void addExerciseButtonClick(ActionEvent event) {
        ExerciseDialog dialog = new ExerciseDialog(stage, twig.getExerciseList());
        Optional<List<Exercise>> exerciseResult = dialog.showAndWait();
        exerciseResult.ifPresent(exerciseList -> twig.exerciseList().setAll(exerciseList));
    }

    @FXML
    protected void onNewTaskButtonClick(ActionEvent event) {
        TaskDialog dialog = new TaskDialog(stage);
        Optional<Task> taskResult = dialog.showAndWait();
        taskResult.ifPresent(task -> twig.taskList().add(task));
    }

    private void addSubtask(TreeItem<Task> parent) {
        TaskDialog dialog = new TaskDialog(stage);
        Optional<Task> taskResult = dialog.showAndWait();
        taskResult.ifPresent(task -> parent.getValue().getChildren().add(task));
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
        routineResult.ifPresent(routine -> twig.routineList().add(routine));
    }

    private void updateDbxAccountState() {
        if (twig.dbxClient().getValue() == null) {
            syncButton.setDisable(true);
            syncLabel.setText("No Dropbox account connected");

            settingsDbxName.setText("No active account");
            settingsDbxButton.setText("Connect Account");
        }
        else {
            syncButton.setDisable(false);
            syncLabel.setText("Not yet synced");

            settingsDbxButton.setText("Logout");
            try {
                settingsDbxName.setText(twig.getDbxAccountName());
            }
            catch (DbxException e) {
                System.err.println("Failed to render dbx account name: " + e.getMessage());
            }
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
                detachTwigData();
                twig.authDbxFromCode(code);
                onDbxButton();
            }
            catch (DbxException e) {
                new Alert(Alert.AlertType.ERROR, "Error Authenticating, code not accepted. Make sure you've entered the code properly and try again.", ButtonType.OK).showAndWait();
            }
            finally {
                attachTwigData();
            }
        });
    }

    public void onDbxButton() {
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

    private TaskTwig.FileAction handleDataConflict() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setHeaderText("Conflicting data between local and remote!");
        alert.setContentText("Would you like to keep the local data or the remote (Dropbox) data?");
        alert.initOwner(stage);

        ButtonType remoteButton = new ButtonType("Remote", ButtonBar.ButtonData.NO);
        ButtonType localButton = new ButtonType("Local", ButtonBar.ButtonData.YES);
        alert.getButtonTypes().setAll(localButton, remoteButton, ButtonType.CANCEL);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == remoteButton)
                return TaskTwig.FileAction.DOWNLOAD;
            else if (result.get() == localButton)
                return TaskTwig.FileAction.UPLOAD;
            else
                return TaskTwig.FileAction.NONE;
        }

        return TaskTwig.FileAction.NONE;
    }

    private boolean userConfirmSave() {
        Alert alert = new Alert(AlertType.CONFIRMATION, "Some data is saved locally but not synced with Dropbox, would you like to sync before exiting?", ButtonType.YES, ButtonType.NO);
        alert.setHeaderText("Sync data before exiting?");
        alert.initOwner(stage);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            return true;
        }

        return false;
    }

    @FXML
    protected void onSyncButton() {
        System.out.println("Sync button pressed");
        backgroundService.syncNow();
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

    private static class SaveSyncService extends ScheduledService<Void> {

        private final TaskTwigController controller;
        private final TaskTwig twig;
        private volatile boolean syncOverrideFlag = false;
        private volatile boolean exitPromptFlag = false;
        private volatile boolean exitPromptAsked = false;
        private final Glyph loadIcon;
        private final RotateTransition loadIconAnimation;

        private SaveSyncService(TaskTwigController controller) {
            super();
            this.controller = controller;
            this.twig = controller.twig;

            loadIcon = new Glyph("FontAwesome", "REFRESH");
            loadIconAnimation = new RotateTransition(Duration.seconds(1), loadIcon);
            loadIconAnimation.setByAngle(360);
            loadIconAnimation.setDelay(Duration.ZERO);
            loadIconAnimation.setCycleCount(Animation.INDEFINITE);
            loadIconAnimation.setInterpolator(Interpolator.LINEAR);
        }

        public void forceSync() {
            syncOverrideFlag = true;
        }

        public void syncNow() {
            setDelay(Duration.ZERO);
            forceSync();
            restart();
        }

        public void syncAndExit() {
            setOnSucceeded(event -> Platform.exit());
            setOnFailed(event -> Platform.exit());
            exitPromptFlag = true;
            restart();
        }

        @Override
        protected javafx.concurrent.Task<Void> createTask() {
            final boolean exitPrompt = exitPromptFlag;
            final boolean forceSync = syncOverrideFlag;
            syncOverrideFlag = false;

            System.out.println("Creating task with forceSync: " + forceSync);

            return new javafx.concurrent.Task<>() {
                @Override
                protected Void call() throws Exception {
                    setStartUI();

                    boolean syncToDbx = forceSync || CompletableFuture.supplyAsync(twig.autoSyncProperty()::get, Platform::runLater).join();
                    System.out.println("Running task with sync: " + syncToDbx);

                    updateMessage("Saving and hashing data");
                    twig.saveToFileFX();

                    if (twig.dbxClient().getValue() != null) {
                        updateMessage("Comparing data with Dropbox");

                        CommitDiff commitDiff;
                        if (syncToDbx) {
                            commitDiff = twig.compareCommitToDbx(controller::handleDataConflict);
                        }
                        else if (exitPrompt) {
                            System.out.println("Checking sync with exitPrompt");
                            commitDiff = twig.compareCommitToDbx(() -> {
                                System.out.println("exitPrompt callback");
                                exitPromptAsked = true;
                                boolean sync = controller.userConfirmSave();
                                System.out.println("exitPrompt result: " + sync);
                                if (sync)
                                    return controller.handleDataConflict();
                                else
                                    return null;
                            });

                            if (commitDiff.action() != null && commitDiff.action() != FileAction.NONE){
                                syncToDbx = exitPromptAsked
                                    || CompletableFuture.supplyAsync(controller::userConfirmSave, Platform::runLater).join();
                            }
                        }
                        else {
                            commitDiff= twig.compareCommitToDbx(null);
                        }

                        if (syncToDbx) {
                            String labelText;
                            switch (commitDiff.action()) {
                                case UPLOAD -> labelText = "Uploading data to Dropbox";
                                case DOWNLOAD -> {
                                    labelText = "Downloading data from Dropbox";
                                    CompletableFuture.runAsync(controller::detachTwigData, Platform::runLater).join();
                                }
                                case NONE -> labelText = "In sync as of " + LocalTime.now().format(timeFormat);
                                default -> labelText = "";
                            }
                            updateMessage(labelText);
                            twig.dbxSync(commitDiff);

                            String finishSyncText;
                            switch (commitDiff.action()) {
                                case UPLOAD ->
                                        finishSyncText = "Synced to remote at " + LocalTime.now().format(timeFormat);
                                case DOWNLOAD -> {
                                    finishSyncText = "Synced from remote at " + LocalTime.now().format(timeFormat);
                                    Platform.runLater(controller::attachTwigData);
                                }
                                case NONE -> finishSyncText = labelText;
                                default -> finishSyncText = "";
                            }
                            updateMessage(finishSyncText);
                        }
                        else {
                            String syncCompareText = switch(commitDiff.action()) {
                                case TaskTwig.FileAction.NONE -> "In sync as of " + LocalTime.now().format(timeFormat);
                                case TaskTwig.FileAction.UPLOAD -> "Ahead of remote";
                                case TaskTwig.FileAction.DOWNLOAD -> "Behind remote";
                                case null -> "local/remote conflict (press sync to resolve)";
                            };

                            updateMessage(syncCompareText);
                        }
                    }
                    else {
                        updateMessage("Data saved to file");
                    }

                    setDoneUI();
                    System.out.println("Finished task");
                    return null;
                }

//                @Override
//                protected void done() {
//                    setDoneUI();
//                }

                @Override
                protected void failed() {
                    System.out.println("Failed task");
                    System.out.println(getException().getMessage());
                    setDoneUI();
                    Platform.runLater(() -> controller.syncLabel.setText("Sync failed"));
                }

                @Override
                protected void cancelled() {
                    System.out.println("Cancelled task");
                    setDoneUI();
                    Platform.runLater(() -> controller.syncLabel.setText("Sync cancelled"));
                }

                private void setStartUI() {
                    System.out.println("Setting start UI");
                    Platform.runLater(() -> {
                        controller.syncLabel.textProperty().bind(this.messageProperty());
                        controller.syncLabel.setGraphic(loadIcon);
                        loadIconAnimation.play();
                        controller.syncButton.setDisable(true);
                    });
                }

                private void setDoneUI() {
                    System.out.println("Setting Done UI");
                    Platform.runLater(() -> {
                        controller.syncLabel.textProperty().unbind();
                        controller.syncLabel.setGraphic(null);
                        loadIconAnimation.stop();
                        controller.syncButton.setDisable(false);
                    });
                }
            };
        }
    }
}
