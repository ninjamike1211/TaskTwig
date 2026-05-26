package ninjamica.tasktwig.ui;

import atlantafx.base.controls.*;
import atlantafx.base.theme.*;
import atlantafx.base.util.Animations;
import com.dropbox.core.DbxException;
import com.gluonhq.attach.browser.BrowserService;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.image.ImageView;
import javafx.scene.input.DataFormat;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import javafx.util.StringConverter;
import javafx.util.Subscription;
import ninjamica.tasktwig.core.*;
import ninjamica.tasktwig.core.TaskTwig.FileAction;
import ninjamica.tasktwig.core.TwigList.TwigListItem;
import ninjamica.tasktwig.ui.util.*;
import org.kordamp.ikonli.fontawesome6.FontAwesomeBrands;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings("OctalInteger")
public class TaskTwigController {

    private final StackPane rootPane;
    private final VBox contentPane;
    private final ModalPane modalPane;

    private ListBox<TaskCategory, TodayTaskCategoryView<TodayTaskBox>> todayTaskCategoryList;
    private TaskCategoryViewBase todayTaskDoneBox;
    private Button todaySleepButton;
    private Button todayExerciseButton;
    private TextArea todayJournalTextArea;
    private Spinner<Float> todayWeightSpinner;

    private Button sleepButton;
    private Label sleepStatusLabel;
    private TableView<Sleep> sleepTableView;
    private LineChart<String, Number> sleepTimeChart;
    private AreaChart<String, Number> sleepLenChart;

    private Button workoutButton;
    private Label workoutStatusLabel;
    private TableView<Workout> workoutTableView;

    private DraggableListBox<TaskCategory, TaskCategoryBox> taskCategoryListBox;

    private ListBox<TaskCategory, TaskCategoryView> taskListBox;

    private TreeView<Object> listTree;

    private ListView<LocalDate> journalListView;
    private TextArea journalTextArea;
    private ListView<String> journalRoutineList;
    private ListView<String> journalTaskList;
    private Spinner<Float> journalWeightSpinner;

    private TimeInput settingsDayStartInput;
    private TimeInput settingsNightStartInput;
    private Spinner<Integer> settingsIntervalSpinner;
    private Label settingsDbxName;
    private ImageView settingsDbxIcon;
    private Button settingsDbxButton;

    private Button syncButton;
    
    // private static final String darkStylesheet = TaskTwigController.class.getResource("css/dark-theme.css").toExternalForm();
    private static final String chartStylesheet = TaskTwigController.class.getResource("css/areaChart.css").toExternalForm();
    static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("EEE M/d/yyyy");
    static final DateTimeFormatter shortDateFormat = DateTimeFormatter.ofPattern("M/d");
    static final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("h:mm a");
    private static final String[] dayOfWeekShorthand = {"M", "T", "W", "Th", "F", "Sa", "Su"};
    private static final LocalTime timeChartRefTime = LocalTime.of(12, 00);
    private static final DataFormat DRAG_DROP_MIME_FORMAT = new DataFormat("application/x-java-serialized-object");

    private static final Theme[] themes = {
            new PrimerLight(),
            new PrimerDark(),
            new NordLight(),
            new NordDark(),
            new CupertinoLight(),
            new CupertinoDark(),
            new Dracula()
    };

    private final TaskTwig twig;
    private final TaskTwigApplication application;
    private Subscription subscriptions = Subscription.EMPTY;
    private final SaveSyncService saveSyncService;
    private final BrowserService browser = BrowserService.create().orElseThrow();

    private final XYChart.Series<String, Number> sleepLenChartData = new XYChart.Series<>();
    private final XYChart.Series<String, Number> sleepStartChartData = new XYChart.Series<>();
    private final XYChart.Series<String, Number> sleepEndChartData = new XYChart.Series<>();


    public TaskTwigController(TaskTwigApplication application, TaskTwig taskTwig) {
        this.application = application;
        this.twig = taskTwig;
        saveSyncService = new SaveSyncService(this, this::handleDataConflict, this::userConfirmSave);
        
        ToolBar toolBar = initToolBar();
        TabPane tabPane = new TabPane(
                createTodayTab(),
                createSleepTab(),
                createExerciseTab(),
                createTaskCategoryTab(),
                createTaskTab(),
                createListTab(),
                createJournalTab(),
                createSettingsTab()
        );
        tabPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        
        contentPane = new VBox(tabPane, toolBar);
        contentPane.setPrefSize(1000, 700);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        contentPane.setDisable(true);

        modalPane = new ModalPane();

        rootPane = new StackPane(contentPane, modalPane);

        Thread twigInitThread = new Thread(() -> {
            twig.authDbxFromFile();
            Platform.runLater(() -> {
                contentPane.setDisable(false);
                attachTwigData();
            });
        });
        twigInitThread.start();
    }

    private ToolBar initToolBar() {
        syncButton = new Button();
        syncButton.getStyleClass().addAll(Styles.FLAT);

        syncButton.setOnAction(event -> saveSyncService.syncNow());

        FontIcon syncIcon = new FontIcon(FontAwesomeSolid.SYNC);
        FontIcon saveIcon = new FontIcon(FontAwesomeSolid.SAVE);
        FontIcon uploadIcon = new FontIcon(FontAwesomeSolid.CLOUD_UPLOAD_ALT);
        FontIcon downloadIcon = new FontIcon(FontAwesomeSolid.CLOUD_DOWNLOAD_ALT);
        FontIcon mergeIcon = new FontIcon(FontAwesomeSolid.EXCHANGE_ALT);
        FontIcon conflictIcon = new FontIcon(FontAwesomeSolid.UNLINK);
        FontIcon doneIcon = new FontIcon(FontAwesomeSolid.CHECK);
        FontIcon errorIcon = new FontIcon(FontAwesomeSolid.SKULL);

        RotateTransition syncAnimation = new RotateTransition(Duration.seconds(1), syncIcon);
        syncAnimation.setByAngle(360);
        syncAnimation.setDelay(Duration.ZERO);
        syncAnimation.setCycleCount(Animation.INDEFINITE);
        syncAnimation.setInterpolator(Interpolator.LINEAR);

        Timeline saveAnimation = Animations.shakeY(saveIcon, 5.0);
        saveAnimation.setRate(0.2);
        saveAnimation.setCycleCount(Animation.INDEFINITE);

        Timeline uploadAnimation = Animations.shakeY(uploadIcon, 5.0);
        uploadAnimation.setRate(0.2);
        uploadAnimation.setCycleCount(Animation.INDEFINITE);

        Timeline downloadAnimation = Animations.shakeY(downloadIcon, 5.0);
        downloadAnimation.setRate(0.2);
        downloadAnimation.setCycleCount(Animation.INDEFINITE);

        saveSyncService.messageProperty().subscribe(message -> {
            if (message != null && !message.isBlank()) {
                syncButton.setText(message);
            }
        });
        saveSyncService.valueProperty().subscribe((oldState, newState) -> {
            switch (oldState) {
                case SYNC -> syncAnimation.stop();
                case SAVE -> saveAnimation.stop();
                case UPLOAD -> uploadAnimation.stop();
                case DOWNLOAD -> downloadAnimation.stop();
                case null, default -> {}
            }

            switch (newState) {
                case SYNC -> {
                    syncButton.setGraphic(syncIcon);
                    if (saveSyncService.isRunning())
                        syncAnimation.playFromStart();
                }
                case SAVE -> {
                    syncButton.setGraphic(saveIcon);
                    if (saveSyncService.isRunning())
                        saveAnimation.playFromStart();
                }
                case UPLOAD -> {
                    syncButton.setGraphic(uploadIcon);
                    if (saveSyncService.isRunning())
                        uploadAnimation.playFromStart();
                }
                case DOWNLOAD -> {
                    syncButton.setGraphic(downloadIcon);
                    if (saveSyncService.isRunning())
                        downloadAnimation.playFromStart();
                }
                case MERGE -> syncButton.setGraphic(mergeIcon);
                case CONFLICT -> syncButton.setGraphic(conflictIcon);
                case DONE -> syncButton.setGraphic(doneIcon);
                case ERROR -> syncButton.setGraphic(errorIcon);
                case null -> {}
            }
        });
        saveSyncService.runningProperty().subscribe(isRunning -> {
            syncButton.setDisable(!isRunning);
            if (!isRunning) {
                switch (saveSyncService.getValue()) {
                    case SYNC -> syncAnimation.stop();
                    case SAVE -> saveAnimation.stop();
                    case UPLOAD -> uploadAnimation.stop();
                    case DOWNLOAD -> downloadAnimation.stop();
                    case null, default -> {}
                }
            }
        });

        return new ToolBar(syncButton);
    }

