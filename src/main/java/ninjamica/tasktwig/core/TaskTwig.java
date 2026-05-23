package ninjamica.tasktwig.core;

import com.dropbox.core.*;
import com.dropbox.core.json.JsonReader;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.WriteMode;
import com.gluonhq.attach.settings.SettingsService;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.scene.image.Image;
import tools.jackson.core.*;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@SuppressWarnings({"OctalInteger", "ResultOfMethodCallIgnored"})
public class TaskTwig implements Serializable {

    static class TwigJsonAssertException extends RuntimeException {
        public TwigJsonAssertException(String message) {
            super(message);
        }
    }
    static class TwigJsonVersionException extends TwigJsonAssertException {
        public TwigJsonVersionException(String message) {
            super(message);
        }
    }

    private static final String DBX_API_KEY = "ul8ujplgavm586q";
    private static final int CONFIG_VERSION = 5;

    public final class TwigFile {
        final String fileSubPath;
        private File file;

        public TwigFile(String filePath) {
            this.fileSubPath = filePath;
        }

        public File file() {
            if (file == null) {
                file = new File(storageDir, fileSubPath);
            }
            return file;
        }
    }

    @SuppressWarnings("FieldCanBeLocal")
    private final TwigFile DBX_DIR = new TwigFile("/dbx");
    private final TwigFile DBX_CRED_FILE = new TwigFile("/dbx/credential.app");
    private final TwigFile COMMIT_FILE = new TwigFile("/data/commit.json");
    private final TwigFile LAST_SYNCED_COMMIT_FILE = new TwigFile("/dbx/last_synced_commit.json");
    private final TwigFile CONFIG_FILE = new TwigFile("/config.json");

    @SuppressWarnings("FieldCanBeLocal")
    private final TwigFile DATA_DIR = new TwigFile("/data");
    private final TwigFile SLEEP_FILE = new TwigFile("/data/sleep.json");
    private final TwigFile WORKOUT_FILE = new TwigFile("/data/workout.json");
    private final TwigFile TASK_FILE = new TwigFile("/data/task.json");
    private final TwigFile ROUTINE_FILE = new TwigFile("/data/routine.json");
    private final TwigFile LIST_FILE = new TwigFile("/data/list.json");
    private final TwigFile JOURNAL_FILE = new TwigFile("/data/journal.json");

    public enum TwigDataFile {
        SLEEP,
        WORKOUT,
        TASK,
        ROUTINE,
        LIST,
        JOURNAL
    }

    private final Map<TwigDataFile, TwigFile> DATA_FILES = new HashMap<>();
    {
        DATA_FILES.put(TwigDataFile.SLEEP, SLEEP_FILE);
        DATA_FILES.put(TwigDataFile.WORKOUT, WORKOUT_FILE);
        DATA_FILES.put(TwigDataFile.TASK, TASK_FILE);
        DATA_FILES.put(TwigDataFile.ROUTINE, ROUTINE_FILE);
        DATA_FILES.put(TwigDataFile.LIST, LIST_FILE);
        DATA_FILES.put(TwigDataFile.JOURNAL, JOURNAL_FILE);
    }

    private static TaskTwig instance;
    private static boolean notFXThread = false;
    private static final ReadOnlyObjectWrapper<LocalDate> today = new ReadOnlyObjectWrapper<>(null);

    private final SettingsService settingsService;
    private final File storageDir;
    private final ObjectMapper mapper = new ObjectMapper();

    private ObservableMap<LocalDate, Sleep> sleepRecords;
    private ObservableList<Workout> workoutRecords;
    private ObservableList<Exercise> exerciseList;
    private ObservableList<Task> taskList;
    private ObservableList<TaskCategory> taskCategoryList;
    private ObservableList<TwigList> twigLists;
    private ObservableList<Routine> routineList;
    private ObservableMap<LocalDate, Journal> journalMap;
    private final ObjectProperty<LocalDateTime> sleepStart = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime> workoutStart = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalTime> dayStart = new SimpleObjectProperty<>(LocalTime.of(5,00));
    private final ObjectProperty<LocalTime> nightStart = new SimpleObjectProperty<>(LocalTime.of(18,00));
    private final BooleanProperty autoSync = new SimpleBooleanProperty(true);
    private final IntegerProperty syncInterval = new SimpleIntegerProperty(15);
    private final StringProperty visualTheme = new SimpleStringProperty();

    private DbxCredential dbxCredential;
    private final ObjectProperty<DbxClientV2> dbxClient = new SimpleObjectProperty<>();
    private DbxPKCEWebAuth currentDbxAuthAttempt;
    private CommitData lastSyncedCommit;


    public TaskTwig(SettingsService settingsService, File dataStorageDir) {
        TaskTwig.instance = this;
        this.settingsService = settingsService;
        storageDir = dataStorageDir;

        if (!DATA_DIR.file().exists())
            DATA_DIR.file().mkdirs();

        if (!DBX_DIR.file().exists())
            DBX_DIR.file().mkdirs();

        readSettings();
        readDataFiles();
        lastSyncedCommit = readLastSyncedCommitData();

        dayStart.subscribe((oldStart, newStart) -> {
            System.out.println(oldStart + " -> " + newStart);
            updateToday();
            if (newStart != oldStart)
                settingsService.store("dayStart", newStart.toString());
//                writeConfigFile();
        });
        nightStart.subscribe((oldStart, newStart) -> {
            System.out.println(oldStart + " -> " + newStart);
            if (newStart != oldStart)
                settingsService.store("nightStart", newStart.toString());
//                writeConfigFile();
        });
        autoSync.subscribe((oldVal, newVal) -> {
            System.out.println(oldVal + " -> " + newVal);
            if (newVal != oldVal)
                settingsService.store("autoSync", newVal.toString());
//                writeConfigFile();
        });
        syncInterval.subscribe((oldVal, newVal) -> {
            System.out.println(oldVal + " -> " + newVal);
            if (!Objects.equals(newVal, oldVal))
                settingsService.store("syncInterval", newVal.toString());
//                writeConfigFile();
        });
        visualTheme.subscribe((oldVal, newVal) -> {
            System.out.println(oldVal + " -> " + newVal);
            if (!Objects.equals(newVal, oldVal))
                settingsService.store("theme", newVal);
//                writeConfigFile();
        });
    }