    private Tab createTodayTab() {
        Tab tab = new Tab("Today");
        tab.setGraphic(new FontIcon(FontAwesomeSolid.CALENDAR_DAY));

        GridPane todayGridPane = new GridPane(20, 15);
        todayGridPane.setAlignment(Pos.CENTER);
        todayGridPane.setPadding(new Insets(20));
        tab.setContent(todayGridPane);

        // Routines
//        Label routineLabel = new Label("Routines");
//        routineLabel.getStyleClass().add(Styles.TITLE_4);
//        HBox routineLabelBox = new HBox(10, new FontIcon(FontAwesomeSolid.CALENDAR_DAY), routineLabel);
//        routineLabelBox.setAlignment(Pos.CENTER);
        
//        todayRoutineListView = new ListView<>();
//        todayRoutineListView.getStyleClass().add(Tweaks.EDGE_TO_EDGE);
//        todayRoutineListView.setCellFactory(col -> new ListCell<>() {
//            private final CheckBox checkBox = new CheckBox();
//            private final Text name = new Text();
//            private final Text dueText = new Text();
//            private final HBox pane = new HBox(7);
//            {
//                setStyle("-fx-background: transparent");
//                checkBox.setFocusTraversable(false);
//                dueText.getStyleClass().add(Styles.TEXT_SUBTLE);
//
//                setOnMouseEntered(event -> {name.setUnderline(true); dueText.setUnderline(true);});
//                setOnMouseExited(event -> {name.setUnderline(false); dueText.setUnderline(false);});
//
//                EventHandler<MouseEvent> setDoneCallback = event -> {
//                    var item = getItem();
//                    if (item != null) {
//                        item.setDone(!item.isDoneToday());
//                        updateFormatting(item);
//                    }
//                };
//                setOnMouseClicked(setDoneCallback);
//                checkBox.setOnMouseClicked(setDoneCallback);
//
//                pane.getChildren().addAll(checkBox, name, dueText);
//            }
//
//            @Override
//            protected void updateItem(Routine item, boolean empty) {
//                super.updateItem(item, empty);
//
//                setText(null);
//                setBackground(Background.EMPTY);
//
//                name.textProperty().unbind();
//                dueText.textProperty().unbind();
//
//                if (item == null || empty) {
//                    setGraphic(null);
//                }
//                else {
//                    name.textProperty().bind(item.name());
//                    dueText.textProperty().bind(item.dueTime().map(time -> {
//                        if (time == null)
//                            return "";
//                        else
//                            return "by " + timeFormat.format(time);
//                    }));
//
//                    updateFormatting(item);
//                    setGraphic(pane);
//                }
//            }
//
//            private void updateFormatting(Routine item) {
//                boolean done = item.isDoneToday();
//                checkBox.setSelected(done);
//
//                if (done) {
//                    if (! name.getStyleClass().contains(Styles.TEXT_STRIKETHROUGH))
//                        name.getStyleClass().addAll(Styles.TEXT_SUBTLE, Styles.TEXT_STRIKETHROUGH);
//                    pane.getChildren().remove(dueText);
//                }
//                else {
//                    name.getStyleClass().removeAll(Styles.TEXT_SUBTLE, Styles.TEXT_STRIKETHROUGH);
//                    if (!pane.getChildren().contains(dueText))
//                        pane.getChildren().add(dueText);
//                }
//            }
//        });

//        Card routineCard = new Card();
//        routineCard.setHeader(routineLabelBox);
//        routineCard.setBody(todayRoutineListView);
//        routineCard.setStyle("-fx-background: -color-fg-default");
//        routineCard.getStyleClass().addAll(Styles.ELEVATED_2);
//        todayGridPane.add(routineCard, 0, 0, 1, 2);
//        GridPane.setHgrow(routineCard, Priority.ALWAYS);
//        GridPane.setVgrow(routineCard, Priority.ALWAYS);

        // Tasks
        Label taskLabel = new Label("Tasks");
        taskLabel.getStyleClass().add(Styles.TITLE_4);
        HBox taskLabelBox = new HBox(10, new FontIcon(FontAwesomeSolid.TASKS), taskLabel);
        taskLabelBox.setAlignment(Pos.CENTER);
        
//        todayTaskTreeView = new TreeView<>();
//        todayTaskTreeView.setShowRoot(false);
//        todayTaskTreeView.getStyleClass().add(Tweaks.EDGE_TO_EDGE);
//        todayTaskTreeView.setRoot(new TreeItem<>(new LegacyTask("rootTask", new TaskInterval.NoInterval(), 0)));
//        todayTaskTreeView.setCellFactory(treeView -> new TreeCell<>() {
//            private Subscription sub = Subscription.EMPTY;
//
//            private final Text name = new Text();
//            private final Text dueText = new Text();
//            private final HBox pane = new HBox(7);
//            private final DoneCheckBox checkBox = new DoneCheckBox(done -> {
//                if (getItem() != null && !isEmpty()) {
//                    getItem().setDone(done);
//                }
//            });
//
//            {
//                name.getStyleClass().add(Styles.TEXT);
//                dueText.getStyleClass().add(Styles.TEXT);
//
//                checkBox.setFocusTraversable(false);
//                checkBox.selectedProperty().subscribe(selected -> updateFormatting(getItem()));
//
//                setOnMouseEntered(event -> { name.setUnderline(true); dueText.setUnderline(true); });
//                setOnMouseExited(event -> { name.setUnderline(false); dueText.setUnderline(false); });
//                setOnMouseClicked(event -> {if (getItem() != null) getItem().toggleDone();});
//
//                pane.getChildren().addAll(checkBox, name, dueText);
//            }
//
//            @Override
//            protected void updateItem(LegacyTask item, boolean empty) {
//                super.updateItem(item, empty);
//
//                setText(null);
//                setBackground(Background.EMPTY);
//                name.textProperty().unbind();
//                checkBox.selectedProperty().unbind();
//
//                sub.unsubscribe();
//                sub = Subscription.EMPTY;
//
//                if (item == null || empty) {
//                    setGraphic(null);
//                }
//                else {
//                    name.textProperty().bind(item.nameProperty());
//                    checkBox.selectedProperty().bind(item.doneObservable());
//
//                    TreeItem<LegacyTask> treeItem = getTreeItem();
//                    treeItem.expandedProperty().bindBidirectional(item.expandedProperty());
//
//                    updateFormatting(item);
//                    setGraphic(pane);
//
//                    sub = Subscription.combine(
//                            item.dueTimeProperty().subscribe(() -> updateFormatting(item)),
//                            item.nextDueObservable().subscribe(() -> updateFormatting(item)),
//                            item.priorityProperty().subscribe(() -> updateFormatting(item)),
//                            () -> treeItem.expandedProperty().unbindBidirectional(item.expandedProperty())
//                    );
//                }
//            }
//
//            private void updateFormatting(LegacyTask item) {
//                if (item != null) {
//                    if (item.isDone()) {
////                        name.setStyle("-fx-fill: #909090; -fx-strikethrough: true");
//                        name.getStyleClass().add(Styles.TEXT_STRIKETHROUGH);
//                        Styles.addStyleClass(name, Styles.TEXT_MUTED, LegacyTask.priorityStyleClassList());
//                        dueText.setText(null);
//                    } else {
////                        name.setStyle("-fx-fill: " + LegacyTask.getPriorityColor(item.getPriority()));
//                        name.getStyleClass().remove(Styles.TEXT_STRIKETHROUGH);
//                        Styles.addStyleClass(name, LegacyTask.priorityStyleClass(item.getPriority()), LegacyTask.priorityStyleClassList());
//
//                        if (item.getInterval().isOverdue()) {
//                            Styles.addStyleClass(dueText, Styles.DANGER, LegacyTask.priorityStyleClassList());
//                            dueText.textProperty().unbind();
//                            dueText.setText("Overdue!");
//                        } else {
////                            dueText.setStyle("-fx-fill: #a1a1a1");
//                            Styles.addStyleClass(dueText, Styles.TEXT_SUBTLE, LegacyTask.priorityStyleClassList());
//                            if (item.getInterval().nextDue() != null) {
//                                if (item.getDueTime() != null)
//                                    dueText.setText(shortDateFormat.format(item.getInterval().nextDue()) + " at " + timeFormat.format(item.getDueTime()));
//                                else
//                                    dueText.setText(shortDateFormat.format(item.getInterval().nextDue()));
//                            } else {
//                                dueText.setText(null);
//                            }
//                        }
//                    }
//                }
//            }
//        });

        todayTaskCategoryList = new ListBox<>(
                item -> new TodayTaskCategoryView<>(TodayTaskBox::new, TodayTaskBox::unbind, item, Task::isToday),
                TaskCategoryViewBase::unbind
        );
        todayTaskDoneBox = new TaskCategoryViewBase(
                () -> new ListBox<>(TodayTaskBox::new, TodayTaskBox::unbind)
        );

        Card taskCard = new Card();
        taskCard.setHeader(taskLabelBox);
        taskCard.setBody(new ScrollPane(new VBox(10, todayTaskCategoryList, todayTaskDoneBox)));
        taskCard.getStyleClass().add(Styles.ELEVATED_2);
        todayGridPane.add(taskCard, 1, 0, 1, 2);
        GridPane.setHgrow(taskCard, Priority.ALWAYS);
        GridPane.setVgrow(taskCard, Priority.ALWAYS);

        // Journal
        todayJournalTextArea = new TextArea();
        todayJournalTextArea.setPromptText("Type journal here...");
        todayJournalTextArea.setWrapText(true);
        todayJournalTextArea.setPrefWidth(150);
        todayJournalTextArea.getStyleClass().add(Styles.ELEVATED_2);
        todayGridPane.add(todayJournalTextArea, 2, 1);
        GridPane.setHgrow(todayJournalTextArea, Priority.NEVER);
        GridPane.setVgrow(todayJournalTextArea, Priority.ALWAYS);
        

        // GridPane containing the rest of the interactions (others)
        GridPane othersGridPane = new GridPane(3, 15);
        GridPane.setHgrow(othersGridPane, Priority.NEVER);
        Card othersCard = new Card();
        othersCard.setBody(othersGridPane);
        othersCard.getStyleClass().add(Styles.ELEVATED_2);
        todayGridPane.add(othersCard, 2, 0, 1, 1);

        Label sleepLabel = new Label("Sleep:", new FontIcon(FontAwesomeSolid.BED));
        sleepLabel.setFont(Font.font(14));
        othersGridPane.add(sleepLabel, 0, 0);

        todaySleepButton = new Button("Go To Sleep");
        todaySleepButton.setOnAction(this::onSleepButtonAction);
        othersGridPane.add(todaySleepButton, 1, 0);

        Label workoutLabel = new Label("Exercise:", new FontIcon(FontAwesomeSolid.RUNNING));
        workoutLabel.setFont(Font.font(14));
        othersGridPane.add(workoutLabel, 0, 1);

        todayExerciseButton = new Button("Start");
        todayExerciseButton.setOnAction(this::onWorkoutButtonAction);
        othersGridPane.add(todayExerciseButton, 1, 1);

        Label weightLabel = new Label("Weight:", new FontIcon(FontAwesomeSolid.WEIGHT));
        weightLabel.setFont(Font.font(14));
        othersGridPane.add(weightLabel, 0, 2);
        
        todayWeightSpinner = new Spinner<>(new WeightSpinnerValueFactory());
        todayWeightSpinner.setEditable(true);
        othersGridPane.add(todayWeightSpinner, 1, 2);

        return tab;
    }