    static TaskTwig instance() {
        return TaskTwig.instance;
    }

    public ObservableValue<LocalTime> getDayStart() {
        return dayStart;
    }

    public void setDayStart(LocalTime dayStart) {
        this.dayStart.set(dayStart);
    }

    public ObservableValue<LocalTime> getNightStart() {
        return nightStart;
    }

    public void setNightStart(LocalTime nightStart) {
        this.nightStart.set(nightStart);
    }


    public void updateToday() {
        LocalDate date;
        if (LocalTime.now().isBefore(dayStart.get()))
            date = LocalDate.now().minusDays(1);
        else
            date = LocalDate.now();

        if (!date.equals(today.get()))
            today.setValue(date);
    }

    public static ObjectExpression<LocalDate> todayObservable() {
        instance.updateToday();
        return today.getReadOnlyProperty();
    }

    public static LocalDate today() {
        instance.updateToday();
        return today.getValue();
    }

    public static LocalDate effectiveDate(LocalDateTime date) {
        if (date.toLocalTime().isBefore(instance.dayStart.get()))
            return date.toLocalDate().minusDays(1);
        
        else
            return date.toLocalDate();
    }

    public static boolean isNight(LocalTime time) {
        if (time.isBefore(instance.dayStart.get()))
            return true;

        else
            return time.isAfter(instance.nightStart.get());
    }

    public static boolean isNight() {
        return TaskTwig.isNight(LocalTime.now());
    }

    static boolean notFxThread() {
        return TaskTwig.notFXThread;
    }

    static void runWithFXSafety(Runnable runnable) {
        if (!Platform.isFxApplicationThread() && TaskTwig.notFXThread)
            CompletableFuture.runAsync(runnable, Platform::runLater).join();
        else
            runnable.run();
    }

    static <U> U supplyWithFXSafety(Supplier<U> supplier) {
        if (!Platform.isFxApplicationThread() && TaskTwig.notFXThread)
            return CompletableFuture.supplyAsync(supplier, Platform::runLater).join();
        else
            return supplier.get();
    }

    static <U> void setWithFXSafety(Consumer<U> setter, U value) {
        if (!Platform.isFxApplicationThread() && TaskTwig.notFXThread)
            CompletableFuture.runAsync(() -> setter.accept(value), Platform::runLater).join();
        else
            setter.accept(value);
    }

    public BooleanProperty autoSyncProperty() {
        return autoSync;
    }

    public IntegerProperty syncIntervalProperty() {
        return syncInterval;
    }

    public String getVisualTheme() {
        return visualTheme.get();
    }

    public void setVisualTheme(String visualTheme) {
        this.visualTheme.set(visualTheme);
    }

    public void startSleep(LocalDateTime sleepStart) {
        this.sleepStart.setValue(sleepStart);
    }

    public void finishSleep(LocalDateTime finishTime) {
        sleepRecords.put(finishTime.toLocalDate().minusDays(1), new Sleep(sleepStart.getValue(), finishTime));
        sleepStart.setValue(null);
    }

    public boolean isSleeping() {
        return this.sleepStart.getValue() != null;
    }

    public ReadOnlyObjectProperty<LocalDateTime> sleepStart() {
        return this.sleepStart;
    }

    public ObservableMap<LocalDate, Sleep> sleepRecords() {
        return sleepRecords;
    }

    public void startWorkout() {
        this.startWorkout(LocalDateTime.now());
    }

    public void startWorkout(LocalDateTime startTime) {
        workoutStart.setValue(startTime);
    }

    public void finishWorkout(SortedMap<Exercise, Integer> exercises, LocalDateTime finishTime) {
        workoutRecords.add(new Workout(workoutStart.getValue(), finishTime, exercises));
        workoutStart.setValue(null);
    }

    public boolean isWorkingOut() {
        return workoutStart.getValue() != null;
    }

    public ReadOnlyObjectProperty<LocalDateTime> workoutStart() {
        return workoutStart;
    }

    public ObservableList<Workout> workoutRecords() {
        return workoutRecords;
    }

    public List<Exercise> getExerciseList() {
        return new ArrayList<>(exerciseList);
    }

    public ObservableList<Exercise> exerciseList() {
        return exerciseList;
    }

    public ObservableList<Task> taskList() {
        return taskList;
    }

    public ObservableList<TaskCategory> getTaskCategoryList() {
        return taskCategoryList;
    }

    public ObservableList<TwigList> twigLists() {
        return twigLists;
    }

    public ObservableList<Routine> routineList() {
        return routineList;
    }

    public ObservableMap<LocalDate, Journal> journalMap() {
        return journalMap;
    }

    public ReadOnlyObjectProperty<DbxClientV2> dbxClient() {
        return dbxClient;
    }

    public Journal todaysJournal() {
        journalMap.putIfAbsent(today(), new Journal());
        return journalMap.get(today());
    }

    private void readSettings() {
        if (settingsService.retrieve("settingsVersion") == null) {
            readConfigFile();
            writeAllSettings();
        }
        else {
            switch (Integer.parseInt(settingsService.retrieve("settingsVersion"))) {
                case 5 -> {
                    setVisualTheme(settingsService.retrieve("theme"));
                    syncInterval.set(Integer.parseInt(settingsService.retrieve("syncInterval")));
                    autoSync.set(Boolean.parseBoolean(settingsService.retrieve("autoSync")));
                    dayStart.set(LocalTime.parse(settingsService.retrieve("dayStart")));
                    nightStart.set(LocalTime.parse(settingsService.retrieve("nightStart")));
                }
            }
        }
    }

    private void writeAllSettings() {
        settingsService.store("settingsVersion", Integer.toString(CONFIG_VERSION));
        settingsService.store("dayStart", dayStart.get().toString());
        settingsService.store("nightStart", nightStart.get().toString());
        settingsService.store("autoSync", autoSync.getValue().toString());
        settingsService.store("syncInterval", syncInterval.getValue().toString());

        if (visualTheme.get() != null) {
            settingsService.store("theme", visualTheme.get());
        }
    }

    private void readConfigFile() {
        try (JsonParser parser = mapper.createParser(CONFIG_FILE.file())) {
            parser.nextToken();
            assertEqual(parser.nextName(), "version");
            int version = parser.nextIntValue(0);

            switch (version) {
                case 5 -> {
                    assertEqual(parser.nextName(), "dayStart");
                    dayStart.set(LocalTime.parse(parser.nextStringValue()));

                    assertEqual(parser.nextName(), "nightStart");
                    nightStart.set(LocalTime.parse(parser.nextStringValue()));

                    assertEqual(parser.nextName(), "autoSync");
                    autoSync.set(parser.nextBooleanValue());

                    assertEqual(parser.nextName(), "syncInterval");
                    syncInterval.set(parser.nextIntValue(15));

                    assertEqual(parser.nextName(), "theme");
                    visualTheme.set(parser.nextStringValue());
                }
                case 4 -> {
                    assertEqual(parser.nextName(), "dayStart");
                    dayStart.set(LocalTime.parse(parser.nextStringValue()));

                    assertEqual(parser.nextName(), "nightStart");
                    nightStart.set(LocalTime.parse(parser.nextStringValue()));

                    assertEqual(parser.nextName(), "autoSync");
                    autoSync.set(parser.nextBooleanValue());

                    assertEqual(parser.nextName(), "syncInterval");
                    syncInterval.set(parser.nextIntValue(15));

                    assertEqual(parser.nextName(), "theme");
                    visualTheme.set(parser.nextStringValue());

                    assertEqual(parser.nextName(), "lastSyncedHash");
                    if (parser.nextValue() == JsonToken.VALUE_STRING)
                        lastSyncedCommit = new CommitData(Instant.MIN, Base64.getDecoder().decode(parser.getValueAsString()), Collections.emptyMap());
                    else
                        lastSyncedCommit = CommitData.NONE;
                }
                case 3 -> {
                    assertEqual(parser.nextName(), "dayStart");
                    dayStart.set(LocalTime.parse(parser.nextStringValue()));

                    assertEqual(parser.nextName(), "nightStart");
                    nightStart.set(LocalTime.parse(parser.nextStringValue()));

                    assertEqual(parser.nextName(), "autoSync");
                    autoSync.set(parser.nextBooleanValue());

                    assertEqual(parser.nextName(), "syncInterval");
                    syncInterval.set(parser.nextIntValue(15));

                    assertEqual(parser.nextName(), "lastSyncedHash");
                    if (parser.nextValue() == JsonToken.VALUE_STRING)
                        lastSyncedCommit = new CommitData(Instant.MIN, Base64.getDecoder().decode(parser.getValueAsString()), Collections.emptyMap());
                    else
                        lastSyncedCommit = CommitData.NONE;
                }
                case 2 -> {
                    assertEqual(parser.nextName(), "dayStart");
                    dayStart.set(LocalTime.parse(parser.nextStringValue()));

                    assertEqual(parser.nextName(), "nightStart");
                    nightStart.set(LocalTime.parse(parser.nextStringValue()));

                    assertEqual(parser.nextName(), "autoSync");
                    autoSync.set(parser.nextBooleanValue());

                    assertEqual(parser.nextName(), "lastSyncedHash");
                    if (parser.nextValue() == JsonToken.VALUE_STRING)
                        lastSyncedCommit = new CommitData(Instant.MIN, Base64.getDecoder().decode(parser.getValueAsString()), Collections.emptyMap());
                    else
                        lastSyncedCommit = CommitData.NONE;
                }
                case 1 -> {
                    assertEqual(parser.nextName(), "dayStart");
                    dayStart.set(LocalTime.parse(parser.nextStringValue()));

                    assertEqual(parser.nextName(), "nightStart");
                    nightStart.set(LocalTime.parse(parser.nextStringValue()));

                    autoSync.set(true);

                    assertEqual(parser.nextName(), "lastSyncedHash");
                    if (parser.nextValue() == JsonToken.VALUE_STRING)
                        lastSyncedCommit = new CommitData(Instant.MIN, Base64.getDecoder().decode(parser.getValueAsString()), Collections.emptyMap());
                    else
                        lastSyncedCommit = CommitData.NONE;
                }
                default -> throw new TwigJsonVersionException("Unsupported config file version: " + version);
            }
        }
        catch (JacksonIOException e) {
            System.out.println("Error reading config file: " + e.getMessage());
        }
    }

//    private void writeConfigFile() {
//        System.out.println("Writing config file");
//        try (JsonGenerator generator = mapper.createGenerator(CONFIG_FILE.file(), JsonEncoding.UTF8)) {
//            generator.writeStartObject();
//
//            generator.writeNumberProperty("version", CONFIG_VERSION);
//            generator.writePOJOProperty("dayStart", dayStart.get());
//            generator.writePOJOProperty("nightStart", nightStart.get());
//            generator.writeBooleanProperty("autoSync", autoSync.get());
//            generator.writeNumberProperty("syncInterval", syncInterval.get());
//            generator.writeStringProperty("theme", visualTheme.get());
//
//            generator.writeEndObject();
//        }
//    }