    private Tab createSleepTab() {
        Tab tab = new Tab("Sleep");
        tab.setGraphic(new FontIcon(FontAwesomeSolid.BED));

        GridPane sleepGridPane = new GridPane(5, 10);
        sleepGridPane.setPadding(new Insets(15));
        sleepGridPane.getStyleClass().add(Styles.DENSE);
        sleepGridPane.setAlignment(Pos.CENTER_LEFT);
        tab.setContent(sleepGridPane);

        sleepButton = new Button("Start");
        sleepButton.setOnAction(this::onSleepButtonAction);
        sleepButton.setOnMouseClicked(this::onSleepButtonClick);
        sleepGridPane.add(sleepButton, 0, 0);
        GridPane.setHgrow(sleepButton, Priority.NEVER);

        sleepStatusLabel = new Label("Status: not started");
        sleepGridPane.add(sleepStatusLabel, 1, 0);
        GridPane.setHgrow(sleepStatusLabel, Priority.ALWAYS);


        sleepTableView = new TableView<>();
        sleepTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_NEXT_COLUMN);
        sleepTableView.getStyleClass().add(Styles.DENSE);
        sleepGridPane.add(sleepTableView, 0, 1, 2, 1);
        GridPane.setVgrow(sleepTableView, Priority.ALWAYS);