    private void readDataFiles() {
        // Parse sleep records
        SortedMap<LocalDate, Sleep> sleepMap = new TreeMap<>();
        try (JsonParser parser = mapper.createParser(SLEEP_FILE.file())) {
            parser.nextToken();
            assertEqual(parser.nextName(), "version");
            int version = parser.nextIntValue(0);

            assertEqual(parser.nextName(), "sleepProgressStart");
            if (parser.nextToken() == JsonToken.VALUE_STRING)
                this.sleepStart.setValue(LocalDateTime.parse(parser.getString()));
            else {
                this.sleepStart.setValue(null);
                assertEqual(parser.currentToken(), JsonToken.VALUE_NULL);
            }

            assertEqual(parser.nextName(), "sleepRecords");
            parser.nextToken();
            parseJsonMap(sleepMap, parser, LocalDate::parse, node -> new Sleep(node, version));
        }
        catch (TwigJsonAssertException | JacksonIOException e) {
            System.err.println(e.getLocalizedMessage());
            this.sleepStart.setValue(null);
            sleepMap.clear();
        }
        finally {
            this.sleepRecords = FXCollections.observableMap(sleepMap);
        }

        // Parse workout records
        this.exerciseList = FXCollections.observableArrayList();
        this.workoutRecords = FXCollections.observableArrayList();
        try (JsonParser parser = mapper.createParser(WORKOUT_FILE.file())) {
            parser.nextToken();
            assertEqual(parser.nextName(), "version");
            int version = parser.nextIntValue(0);

            assertEqual(parser.nextName(), "workoutProgressStart");
            if (parser.nextToken() == JsonToken.VALUE_STRING)
                this.workoutStart.setValue(LocalDateTime.parse(parser.getString()));
            else {
                this.workoutStart.setValue(null);
                assertEqual(parser.currentToken(), JsonToken.VALUE_NULL);
            }

            assertEqual(parser.nextName(), "exercises");
            parser.nextToken();
            parseJsonList(this.exerciseList, parser, node -> new Exercise(node, version));

            assertEqual(parser.nextName(), "workoutRecords");
            parser.nextToken();
            parseJsonList(this.workoutRecords, parser, node -> new Workout(node, version));
        } catch (TwigJsonAssertException | JacksonIOException e) {
            System.err.println(e.getLocalizedMessage());
            this.exerciseList.clear();
            this.workoutStart.setValue(null);
            this.workoutRecords.clear();
        }

        // Parse tasks
        this.taskCategoryList = FXCollections.observableArrayList();
        taskList = FXCollections.observableArrayList(
                task -> new Observable[] {task.isDoneObservable(), task.isTodayObservable()});
        try (JsonParser parser = mapper.createParser(TASK_FILE.file())) {
            parser.nextToken();

            assertEqual(parser.nextName(), "version");
            int version =  parser.nextIntValue(0);

            assertEqual(parser.nextName(), "categories");
            parser.nextToken();
            TaskTwig.parseJsonList(taskCategoryList, parser, node ->  new TaskCategory(node, version));

//            assertEqual(parser.nextName(), "tasks");
//            parser.nextToken();
//            parseJsonList(taskList, parser, node -> new Task(node, version));

            for (TaskCategory category : taskCategoryList) {
                taskList.addAll(category.getTasks());
            }

        } catch (TwigJsonAssertException | JacksonIOException e) {
            System.err.println(e.getLocalizedMessage());
            taskList.clear();
        }
//        for (LegacyTask task : taskList) {
//            taskList.add(new Task(task));
//        }

        // Parse lists
        this.twigLists = FXCollections.observableArrayList();
        try (JsonParser parser = mapper.createParser(LIST_FILE.file())) {
            parser.nextToken();
            assertEqual(parser.nextName(), "version");
            int version =  parser.nextIntValue(0);

            assertEqual(parser.nextName(), "lists");
            parser.nextToken();
            parseJsonList(this.twigLists, parser, node -> new TwigList(node, version));
        } catch (TwigJsonAssertException | JacksonIOException e) {
            System.err.println(e.getLocalizedMessage());
            this.twigLists.clear();
        }

        // Parse routines
        this.routineList = FXCollections.observableArrayList(routine -> new Observable[] {routine.interval()});
        try (JsonParser parser = mapper.createParser(ROUTINE_FILE.file())) {
            parser.nextToken();
            assertEqual(parser.nextName(), "version");
            int version =  parser.nextIntValue(0);

            assertEqual(parser.nextName(), "routines");
            parser.nextToken();
            parseJsonList(this.routineList, parser, node -> new Routine(node, version));
        } catch (TwigJsonAssertException | JacksonIOException e) {
            System.err.println(e.getLocalizedMessage());
            this.routineList.clear();
        }

//        for (Routine routine : routineList) {
//            taskList.add(new Task(routine));
//        }

        // Parse journals
        SortedMap<LocalDate, Journal> journals = new TreeMap<>();
        try (JsonParser parser = mapper.createParser(JOURNAL_FILE.file())) {
            parser.nextToken();
            assertEqual(parser.nextName(), "version");
            int version =  parser.nextIntValue(0);

            assertEqual(parser.nextName(), "journals");
            parser.nextToken();
            parseJsonMap(journals, parser, LocalDate::parse, node -> new Journal(node, version));
        } catch (TwigJsonAssertException | JacksonIOException e) {
            System.err.println(e.getLocalizedMessage());
            journals.clear();
        }
        this.journalMap = FXCollections.observableMap(journals);
    }