        TableColumn<Sleep, LocalDate> sleepDateCol = new TableColumn<>("Date");
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
        sleepDateCol.setCellValueFactory(sleep -> new SimpleObjectProperty<>(sleep.getValue().end().toLocalDate().minusDays(1)));

        TableColumn<Sleep, LocalTime> sleepStartCol = new TableColumn<>("Start");
        sleepStartCol.setCellFactory(column -> new timeTableCell<>(timeFormat));
        sleepStartCol.setCellValueFactory(sleep -> new SimpleObjectProperty<>(sleep.getValue().start().toLocalTime()));

        TableColumn<Sleep, LocalTime> sleepEndCol = new TableColumn<>("End");
        sleepEndCol.setCellFactory(column -> new timeTableCell<>(timeFormat));
        sleepEndCol.setCellValueFactory(sleep -> new SimpleObjectProperty<>(sleep.getValue().end().toLocalTime()));

        TableColumn<Sleep, Float> sleepLengthCol = new TableColumn<>("Sleep (hours)");
        sleepLengthCol.setCellValueFactory(sleep -> new SimpleFloatProperty(sleep.getValue().length().toMinutes() / 60f).asObject());

        sleepTableView.getColumns().addAll(sleepDateCol, sleepStartCol, sleepEndCol, sleepLengthCol);


        Tab sleepLenChartTab = new Tab("Total Sleep");
        var sleepLenNumAxis = new NumberAxis();
        sleepLenNumAxis.setLabel("Sleep (hours)");
        sleepLenNumAxis.setAutoRanging(true);
        sleepLenNumAxis.setForceZeroInRange(false);
        sleepLenChart = new AreaChart<>(new CategoryAxis(), sleepLenNumAxis);
        sleepLenChart.setLegendVisible(false);
        sleepLenChart.getStylesheets().add(chartStylesheet);
        sleepLenChartTab.setContent(sleepLenChart);

        Tab sleepTimeChartTab = new Tab("Sleep Times");
        sleepStartChartData.setName("Sleep Start");
        sleepEndChartData.setName("SleepEnd");
        NumberAxis sleepTimeNumAxis = new NumberAxis("Sleep (hours)", 3.0, 11.0, 2.0);
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
        sleepTimeNumAxis.setAutoRanging(true);
        sleepTimeNumAxis.setForceZeroInRange(false);
        sleepTimeChart = new LineChart<>(new CategoryAxis(), sleepTimeNumAxis);
        sleepTimeChart.setHorizontalZeroLineVisible(false);
        sleepTimeChart.getStylesheets().add(chartStylesheet);
        sleepTimeChartTab.setContent(sleepTimeChart);

        TabPane chartPane = new TabPane(sleepLenChartTab, sleepTimeChartTab);
        chartPane.setSide(Side.TOP);
        chartPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        sleepGridPane.add(chartPane, 0, 2, 2, 1);
        GridPane.setVgrow(chartPane, Priority.ALWAYS);