    public void saveToFileFX() {
        TaskTwig.notFXThread = true;
        saveToDataFiles();
        TaskTwig.notFXThread = false;
    }

    public void saveToDataFiles() {
        CommitData liveCommit = genLiveCommitData();
        List<TwigDataFile> saveFiles = findOutOfDateFiles(liveCommit);
        System.out.println("Saving files: " + saveFiles);

        for (TwigDataFile file : saveFiles) {
            switch (file) {
                case SLEEP -> {
                    try (JsonGenerator generator = mapper.createGenerator(SLEEP_FILE.file(), JsonEncoding.UTF8)) {
                        generator.writeStartObject();

                        generator.writeNumberProperty("version", Sleep.VERSION);
                        generator.writePOJOProperty("sleepProgressStart", this.sleepStart.getValue());
                        generator.writePOJOProperty("sleepRecords", this.sleepRecords);
                        generator.writeEndObject();
                    }
                }
                case WORKOUT -> {
                    try (JsonGenerator generator = mapper.createGenerator(WORKOUT_FILE.file(), JsonEncoding.UTF8)) {
                        generator.writeStartObject();

                        generator.writeNumberProperty("version", Workout.VERSION);
                        generator.writePOJOProperty("workoutProgressStart", this.workoutStart.getValue());
                        generator.writePOJOProperty("exercises", this.exerciseList);
                        generator.writePOJOProperty("workoutRecords", this.workoutRecords);

                        generator.writeEndObject();
                    }
                }
                case TASK -> {
                    try (JsonGenerator generator = mapper.createGenerator(TASK_FILE.file(), JsonEncoding.UTF8)) {
                        generator.writeStartObject();

                        generator.writeNumberProperty("version", Task.VERSION);

                        generator.writeArrayPropertyStart("categories");
                        for (TaskCategory category : taskCategoryList) {
                            generator.writePOJO(category);
                        }
                        generator.writeEndArray();

//                        generator.writeArrayPropertyStart("tasks");
//                        for (Task task : taskList) {
//                            generator.writePOJO(task);
//                        }
//                        generator.writeEndArray();

                        generator.writeEndObject();
                    }
                }
                case LIST -> {
                    try (JsonGenerator generator = mapper.createGenerator(LIST_FILE.file(), JsonEncoding.UTF8)) {
                        generator.writeStartObject();

                        generator.writeNumberProperty("version", TwigList.VERSION);

                        generator.writeArrayPropertyStart("lists");
                        for (TwigList list : twigLists) {
                            generator.writePOJO(list);
                        }
                        generator.writeEndArray();

                        generator.writeEndObject();
                    }
                }
                case ROUTINE -> {
                    try (JsonGenerator generator = mapper.createGenerator(ROUTINE_FILE.file(), JsonEncoding.UTF8)) {
                        generator.writeStartObject();

                        generator.writeNumberProperty("version", Routine.VERSION);

                        generator.writeArrayPropertyStart("routines");
                        for (Routine routine : routineList) {
                            generator.writePOJO(routine);
                        }
                        generator.writeEndArray();

                        generator.writeEndObject();
                    }
                }
                case JOURNAL -> {
                    try (JsonGenerator generator = mapper.createGenerator(JOURNAL_FILE.file(), JsonEncoding.UTF8)) {
                        generator.writeStartObject();

                        generator.writeNumberProperty("version", Journal.VERSION);

                        generator.writeObjectPropertyStart("journals");
                        for (Map.Entry<LocalDate, Journal> journalEntry : journalMap.entrySet()) {
                            if (!journalEntry.getValue().isEmpty()) {
                                generator.writePOJOProperty(journalEntry.getKey().toString(), journalEntry.getValue());
                            }
                        }
                        generator.writeEndObject();

                        generator.writeEndObject();
                    }
                }
            }
        }

        if (!saveFiles.isEmpty()) {
            writeCommitData(liveCommit, COMMIT_FILE);
        }
    }

    static void assertEqual(Object actual, Object expected) throws TwigJsonAssertException {
        if (!expected.equals(actual))
            throw new TwigJsonAssertException("JSON parse encountered unexpected value. Expected: " + expected + ", actual: " + actual);
    }

    static void requireJsonProperty(JsonParser parser, String name) throws TwigJsonAssertException {
        if (!parser.nextName().equals(name)) {
            System.err.println("Json parser expected property \"" + name + "\" but found: \"" + parser.currentToken().asString() + "\"");
            throw new TwigJsonAssertException(
                    "Json parser expected property \"" + name + "\" but found: \"" + parser.currentToken().asString() + "\"");
        }
    }

    static <T> void parseJsonList(List<T> list, JsonParser parser, Function<JsonNode, T> valueConstructor) {
        parser.nextToken();
        while (parser.currentToken() != JsonToken.END_ARRAY) {
            JsonNode node = parser.readValueAsTree();
            T value = valueConstructor.apply(node);
            list.add(value);
            parser.nextToken();
        }
    }

    static <K, V> void parseJsonMap(Map<K, V> map, JsonParser parser,
                                     Function<String, K> keyParser, Function<JsonNode, V> valueConstructor) {
        parser.nextToken();
        while (parser.currentToken() != JsonToken.END_OBJECT) {
            K key = keyParser.apply(parser.currentName());
            parser.nextToken();
            JsonNode node = parser.readValueAsTree();
            V value = valueConstructor.apply(node);
            map.put(key, value);
            parser.nextToken();
        }
    }


// ----------------------------------------------- Hashing and Commits -------------------------------------------------

    public record CommitData(Instant timestamp, byte[] commitHash, Map<TwigDataFile, byte[]> fileHashes) {

        public static final CommitData NONE = new CommitData(null, null, Collections.emptyMap());
    }

    private byte[] hashLiveDataFile(TwigDataFile file) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            switch (file) {
                case SLEEP -> {
                    digest.update((byte) Sleep.VERSION);

                    if (supplyWithFXSafety(this::isSleeping))
                        digest.update(supplyWithFXSafety(sleepStart::get).toString().getBytes(StandardCharsets.UTF_8));

                    SortedMap<LocalDate, Sleep> sleepMap = supplyWithFXSafety(() -> new TreeMap<>(sleepRecords));
                    for (Map.Entry<LocalDate, Sleep> sleepEntry : sleepMap.entrySet()) {
                        digest.update(sleepEntry.getKey().toString().getBytes(StandardCharsets.UTF_8));
                        sleepEntry.getValue().hashContents(digest);
                    }
                }
                case WORKOUT -> {
                    digest.update((byte) Workout.VERSION);

                    if (supplyWithFXSafety(this::isWorkingOut))
                        digest.update(supplyWithFXSafety(workoutStart::get).toString().getBytes(StandardCharsets.UTF_8));

                    for (Exercise exercise : supplyWithFXSafety(() -> new ArrayList<>(exerciseList))) {
                        exercise.hashContents(digest);
                    }

                    for (Workout workout : supplyWithFXSafety(() -> new ArrayList<>(workoutRecords))) {
                        workout.hashContents(digest);
                    }
                }
                case TASK ->  {
                    digest.update((byte) LegacyTask.VERSION);

                    for (TaskCategory category : supplyWithFXSafety(() -> new ArrayList<>(taskCategoryList))) {
                        category.hashContents(digest);
                    }
                    for (Task task : supplyWithFXSafety(() -> new ArrayList<>(taskList))) {
                        task.hashContents(digest);
                    }
                }
                case ROUTINE -> {
                    digest.update((byte) Routine.VERSION);

                    for (Routine routine : supplyWithFXSafety(() -> new ArrayList<>(routineList))) {
                        routine.hashContents(digest);
                    }
                }
                case LIST -> {
                    digest.update((byte) TwigList.VERSION);

                    for (TwigList twigList : supplyWithFXSafety(() -> new ArrayList<>(twigLists))) {
                        twigList.hashContents(digest);
                    }
                }
                case JOURNAL -> {
                    digest.update((byte) Journal.VERSION);

                    SortedMap<LocalDate, Journal> journals = supplyWithFXSafety(() -> new TreeMap<>(journalMap));
                    for (Map.Entry<LocalDate, Journal> journalEntry : journals.entrySet()) {
                        digest.update(journalEntry.getKey().toString().getBytes(StandardCharsets.UTF_8));
                        journalEntry.getValue().hashContents(digest);
                    }
                }
            }

            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private CommitData genLiveCommitData() {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");

            Map<TwigDataFile, byte[]> fileHashes = new TreeMap<>();
            for (TwigDataFile dataFile : TwigDataFile.values()) {
                byte[] hash = hashLiveDataFile(dataFile);
                fileHashes.put(dataFile, hash);
                digest.update(hash);
            }

            return new CommitData(Instant.now(), digest.digest(), fileHashes);
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private CommitData readCommitData(JsonParser parser) {
        var base64Decoder = Base64.getDecoder();

        parser.nextToken();
        assertEqual(parser.nextName(), "timestamp");
        Instant timestamp = Instant.parse(parser.nextStringValue());

        assertEqual(parser.nextName(), "commitHash");
        byte[] commitHash = base64Decoder.decode(parser.nextStringValue());

        Map<TwigDataFile, byte[]> fileHashes = new TreeMap<>();
        assertEqual(parser.nextName(), "fileHashes");
        parser.nextToken();
        parseJsonMap(fileHashes, parser, TwigDataFile::valueOf, node -> base64Decoder.decode(node.asString()));

        return new CommitData(timestamp, commitHash, fileHashes);
    }

    private void writeCommitData(CommitData commitData, TwigFile file) {
        try (JsonGenerator generator = mapper.createGenerator(file.file(), JsonEncoding.UTF8)) {
            var base64Encoder = Base64.getEncoder();

            generator.writeStartObject();

            generator.writePOJOProperty("timestamp", commitData.timestamp());
            generator.writeStringProperty("commitHash", base64Encoder.encodeToString(commitData.commitHash()));

            generator.writeObjectPropertyStart("fileHashes");

            for (Map.Entry<TwigDataFile, byte[]> hash : commitData.fileHashes().entrySet()) {
                generator.writeStringProperty(hash.getKey().toString(), base64Encoder.encodeToString(hash.getValue()));
            }

            generator.writeEndObject();
            generator.writeEndObject();
        }
    }

    private List<TwigDataFile> findOutOfDateFiles(CommitData liveCommit) {

        try (JsonParser parser = mapper.createParser(COMMIT_FILE.file())) {
            List<TwigDataFile> files = new ArrayList<>();
            CommitData diskCommit = readCommitData(parser);

            for (Map.Entry<TwigDataFile, byte[]> liveHash : liveCommit.fileHashes().entrySet()) {

                if (!diskCommit.fileHashes.containsKey(liveHash.getKey())
                        || !Arrays.equals(liveHash.getValue(), diskCommit.fileHashes().get(liveHash.getKey()))) {
                    files.add(liveHash.getKey());
                }
            }

            return files;
        }
        catch (TwigJsonAssertException | JacksonException e) {
            System.out.println("Failed reading parse data: " + e.getMessage());
            System.out.println("Skipped reading parse data");

            return List.of(TwigDataFile.values());
        }
    }

    private CommitData readLocalCommitData() {
        try (JsonParser parser = mapper.createParser(COMMIT_FILE.file())) {
            return readCommitData(parser);
        }
        catch (JacksonIOException | TwigJsonAssertException e) {
            System.out.println("Couldn't read local commit file: " + e.getMessage());
            return null;
        }
    }

    private CommitData readDbxCommitData() {
        try (var remoteCommitFile = dbxClient.get().files().downloadBuilder("/" + COMMIT_FILE.file().getName()).start();
             JsonParser parser = mapper.createParser(remoteCommitFile.getInputStream()))
        {
            return readCommitData(parser);
        }
        catch (DbxException | TwigJsonAssertException e) {
            System.out.println("Couldn't read remote commit file: " + e.getMessage());
            return null;
        }
    }

    private CommitData readLastSyncedCommitData() {
        try (JsonParser parser = mapper.createParser(LAST_SYNCED_COMMIT_FILE.file())) {
            return readCommitData(parser);
        }
        catch (JacksonIOException | TwigJsonAssertException e) {
            System.out.println("Couldn't read last synced commit file: " + e.getMessage());
            return CommitData.NONE;
        }
    }


// -------------------------------------------- Comparing and Syncing Data ---------------------------------------------

    public enum FileAction {
        DOWNLOAD,
        UPLOAD,
        MERGE,
        CONFLICT,
        NONE
    }
    public record CommitDiff(FileAction action, Map<TwigDataFile, FileAction> files,
                             CommitData localCommitData, CommitData remoteCommitData) {}

    @FunctionalInterface
    public interface SyncConflictCallback {
        FileAction getUserChoice(FileAction overallAction, Map<TwigDataFile, FileAction> fileActions);
    }
    public CommitDiff compareCommitToDbx(SyncConflictCallback conflictCallback) {
        Map<TwigDataFile, FileAction> fileSyncActions = new HashMap<>();
        CommitData localCommit = readLocalCommitData();
        CommitData remoteCommit = readDbxCommitData();
        FileAction fileAction = FileAction.NONE;

        if (localCommit == null || remoteCommit == null || Arrays.equals(localCommit.commitHash(), remoteCommit.commitHash())) {
            if (localCommit == null && remoteCommit != null) {
                Arrays.stream(TwigDataFile.values()).forEach(file -> fileSyncActions.put(file, FileAction.DOWNLOAD));
                fileAction = FileAction.DOWNLOAD;
            }
            else if (localCommit != null && remoteCommit == null) {
                Arrays.stream(TwigDataFile.values()).forEach(file -> fileSyncActions.put(file, FileAction.UPLOAD));
                fileAction = FileAction.UPLOAD;
            }
        }
        else {

            for (TwigDataFile file : TwigDataFile.values()) {
                byte[] localHash = localCommit.fileHashes.get(file);
                byte[] remoteHash = remoteCommit.fileHashes.get(file);
                byte[] lastSyncedHash = lastSyncedCommit.fileHashes.get(file);

                if (!Arrays.equals(localHash, remoteHash)) {
                    if (Arrays.equals(remoteHash, lastSyncedHash)) {
                        fileSyncActions.put(file, FileAction.UPLOAD);

                        if (fileAction == FileAction.NONE)
                            fileAction = FileAction.UPLOAD;
                        else if (fileAction == FileAction.DOWNLOAD)
                            fileAction = FileAction.MERGE;
                    }
                    else if (Arrays.equals(localHash, lastSyncedHash)) {
                        fileSyncActions.put(file, FileAction.DOWNLOAD);

                        if (fileAction == FileAction.NONE)
                            fileAction = FileAction.DOWNLOAD;
                        else if (fileAction == FileAction.UPLOAD)
                            fileAction = FileAction.MERGE;
                    }
                    else {
                        fileSyncActions.put(file, FileAction.CONFLICT);
                        fileAction = FileAction.CONFLICT;
                    }
                }
            }

            if (fileAction == FileAction.MERGE || fileAction == FileAction.CONFLICT) {
                if (conflictCallback != null) {
                    fileAction = conflictCallback.getUserChoice(fileAction, fileSyncActions);
                    System.out.println("User chose: " + fileAction);
                }
            }
        }

        return new CommitDiff(fileAction, fileSyncActions, localCommit, remoteCommit);
    }

    public void dbxSync(CommitDiff commitDiff) {
        System.out.println("sync: " + commitDiff.action() + " " + commitDiff.files());
        if (commitDiff.action() == FileAction.DOWNLOAD || commitDiff.action() == FileAction.UPLOAD || commitDiff.action() == FileAction.MERGE) {
            for (Map.Entry<TwigDataFile, FileAction> file : commitDiff.files().entrySet()) {
                try {
                    if (commitDiff.action() == FileAction.DOWNLOAD) {
                        dbxDownloadFile(DATA_FILES.get(file.getKey()));
                    }
                    else if (commitDiff.action() == FileAction.UPLOAD || file.getValue() == FileAction.UPLOAD) {
                        dbxUploadFile(DATA_FILES.get(file.getKey()));
                    }
                    else if (file.getValue() == FileAction.DOWNLOAD) {
                        dbxDownloadFile(DATA_FILES.get(file.getKey()));
                    }
                } catch (IOException | DbxException e) {
                    System.err.println("Failed to " + file.getValue() + " file: " + file.getKey());
                    System.err.println(e.getMessage());
                }
            }
        }

        try {
            switch (commitDiff.action()) {
                case DOWNLOAD -> {
                    lastSyncedCommit = commitDiff.remoteCommitData();
                    writeCommitData(lastSyncedCommit, COMMIT_FILE);
                    writeCommitData(lastSyncedCommit, LAST_SYNCED_COMMIT_FILE);

                    readDataFiles();
                }
                case UPLOAD -> {
                    lastSyncedCommit = commitDiff.localCommitData();
                    writeCommitData(lastSyncedCommit, LAST_SYNCED_COMMIT_FILE);
                    dbxUploadCommitFile(LAST_SYNCED_COMMIT_FILE);
                }
                case MERGE -> {
                    readDataFiles();

                    lastSyncedCommit = genLiveCommitData();
                    writeCommitData(lastSyncedCommit, COMMIT_FILE);
                    writeCommitData(lastSyncedCommit, LAST_SYNCED_COMMIT_FILE);
                    dbxUploadCommitFile(LAST_SYNCED_COMMIT_FILE);
                }
            }
        } catch (IOException | DbxException e) {
            throw new RuntimeException(e);
        }
    }


// ----------------------------------------------------- Dropbox -------------------------------------------------------

    public String genDbxAuthUrl() {
        DbxRequestConfig config = DbxRequestConfig.newBuilder("TaskTwig/alpha").build();

        DbxAppInfo appInfo = new DbxAppInfo(DBX_API_KEY);
        currentDbxAuthAttempt = new DbxPKCEWebAuth(config, appInfo);
        DbxWebAuth.Request webAuthRequest = DbxWebAuth.newRequestBuilder()
                .withNoRedirect()
                .withTokenAccessType(TokenAccessType.OFFLINE)
                .build();

        return currentDbxAuthAttempt.authorize(webAuthRequest);
    }

    public void authDbxFromCode(String code) throws DbxException{
        if (currentDbxAuthAttempt == null) {
            return;
        }

        DbxAuthFinish authFinish = currentDbxAuthAttempt.finishFromCode(code);
        System.out.println("authFinish scopes: " + authFinish.getScope());

        dbxCredential = new DbxCredential(authFinish.getAccessToken(), authFinish.getExpiresAt(), authFinish.getRefreshToken(), DBX_API_KEY);
        initDbxClient(dbxCredential);
        currentDbxAuthAttempt = null;

        try {
            DBX_CRED_FILE.file().createNewFile();
            DbxCredential.Writer.writeToFile(dbxCredential, DBX_CRED_FILE.file());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean authDbxFromFile() {
        DbxRequestConfig config = DbxRequestConfig.newBuilder("TaskTwig/alpha").build();

        if (DBX_CRED_FILE.file().exists()) {
            try {
                dbxCredential = DbxCredential.Reader.readFromFile(DBX_CRED_FILE.file());
                if (dbxCredential.aboutToExpire()) {
                    dbxCredential.refresh(config);
                }

                initDbxClient(dbxCredential);
                return true;
            }
            catch (JsonReader.FileLoadException e) {
                System.out.println("Error reading credential file: " + e.getMessage());
                System.out.println("Reauthorizing user");
            }
            catch (DbxException e) {
                System.out.println("Error refreshing credential: " + e.getMessage());
                System.out.println("Reauthorizing user");
            }
        }

        return false;
    }

    public void dbxLogout() {
        DBX_CRED_FILE.file().delete();
        dbxCredential = null;
        dbxClient.set(null);
    }

    private void initDbxClient(DbxCredential credential) {
        DbxRequestConfig config = DbxRequestConfig.newBuilder("TaskTwig/alpha").build();
        Platform.runLater(() -> dbxClient.set(new DbxClientV2(config, credential)));
    }

    public String getDbxAccountName() throws DbxException, NullPointerException {
        return dbxClient.get().users().getCurrentAccount().getName().getDisplayName();
    }

    public Image getDbxAccountImage() throws DbxException {
        String iconUrl = dbxClient.get().users().getCurrentAccount().getProfilePhotoUrl();
        if (iconUrl != null)
            return new Image(iconUrl, 64, 64, true, true, true);
        else
            return null;
    }

    public void dbxDownloadFile(TwigFile dataFile) throws IOException, DbxException {
        try (OutputStream fileStream = new FileOutputStream(dataFile.file())) {
            dbxClient.get().files().downloadBuilder("/" + dataFile.file().getName()).download(fileStream);
        }
        catch (IOException | DbxException e) {
            System.err.println("Error downloading file " + dataFile.file().getName() + ": " + e.getMessage());
            throw e;
        }
    }

    public void dbxUploadFile(TwigFile dataFile) throws IOException, DbxException {
        try (InputStream fileStream = new FileInputStream(dataFile.file())) {
            dbxClient.get().files().uploadBuilder("/" + dataFile.file().getName()).withMode(WriteMode.OVERWRITE).uploadAndFinish(fileStream);
        }
        catch (IOException | DbxException e) {
            System.err.println("Error uploading file " + dataFile.file().getName() + ": " + e.getMessage());
            throw e;
        }
    }

    public void dbxUploadCommitFile(TwigFile commitFile) throws IOException, DbxException {
        try (InputStream fileStream = new FileInputStream(commitFile.file())) {
            dbxClient.get().files().uploadBuilder("/" + COMMIT_FILE.file().getName()).withMode(WriteMode.OVERWRITE).uploadAndFinish(fileStream);
        }
        catch (IOException | DbxException e) {
            System.err.println("Error uploading file " + commitFile.file().getName() + " as remote commit file: " + e.getMessage());
            throw e;
        }
    }
}