        return tab;
    }

    private Tab createExerciseTab() {
        Tab tab = new Tab("Exercise");
        tab.setGraphic(new FontIcon(FontAwesomeSolid.RUNNING));

        GridPane exerciseGridPane = new GridPane(0, 10);
        exerciseGridPane.setPadding(new Insets(15));
        tab.setContent(exerciseGridPane);

        workoutButton = new Button("Start");
        workoutButton.setOnAction(this::onWorkoutButtonAction);
        workoutButton.setOnMouseClicked(this::onWorkoutButtonClick);
        exerciseGridPane.add(workoutButton, 0, 0);

        workoutStatusLabel = new Label("Status: not started");
        exerciseGridPane.add(workoutStatusLabel, 1, 0);

        Button editExerciseButton = new Button("Edit Exercises");
        editExerciseButton.setOnAction(this::addExerciseButtonClick);
        exerciseGridPane.add(editExerciseButton, 2, 0);
        GridPane.setHalignment(editExerciseButton, HPos.RIGHT);
        GridPane.setHgrow(editExerciseButton, Priority.ALWAYS);


        workoutTableView = new TableView<>();
        workoutTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_NEXT_COLUMN);

        TableColumn<Workout, String> workoutDateCol = new TableColumn<>("Date");
        workoutDateCol.setCellValueFactory(workout -> new SimpleStringProperty(workout.getValue().start().toLocalDate().format(dateFormat)));

        TableColumn<Workout, Float> workoutLengthCol = new TableColumn<>("Length (minutes)");
        workoutLengthCol.setCellValueFactory(workout -> new SimpleFloatProperty(workout.getValue().length().toSeconds() / 60f).asObject());

        TableColumn<Workout, String> workoutExerciseCol = new TableColumn<>("Exercises");
        workoutExerciseCol.setCellValueFactory(workout -> genWorkoutExercises(workout.getValue().exercises()));
        
        workoutTableView.getColumns().addAll(workoutDateCol, workoutLengthCol, workoutExerciseCol);
        exerciseGridPane.add(workoutTableView, 0, 1, 3, 1);
        GridPane.setVgrow(workoutTableView, Priority.ALWAYS);

        return tab;
    }

    private Tab createTaskCategoryTab() {
        Tab tab = new Tab("Categories");
        tab.setGraphic(new FontIcon(FontAwesomeSolid.SHAPES));

        taskCategoryListBox = new DraggableListBox<>(
                category -> {
                    TaskCategoryBox box = new TaskCategoryBox(category);
                    box.setOnMouseClicked(event -> {
                        if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                            openTaskCategoryPanel(category);
                        }
                    });
                    return box;
                },
                TaskCategoryBox::unbind,
                Pos.CENTER_LEFT
        );
        taskCategoryListBox.setNodeStyle(
                "-fx-background-color: -color-bg-default;" +
                "-fx-background-radius: 5;" +
                "-fx-border-color: -color-border-default;" +
                "-fx-border-width: 1;" +
                "-fx-border-radius: 5;" +
                "-fx-padding: 10"
        );

        Button addCategoryButton = new Button("Add Category");
        addCategoryButton.setOnAction(event -> {
            TaskCategory newCat = new TaskCategory("", Color.WHITE);
            twig.getTaskCategoryList().add(newCat);
            openTaskCategoryPanel(newCat);
        });

        tab.setContent(new VBox(10, addCategoryButton, taskCategoryListBox));
        return tab;
    }

    public Tab createTaskTab() {
        Tab tab = new Tab("Tasks");
        tab.setGraphic(new FontIcon(FontAwesomeSolid.TASKS));

        taskListBox = new ListBox<>(
                category -> new TaskCategoryView(
                        category,
                        cat -> {
                            Task newTask = new Task(
                                    "",
                                    cat,
                                    1,
                                    Task.OccurrencePattern.DUE_BY,
                                    Task.ExtendPattern.ON_COMPLETION,
                                    new TwigInterval.NoRepeat(),
                                    null
                            );
                            cat.getTasks().add(newTask);
                            twig.taskList().add(newTask);
                            openTaskPanel(newTask);
                        },
                        task -> {
                            SubTask newSubTask = new SubTask(
                                    "",
                                    null,
                                    task
                            );
                            task.getSubTasks().add(newSubTask);
                            openTaskPanel(newSubTask);
                        },
                        (event, task) -> {
                            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                                openTaskPanel(task);
                                event.consume();
                            }
                        }),
                TaskCategoryViewBase::unbind
        );

        tab.setContent(new ScrollPane(new VBox(10, taskListBox)));
        return tab;
    }

    private Tab createListTab() {
        Tab tab = new Tab("Lists");
        tab.setGraphic(new FontIcon(FontAwesomeSolid.LIST));

        listTree = new TreeView<>(new TreeItem<>());
        listTree.setShowRoot(false);
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
                            subscription = listItem.name().subscribe(label::setText).and(subscription);

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

        tab.setContent(listTree);
        return tab;
    }

    private Tab createJournalTab() {
        Tab tab = new Tab("Journals");
        tab.setGraphic(new FontIcon(FontAwesomeSolid.BOOK));

        GridPane journalGridPane = new GridPane(10, 10);
        journalGridPane.setPadding(new Insets(15));
        tab.setContent(journalGridPane);

        Calendar journalCalendar = new Calendar();
        journalCalendar.setDayCellFactory(calendar -> new DateCell() {
            @Override
            public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setDisable(empty || !twig.journalMap().containsKey(item));
            }
        });

        journalListView = new ListView<>();
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
                journalTextArea.clear();
                journalWeightSpinner.getValueFactory().valueProperty().unbindBidirectional(twig.journalMap().get(oldValue).weight());
                journalWeightSpinner.getValueFactory().setValue(null);
                journalRoutineList.setItems(null);
                journalTaskList.setItems(null);
            }

            if (newValue != null && twig.journalMap().containsKey(newValue)) {
                Journal journal = twig.journalMap().get(newValue);

                journalRoutineList.setItems(journal.completedRoutines());
                journalTaskList.setItems(journal.completedTasks());
                journalWeightSpinner.getValueFactory().valueProperty().bindBidirectional(journal.weight());
                journalTextArea.textProperty().bindBidirectional(journal.textProperty());
                journalTextArea.setPromptText("Type journal here...");
            }
        });

        VBox journalLeftVBox = new VBox(10, journalCalendar, journalListView);
        journalLeftVBox.setMinWidth(Region.USE_PREF_SIZE);
        VBox.setVgrow(journalListView, Priority.ALWAYS);
        journalGridPane.add(journalLeftVBox, 0, 0,1, 4);
        GridPane.setHgrow(journalLeftVBox, Priority.SOMETIMES);

        journalCalendar.valueProperty().subscribe(journalListView.getSelectionModel()::select);
        journalListView.getSelectionModel().selectedItemProperty().subscribe(journalCalendar::setValue);

        journalTextArea = new TextArea();
        journalTextArea.setPromptText("Select a date on the left to see journal");
        journalTextArea.setWrapText(true);
        journalGridPane.add(journalTextArea, 1, 0, 1, 4);
        GridPane.setHgrow(journalTextArea, Priority.ALWAYS);
        GridPane.setVgrow(journalTextArea, Priority.ALWAYS);

        Label weightLabel = new Label("Weight:");
        weightLabel.setFont(Font.font(16));
        journalGridPane.add(weightLabel, 2, 0);

        journalWeightSpinner = new Spinner<>(new WeightSpinnerValueFactory());
        journalWeightSpinner.setEditable(true);
        journalGridPane.add(journalWeightSpinner, 2, 1);
        
        journalRoutineList = new ListView<>();
        journalRoutineList.setSelectionModel(null);
        journalRoutineList.getStyleClass().addAll(Tweaks.EDGE_TO_EDGE, Styles.DENSE);
        TitledPane routinePane = new TitledPane("Completed Routines", journalRoutineList);
        routinePane.getStyleClass().add(Styles.DENSE);
        routinePane.setMaxHeight(Double.MAX_VALUE);
        journalGridPane.add(routinePane, 2, 2);
        GridPane.setVgrow(routinePane, Priority.ALWAYS);

        journalTaskList = new ListView<>();
        journalTaskList.setSelectionModel(null);
        journalTaskList.getStyleClass().addAll(Tweaks.EDGE_TO_EDGE, Styles.DENSE);
        TitledPane taskPane = new TitledPane("Completed Tasks", journalTaskList);
        taskPane.getStyleClass().add(Styles.DENSE);
        taskPane.setMaxHeight(Double.MAX_VALUE);
        journalGridPane.add(taskPane, 2, 3);
        GridPane.setVgrow(taskPane, Priority.ALWAYS);

        return tab;
    }

    private Tab createSettingsTab() {
        Tab tab = new Tab("Settings");
        tab.setGraphic(new FontIcon(FontAwesomeSolid.COG));

        Label settingsLabel = new Label("Settings");
        settingsLabel.setFont(Font.font("System Bold", 21));

        Tile startOfDayTile = new Tile(
            "Start of day:",
            "The time of the day which separates between late night and early morning",
            new FontIcon(FontAwesomeSolid.SUN)
        );
        settingsDayStartInput = new TimeInput(twig.getDayStart().getValue(), false);
        System.out.println(settingsDayStartInput.getText());
        System.out.println(settingsDayStartInput.getTime());
        startOfDayTile.setAction(settingsDayStartInput);
        startOfDayTile.setActionHandler(settingsDayStartInput::requestFocus);

        Tile startOfNightTile = new Tile(
            "Start of night",
            "The time of day which separates late afternoon/evening from early night",
            new FontIcon(FontAwesomeSolid.MOON)
        );
        settingsNightStartInput = new TimeInput(twig.getNightStart().getValue(), false);
        startOfNightTile.setAction(settingsNightStartInput);
        startOfNightTile.setActionHandler(settingsNightStartInput::requestFocus);
        
        Tile autoSyncIntervalTile = new Tile(
            "Auto Save/Sync Interval",
            "How often a save/sync is triggered in the background (in seconds)",
            new FontIcon(FontAwesomeSolid.HISTORY)
        );
        settingsIntervalSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 30*60));
        settingsIntervalSpinner.getValueFactory().valueProperty().subscribe(interval ->
                saveSyncService.setPeriod(Duration.seconds(interval)));
        settingsIntervalSpinner.setPrefWidth(110);
        autoSyncIntervalTile.setAction(settingsIntervalSpinner);
        autoSyncIntervalTile.setActionHandler(settingsIntervalSpinner::requestFocus);

        Tile autoSyncToggleTile = new Tile(
            "Auto Sync Toggle",
            "Whether to automatically sync with Dropbox when data is saved periodically",
            new FontIcon(FontAwesomeSolid.CLOUD)
        );
        ToggleSwitch settingsAutoSyncToggle = new ToggleSwitch();
        settingsAutoSyncToggle.selectedProperty().subscribe((autoSync) -> twig.autoSyncProperty().set(autoSync));
        autoSyncToggleTile.setAction(settingsAutoSyncToggle);
        autoSyncToggleTile.setActionHandler(settingsAutoSyncToggle::fire);

        Tile dbxTile = new Tile(
            "Dropbox Account",
            "Connect a Dropbox account to sync data to the cloud and between devices",
            new FontIcon(FontAwesomeBrands.DROPBOX)
        );
        settingsDbxName = new Label("dbx account here");
        settingsDbxIcon = new ImageView();
        settingsDbxIcon.setFitWidth(48);
        settingsDbxIcon.setFitHeight(48);
        Rectangle iconClip = new Rectangle(48, 48);
        iconClip.setArcWidth(38);
        iconClip.setArcHeight(38);
        settingsDbxIcon.setClip(iconClip);
        settingsDbxButton = new Button("Dbx Connect Button");
        settingsDbxButton.setOnAction(this::onDbxButton);

        GridPane dbxAccountPane = new GridPane(10, 10);
        dbxAccountPane.add(settingsDbxIcon, 0, 0, 1, 2);
        dbxAccountPane.add(settingsDbxName, 1, 0);
        dbxAccountPane.add(settingsDbxButton, 1, 1);
        dbxAccountPane.setAlignment(Pos.CENTER);
        dbxTile.setAction(dbxAccountPane);
        dbxTile.setActionHandler(settingsDbxButton::fire);

        Tile themeTile = new Tile(
            "Theme",
            "Visual theme to use throughout the app",
            new FontIcon(FontAwesomeSolid.PALETTE)
        );
        ChoiceBox<Theme> settingsThemeChoiceBox = new ChoiceBox<>(FXCollections.observableArrayList(themes));
        settingsThemeChoiceBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Theme object) {
                return object != null ? object.getName() : "";
            }

            @Override
            public Theme fromString(String s) {
                for (Theme theme : themes) {
                    if (theme.getName().equals(s)) {
                        return theme;
                    }
                }
                return null;
            }
        });
        settingsThemeChoiceBox.setOnAction(action -> {
            Theme theme =  settingsThemeChoiceBox.getValue();
            application.setTheme(theme);
            twig.setVisualTheme(theme.getName());
        });

        Theme currentTheme = settingsThemeChoiceBox.getConverter().fromString(twig.getVisualTheme());
        if (currentTheme == null)
            settingsThemeChoiceBox.getSelectionModel().selectFirst();
        else
            settingsThemeChoiceBox.setValue(currentTheme);
        themeTile.setAction(settingsThemeChoiceBox);
        themeTile.setActionHandler(settingsThemeChoiceBox::show);

        VBox settingsVBox = new VBox(
            settingsLabel,
            new Separator(),
            startOfDayTile,
            startOfNightTile,
            autoSyncIntervalTile,
            autoSyncToggleTile,
            new Separator(),
            dbxTile,
            new Separator(),
            themeTile
        );
        settingsVBox.setAlignment(Pos.TOP_CENTER);
        settingsVBox.setPadding(new Insets(20));
        ScrollPane settingsScrollPane = new ScrollPane(settingsVBox);
        settingsScrollPane.setFitToWidth(true);
        VBox outerVBox = new VBox(settingsScrollPane);
        outerVBox.setAlignment(Pos.TOP_CENTER);
        outerVBox.setFillWidth(false);
        tab.setContent(outerVBox);


        twig.dbxClient().subscribe(_ -> updateDbxAccountState());
        updateDbxAccountState();

        return tab;
    }

    public void attachTwigData() {
        Journal todaysJournal = twig.todaysJournal();
        todayJournalTextArea.textProperty().bindBidirectional(todaysJournal.textProperty());
        subscriptions = subscriptions.and(() -> todayJournalTextArea.textProperty().unbindBidirectional(todaysJournal.textProperty()));

        todayWeightSpinner.getValueFactory().valueProperty().bindBidirectional(todaysJournal.weight());
        subscriptions = subscriptions.and(() -> todayWeightSpinner.getValueFactory().valueProperty().unbindBidirectional(todaysJournal.weight()));

        todayTaskCategoryList.setItems(twig.getTaskCategoryList());
        todayTaskDoneBox.setCategory(
                twig.taskList(),
                Task::isDoneToday,
                new SimpleStringProperty("Done"),
                new SimpleObjectProperty<>(Color.valueOf("aaaaaaff"))
        );
        subscriptions = subscriptions.and(todayTaskCategoryList::unbind);
        subscriptions = subscriptions.and(todayTaskDoneBox::unbind);

        subscriptions = twig.getDayStart().subscribe(this::updateTodaySleepPane).and(subscriptions);
        subscriptions = twig.getNightStart().subscribe(this::updateTodaySleepPane).and(subscriptions);

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

//        taskListChangeMapper.constructFromList(twig.taskList());
//        twig.taskList().addListener(taskListChangeMapper);
//        subscriptions = subscriptions.and(() -> twig.taskList().removeListener(taskListChangeMapper));
        taskListBox.setItems(twig.getTaskCategoryList());
        subscriptions = subscriptions.and(() -> taskListBox.setItems(null));

        taskCategoryListBox.setItems(twig.getTaskCategoryList());
        subscriptions = subscriptions.and(() -> taskCategoryListBox.setItems(null));

//        populateTaskTree(taskTreeTable.getRoot(), twig.taskList());
        populateTwigLists();

//        routineTable.setItems(twig.routineList());
//        subscriptions = subscriptions.and(() -> routineTable.setItems(null));

        MapChangeListener<LocalDate, Journal> journalChangeListener = change ->
                journalListView.setItems(FXCollections.observableArrayList(twig.journalMap().keySet()).sorted(Comparator.reverseOrder()));
        twig.journalMap().addListener(journalChangeListener);
        subscriptions = subscriptions.and(() -> twig.journalMap().removeListener(journalChangeListener));

        journalListView.setItems(FXCollections.observableArrayList(twig.journalMap().keySet()).sorted(Comparator.reverseOrder()));
        subscriptions = subscriptions.and(() -> journalListView.setItems(null));

        settingsDayStartInput.setTime(twig.getDayStart().getValue());
        settingsNightStartInput.setTime(twig.getNightStart().getValue());
        settingsIntervalSpinner.getValueFactory().setValue(twig.syncIntervalProperty().getValue());

        subscriptions = settingsDayStartInput.timeValueProperty().subscribe(twig::setDayStart).and(subscriptions);
        subscriptions = settingsNightStartInput.timeValueProperty().subscribe(twig::setNightStart).and(subscriptions);
        subscriptions = settingsIntervalSpinner.getValueFactory().valueProperty().subscribe(twig.syncIntervalProperty()::setValue).and(subscriptions);

        saveSyncService.restart();
    }

    public void detachTwigData() {
        subscriptions.unsubscribe();
        subscriptions = Subscription.EMPTY;

        if (saveSyncService.getState() != Worker.State.RUNNING)
            saveSyncService.cancel();
    }

    public Pane getRoot() {
        return rootPane;
    }

    public void closeTwig(WindowEvent event) {
        event.consume();
        saveSyncService.syncAndExit();
    }

    private void populateTaskTree(TreeItem<LegacyTask> root, ObservableList<LegacyTask> topLevelTasks) {
        root.getChildren().clear();
        for (LegacyTask task : topLevelTasks) {
            root.getChildren().add(constructTaskTree(task));
        }

        ListChangeListener<LegacyTask> listener = change -> handleTaskItemChange(change, root);
        topLevelTasks.addListener(listener);

        subscriptions = Subscription.combine(
                () -> root.getChildren().clear(),
                () -> topLevelTasks.removeListener(listener)
        ).and(subscriptions);
    }

    private TreeItem<LegacyTask> constructTaskTree(LegacyTask task) {
        TreeItem<LegacyTask> treeItem = new TreeItem<>(task);
        treeItem.setExpanded(true);

        for (LegacyTask subTask : task.getChildren()) {
            treeItem.getChildren().add(constructTaskTree(subTask));
        }

        ListChangeListener<LegacyTask> listener = change -> handleTaskItemChange(change, treeItem);
        task.getChildren().addListener(listener);

        subscriptions = subscriptions.and(() -> task.getChildren().removeListener(listener));

        return treeItem;
    }

    private void handleTaskItemChange(ListChangeListener.Change<? extends LegacyTask> change, TreeItem<LegacyTask> parent) {
        ObservableList<TreeItem<LegacyTask>> treeChildren = parent.getChildren();
        while (change.next()) {
            if (change.wasPermutated()) {
                for (int i = change.getFrom(); i < change.getTo(); ++i) {
                    TreeItem<LegacyTask> cell = treeChildren.remove(i);
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

    private void handleListItemChange(ListChangeListener.Change<? extends TwigListItem> change, TreeItem<Object> parent) {
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

    protected void onSleepButtonAction(ActionEvent event) {
        if(!twig.isSleeping()) {
            TimeDateModalBox modalBox = new TimeDateModalBox(
                    modalPane,
                    "Start Sleeping",
                    "Confirm date and time when you went to bed",
                    false, twig::startSleep);
            modalPane.show(modalBox);
        }
        else {
            TimeDateModalBox modalBox = new TimeDateModalBox(
                    modalPane,
                    "Finish Sleeping",
                    "Confirm date and time when you got out of bed",
                    true, datetime -> {
                        LocalDate lastNight = datetime.toLocalDate().minusDays(1);
                        if (twig.sleepRecords().containsKey(lastNight)) {
                            new AlertModalBox(
                                    modalPane,
                                    "Overwrite Sleep Record?",
                                    "An existing sleep record was found for "+lastNight.format(dateFormat)+"\nDo you want to overwrite this record?",
                                    true,
                                    buttonType -> {
                                        if (buttonType == ModalButtonType.YES)
                                            twig.finishSleep(datetime);
                                    },
                                    ModalButtonType.YES, ModalButtonType.NO
                            ).show();
                        }
                        else {
                            twig.finishSleep(datetime);
                        }
                    }
            );
            modalPane.show(modalBox);
        }
    }

    protected void onSleepButtonClick(MouseEvent event) {
        if(twig.isSleeping() && event.getButton() == MouseButton.SECONDARY) {
            new AlertModalBox(
                    modalPane,
                    "Cancel Sleep Record?",
                    "Do you want to cancel this sleep record?",
                    false,
                    buttonType -> {
                        if (buttonType == ModalButtonType.YES)
                            twig.startSleep(null);
                    },
                    ModalButtonType.YES, ModalButtonType.NO
            ).show();
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

    protected void onWorkoutButtonAction(ActionEvent event) {
        if(!twig.isWorkingOut()) {
            TimeDateModalBox modalBox = new TimeDateModalBox(modalPane, "Start Workout", "Confirm date and time for the start of this workout", false, twig::startWorkout);
            modalPane.show(modalBox);
        }
        else {
            TimeDateModalBox modalBox = new TimeDateModalBox(modalPane, "Finish Workout", "Confirm date and time for the end of this workout", true, datetime -> {
                modalPane.show(new WorkoutModal(modalPane, twig, exercises -> {
                    twig.finishWorkout(exercises, datetime);
                    workoutTableView.refresh();
                }));
            });
            modalPane.show(modalBox);
        }
    }

    protected void onWorkoutButtonClick(MouseEvent event) {
        if(twig.isWorkingOut() && event.getButton() == MouseButton.SECONDARY) {
            new AlertModalBox(
                    modalPane,
                    "Cancel Workout?",
                    "Do you want to cancel this workout?",
                    false,
                    buttonType -> {
                        if (buttonType == ModalButtonType.YES)
                            twig.startWorkout(null);
                    },
                    ModalButtonType.YES, ModalButtonType.NO
            ).show();
        }
    }

    protected void addExerciseButtonClick(ActionEvent event) {
        modalPane.show(new ExerciseModal(modalPane, twig.exerciseList()));
    }

    private void openTaskPanel(TaskInterface taskInterface) {
        final ModalButtonType deleteButton = new ModalButtonType(
                "Delete",
                ButtonBar.ButtonData.NO,
                new FontIcon(FontAwesomeSolid.TRASH_ALT),
                Styles.DANGER
        );

        switch (taskInterface) {
            case Task task -> {
                new AlertModalBox(
                        modalPane,
                        "Edit LegacyTask", "",
                        new TaskPropertyPane(twig, task),
                        false,
                        buttonType -> {
                            if (buttonType == deleteButton) {
                                task.setCategory(null);
                                twig.taskList().remove(task);
                            }
                        },
                        ModalButtonType.OK, deleteButton
                ).show();
            }
            case SubTask subTask -> {
                new AlertModalBox(
                        modalPane,
                        "Edit Sub-LegacyTask", "",
                        new SubTaskPropertyPane(subTask),
                        false,
                        buttonType -> {
                            if (buttonType == deleteButton) {
                                subTask.getParentTask().getSubTasks().remove(subTask);
                            }
                        },
                        ModalButtonType.OK, deleteButton
                ).show();
            }
            case null, default -> {}
        }
    }

    private void openTaskCategoryPanel(TaskCategory category) {
        final ModalButtonType deleteButton = new ModalButtonType(
                "Delete",
                ButtonBar.ButtonData.NO,
                new FontIcon(FontAwesomeSolid.TRASH_ALT),
                Styles.DANGER
        );

        new AlertModalBox(
                modalPane,
                "Edit LegacyTask", "",
                new TaskCategoryPropertyPane(category),
                true,
                buttonType -> {
                    if (buttonType == deleteButton) {
                        if (category.getTasks().isEmpty()) {
                            twig.getTaskCategoryList().remove(category);
                        }
                        else {
                            ObservableList<TaskCategory> catList = FXCollections.observableArrayList();
                            twig.getTaskCategoryList().forEach(cat -> {
                                if (cat != category)
                                    catList.add(cat);
                            });

                            ChoiceBox<TaskCategory> categoryChoiceBox = new ChoiceBox<>(catList);
                            categoryChoiceBox.setConverter(new TaskPropertyPane.CategoryChoiceBoxConverter());
                            new AlertModalBox(
                                    modalPane,
                                    "Select where to move tasks",
                                    "This category has task(s) assigned to it.\nBefore deleting the tasks must be moved" +
                                            "to a different category.\nWhich category would you like to move them to?",
                                    categoryChoiceBox, false,
                                    deleteButtonType -> {
                                        if (deleteButtonType == ModalButtonType.OK) {
                                            TaskCategory newCategory = categoryChoiceBox.getValue();
                                            category.getTasksSafe().forEach(task -> task.setCategory(newCategory));
                                            twig.getTaskCategoryList().remove(category);
                                        }
                                    },
                                    ModalButtonType.OK, ModalButtonType.CANCEL
                            ).show();
                        }
                    }
                },
                ModalButtonType.OK, deleteButton
        ).show();
    }

    private void updateDbxAccountState() {
        if (twig.dbxClient().getValue() == null) {
            syncButton.setDisable(true);
            syncButton.setText("No Dropbox account connected");

            settingsDbxName.setText("No active account");
            settingsDbxButton.setText("Connect Account");
            settingsDbxButton.getStyleClass().remove(Styles.DANGER);
            settingsDbxButton.getStyleClass().add(Styles.ACCENT);
        }
        else {
            syncButton.setDisable(false);
            syncButton.setText("Not yet synced");
            
            settingsDbxButton.setText("Logout");
            settingsDbxButton.getStyleClass().remove(Styles.ACCENT);
            settingsDbxButton.getStyleClass().add(Styles.DANGER);
            try {
                settingsDbxName.setText(twig.getDbxAccountName());
                settingsDbxIcon.setImage(twig.getDbxAccountImage());
            }
            catch (DbxException e) {
                System.err.println("Failed to render dbx account name: " + e.getMessage());
            }
        }
    }

    private void dbxAuthorize() {
        String authUrl = twig.genDbxAuthUrl();
        Hyperlink url = new Hyperlink(authUrl);
        url.setOnAction(event -> {
            try {
                browser.launchExternalBrowser(authUrl);
            } catch (IOException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        });
        url.setMaxWidth(600);
        url.setWrapText(true);

        TextField codeInput = new TextField();
        codeInput.setPromptText("Enter code here");

        VBox contentBox = new VBox(url, codeInput);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setSpacing(15);

        AlertModalBox dialog = new AlertModalBox(
                modalPane,
                "Connect Dropbox Account",
                "Open the following URL and paste the provided code below:",
                contentBox,
                true,
                buttonType -> {
                    if (buttonType == ModalButtonType.OK) {
                        try {
                            detachTwigData();
                            twig.authDbxFromCode(codeInput.getText());
                        } catch (DbxException e) {
                            new AlertModalBox(
                                    modalPane,
                                    "Authentication Error",
                                    "Error Authenticating, code not accepted",
                                    new Label("Make sure you've entered the code properly and try again."),
                                    false, null, ModalButtonType.OK
                            ).show();
                        } finally {
                            attachTwigData();
                        }
                    }
                },
                ModalButtonType.OK, ModalButtonType.CANCEL
        );
        dialog.show();
    }

    public void onDbxButton() {
        onDbxButton(null);
    }

    private void onDbxButton(ActionEvent event) {
        if (twig.dbxClient().getValue() == null) {
            dbxAuthorize();
        }
        else {
            modalPane.show(AlertModalBox.yesNoAlert(
                    modalPane,
                    "Confirm logout?",
                    "Are you sure you want to log out of your Dropbox account?",
                    false,
                    buttonType -> {
                        if (buttonType == ModalButtonType.YES) twig.dbxLogout();
                    }
            ));
        }
    }

    private FileAction handleDataConflict(FileAction overallAction, Map<TaskTwig.TwigDataFile, FileAction> fileActions) {
        final ModalButtonType remoteButton = new ModalButtonType("Remote", ButtonBar.ButtonData.NO, new FontIcon(FontAwesomeSolid.DOWNLOAD));
        final ModalButtonType localButton = new ModalButtonType("Local", ButtonBar.ButtonData.YES, new FontIcon(FontAwesomeSolid.UPLOAD));
        final ModalButtonType mergeButton = new ModalButtonType("Merge", ButtonBar.ButtonData.OTHER, new FontIcon(FontAwesomeSolid.EXCHANGE_ALT));

        GridPane commitDiffTable = new GridPane(10 ,10);
        int row = 0;
        for (Map.Entry<TaskTwig.TwigDataFile, FileAction> entry : fileActions.entrySet()) {
            String conflictLabel = switch(entry.getValue()) {
                case DOWNLOAD -> "Remote is Ahead";
                case UPLOAD -> "Local is Ahead";
                case CONFLICT -> "Local and Remove Conflict";
                default -> throw new IllegalStateException("Unexpected FileAction for file in data conflict handler gui: " + entry.getValue());
            };

            commitDiffTable.add(new Label(entry.getKey().toString()), 0, row);
            commitDiffTable.add(new Label(conflictLabel), 1, row++);
        }

        AlertModalBox alert = switch(overallAction) {
            case MERGE -> new AlertModalBox(
                    modalPane,
                    "Conflicting data between local and remote!",
                    "Data across non-overlapping files conflict between local and remote. " +
                            "Would you like to merge the data, keep only the local data, or keep only the remote (Dropbox) data?",
                    commitDiffTable, false, null,
                    mergeButton, remoteButton, localButton, ModalButtonType.CANCEL
            );
            case CONFLICT -> new AlertModalBox(
                    modalPane,
                    "Conflicting data between local and remote!",
                    "Would you like to keep the local data or the remote (Dropbox) data?",
                    commitDiffTable, false, null,
                    remoteButton, localButton, ModalButtonType.CANCEL
            );
            default -> throw new IllegalStateException("Unexpected FileAction in data conflict handler gui: " + overallAction);
        };

        Optional<ModalButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == remoteButton)
                return FileAction.DOWNLOAD;
            else if (result.get() == localButton)
                return FileAction.UPLOAD;
            else if (result.get() == mergeButton)
                return FileAction.MERGE;
            else
                return FileAction.NONE;
        }

        return FileAction.NONE;
    }

    private boolean userConfirmSave() {
        AlertModalBox alert = AlertModalBox.yesNoAlert(
                modalPane,
                "Sync data before exiting?",
                "Some data is saved locally but not synced with Dropbox, would you like to sync before exiting?",
                false, null
        );
        Optional<ModalButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ModalButtonType.YES;
    }

    static void enableStyle(Node node, String style) {
        ObservableList<String> styleClass = node.getStyleClass();
        if (!styleClass.contains(style)) {
            styleClass.add(style);
        }
    }

    static void disableStyle(Node node, String style) {
        node.getStyleClass().remove(style);
    }

    static void setStyleStatus(Node node, String style, boolean set) {
        if(set)
            enableStyle(node, style);
        else
            disableStyle(node, style);
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

    private static class WeightSpinnerValueFactory extends SpinnerValueFactory<Float> {

        public WeightSpinnerValueFactory() {
            // Modified from SpinnerValueFactory.DoubleSpinnerValueFactory documentation
            setConverter(new StringConverter<>() {
                private final DecimalFormat df = new DecimalFormat("#.##");

                @Override
                public String toString(Float value) {
                    // If the specified value is null, return a zero-length String
                    if (value == null) {
                        return "";
                    }

                    return df.format(value);
                }

                @Override
                public Float fromString(String value) {
                    try {
                        // If the specified value is null or zero-length, return null
                        if (value == null) {
                            return null;
                        }

                        value = value.trim();

                        if (value.isEmpty()) {
                            return null;
                        }

                        // Perform the requested parsing
                        return df.parse(value).floatValue();
                    } catch (ParseException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            });
        }

        @Override
        public void decrement(int i) {
            Float value = getValue();

            if (value != null)
                setValue(Math.max(value - i, 0f));
            else
                setValue(0f);
        }

        @Override
        public void increment(int i) {
            Float value = getValue();

            if (value != null)
                setValue(value + i);
            else
                setValue(0f);
        }
    }
}
