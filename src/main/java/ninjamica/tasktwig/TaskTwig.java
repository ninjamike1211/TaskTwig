package ninjamica.tasktwig;

import com.dropbox.core.*;
import com.dropbox.core.json.JsonReader;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.WriteMode;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.util.Callback;
import tools.jackson.core.*;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.core.type.TypeReference;
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
import java.util.function.Supplier;

public class TaskTwig implements Serializable {

    static class JsonAssertException extends RuntimeException {
        public JsonAssertException(String message) {
            super(message);
        }
    }
    static class JsonVersionException extends JsonAssertException {
        public JsonVersionException(String message) {
            super(message);
        }
    }

    @FunctionalInterface
    interface TwigJsonConstructor<T> {
        T construct(JsonNode node, int version);
    }

    private static final String DBX_API_KEY = "ul8ujplgavm586q";
    private static final int CONFIG_VERSION = 3;

    private static final File DATA_DIR = new File("data");
    private static final File DBX_DIR = new File(DATA_DIR.getPath() + "/dbx");
    private static final File DBX_CRED_FILE = new File(DBX_DIR.getPath() + "/credential.app");
    private static final File COMMIT_FILE = new File(DATA_DIR.getPath()+"/commit.json");
    private static final File CONFIG_FILE = new File(DATA_DIR.getPath()+"/config.json");
    private enum DataFile {
        SLEEP (new File(DATA_DIR.getPath()+"/sleep.json")),
        WORKOUT (new File(DATA_DIR.getPath()+"/workout.json")),
        TASK (new File(DATA_DIR.getPath()+"/task.json")),
        ROUTINE (new File(DATA_DIR.getPath()+"/routine.json")),
        LIST (new File(DATA_DIR.getPath()+"/list.json")),
        JOURNAL (new File(DATA_DIR.getPath()+"/journal.json"));

        public final File file;
        DataFile(File file) {
            this.file = file;
        }
    }

    private static TaskTwig instance;
    private static boolean notFXThread = false;
    private static final ReadOnlyObjectWrapper<LocalDate> today = new ReadOnlyObjectWrapper<>(null);

    private final ObjectMapper mapper = new ObjectMapper();

    private ObservableMap<LocalDate, Sleep> sleepRecords;
    private ObservableList<Workout> workoutRecords;
    private ObservableList<Exercise> exerciseList;
    private ObservableList<Task> taskList;
    private ObservableList<TwigList> twigLists;
    private ObservableList<Routine> routineList;
    private ObservableMap<LocalDate, Journal> journalMap;
    private final ObjectProperty<LocalDateTime> sleepStart = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime> workoutStart = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalTime> dayStart = new SimpleObjectProperty<>(LocalTime.of(5,00));
    private final ObjectProperty<LocalTime> nightStart = new SimpleObjectProperty<>(LocalTime.of(18,00));
    private final BooleanProperty autoSync = new SimpleBooleanProperty(true);
    private final IntegerProperty syncInterval = new SimpleIntegerProperty(15);

    private DbxCredential dbxCredential;
    private final ObjectProperty<DbxClientV2> dbxClient = new SimpleObjectProperty<>();
    private DbxPKCEWebAuth currentDbxAuthAttempt;
    private String lastSyncedHash;


    public TaskTwig() {
        TaskTwig.instance = this;

        if (!DATA_DIR.exists())
            DATA_DIR.mkdirs();

        if (!DBX_DIR.exists())
            DBX_DIR.mkdirs();

        readConfigFile();
        readDataFiles();

        dayStart.subscribe((oldStart, newStart) -> {
            System.out.println(oldStart + " -> " + newStart);
            updateToday();
            if (newStart != oldStart)
                writeConfigFile();
        });
        nightStart.subscribe((oldStart, newStart) -> {
            System.out.println(oldStart + " -> " + newStart);
            if (newStart != oldStart)
                writeConfigFile();
        });
        autoSync.subscribe((oldVal, newVal) -> {
            System.out.println(oldVal + " -> " + newVal);
            if (newVal != oldVal)
                writeConfigFile();
        });
        syncInterval.subscribe((oldVal, newVal) -> {
            System.out.println(oldVal + " -> " + newVal);
            if (!Objects.equals(newVal, oldVal))
                writeConfigFile();
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

    public static ObjectExpression<LocalDate> todayValue() {
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

    static <U> U callWithFXSafety(Supplier<U> supplier) {
        if (TaskTwig.notFXThread)
            return CompletableFuture.supplyAsync(supplier, Platform::runLater).join();
        else
            return supplier.get();
    }

    public BooleanProperty autoSyncProperty() {
        return autoSync;
    }

    public IntegerProperty syncIntervalProperty() {
        return syncInterval;
    }

    public void startSleep() {
        this.startSleep(LocalDateTime.now());
    }

    public void startSleep(LocalDateTime sleepStart) {
        this.sleepStart.setValue(sleepStart);
    }

    public void finishSleep() {
        this.finishSleep(LocalDateTime.now());
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

    public void finishWorkout(SortedMap<Exercise, Integer> exercises) {
        this.finishWorkout(exercises, LocalDateTime.now());
    }

    public ObservableList<Workout> workoutRecords() {
        return workoutRecords;
    }

    public List<Exercise> getExerciseList() {
        return new ArrayList<>(exerciseList);
    }

    public void addExercise(String name, Exercise.ExerciseUnit unit) {
        exerciseList.add(new Exercise(name, unit));
    }

    public ObservableList<Exercise> exerciseList() {
        return exerciseList;
    }

    public Sleep getSleepRecord(LocalDate date) {
        return sleepRecords.get(date);
    }

    public void addTask(Task task) {
        taskList.add(task);
    }

    public void finishTask(int index) {
        taskList.get(index).setDone(true);
    }

    public ObservableList<Task> taskList() {
        return taskList;
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

    public String getDbxAccountName() throws DbxException, NullPointerException {
        return dbxClient.get().users().getCurrentAccount().getName().getDisplayName();
    }

    public Journal todaysJournal() {
        journalMap.putIfAbsent(today(), new Journal());
        return journalMap.get(today());
    }

    private void readConfigFile() {
        try (JsonParser parser = mapper.createParser(CONFIG_FILE)) {
            parser.nextToken();
            assertEqual(parser.nextName(), "version");
            int version = parser.nextIntValue(0);

            switch (version) {
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
                        lastSyncedHash = parser.getValueAsString();
                    else
                        lastSyncedHash = null;
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
                        lastSyncedHash = parser.getValueAsString();
                    else
                        lastSyncedHash = null;
                }
                case 1 -> {
                    assertEqual(parser.nextName(), "dayStart");
                    dayStart.set(LocalTime.parse(parser.nextStringValue()));

                    assertEqual(parser.nextName(), "nightStart");
                    nightStart.set(LocalTime.parse(parser.nextStringValue()));

                    autoSync.set(true);

                    assertEqual(parser.nextName(), "lastSyncedHash");
                    if (parser.nextValue() == JsonToken.VALUE_STRING)
                        lastSyncedHash = parser.getValueAsString();
                    else
                        lastSyncedHash = null;
                }
                default -> throw new JsonVersionException("Unsupported config file version: " + version);
            }
        }
        catch (JacksonIOException e) {
            System.out.println("Error reading config file: " + e.getMessage());
        }
    }

    private void writeConfigFile() {
        System.out.println("Writing config file");
        try (JsonGenerator generator = mapper.createGenerator(CONFIG_FILE, JsonEncoding.UTF8)) {
            generator.writeStartObject();

            generator.writeNumberProperty("version", CONFIG_VERSION);
            generator.writePOJOProperty("dayStart", dayStart.get());
            generator.writePOJOProperty("nightStart", nightStart.get());
            generator.writeBooleanProperty("autoSync", autoSync.get());
            generator.writeNumberProperty("syncInterval", syncInterval.get());

            if (lastSyncedHash != null)
                generator.writeStringProperty("lastSyncedHash", lastSyncedHash);
            else
                generator.writeNullProperty("lastSyncedHash");

            generator.writeEndObject();
        }
    }

    private void readDataFiles() {
        // Parse sleep records
        SortedMap<LocalDate, Sleep> sleepMap = new TreeMap<>();
        try (JsonParser parser = mapper.createParser(DataFile.SLEEP.file)) {
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
            parseJsonMap(sleepMap, parser, LocalDate::parse, Sleep::new, version);
        }
        catch (JsonAssertException | JacksonIOException e) {
            this.sleepStart.setValue(null);
            sleepMap.clear();
        }
        finally {
            this.sleepRecords = FXCollections.observableMap(sleepMap);
        }

        // Parse workout records
        this.exerciseList = FXCollections.observableArrayList();
        this.workoutRecords = FXCollections.observableArrayList();
        try (JsonParser parser = mapper.createParser(DataFile.WORKOUT.file)) {
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
            parseJsonList(this.exerciseList, parser, Exercise::new, version);

            assertEqual(parser.nextName(), "workoutRecords");
            parser.nextToken();
            parseJsonList(this.workoutRecords, parser, Workout::new, version);
        } catch (JsonAssertException | JacksonIOException e) {
            this.exerciseList.clear();
            this.workoutStart.setValue(null);
            this.workoutRecords.clear();
        }

        // Parse tasks
        this.taskList = FXCollections.observableArrayList(task -> new Observable[] {task.intervalProperty()});
        try (JsonParser parser = mapper.createParser(DataFile.TASK.file)) {
            parser.nextToken();
            assertEqual(parser.nextName(), "version");
            int version =  parser.nextIntValue(0);

            assertEqual(parser.nextName(), "tasks");
            parser.nextToken();
            parseJsonList(this.taskList, parser, Task::new, version);
        } catch (JsonAssertException | JacksonIOException e) {
            this.taskList.clear();
        }

        // Parse lists
        this.twigLists = FXCollections.observableArrayList();
        try (JsonParser parser = mapper.createParser(DataFile.LIST.file)) {
            parser.nextToken();
            assertEqual(parser.nextName(), "version");
            int version =  parser.nextIntValue(0);

            assertEqual(parser.nextName(), "lists");
            parser.nextToken();
            parseJsonList(this.twigLists, parser, TwigList::new, version);
        } catch (JsonAssertException | JacksonIOException e) {
            this.twigLists.clear();
        }

        // Parse routines
        this.routineList = FXCollections.observableArrayList(routine -> new Observable[] {routine.interval()});
        try (JsonParser parser = mapper.createParser(DataFile.ROUTINE.file)) {
            parser.nextToken();
            assertEqual(parser.nextName(), "version");
            int version =  parser.nextIntValue(0);

            assertEqual(parser.nextName(), "routines");
            parser.nextToken();
            parseJsonList(this.routineList, parser, Routine::new, version);
        } catch (JsonAssertException | JacksonIOException e) {
            this.routineList.clear();
        }

        // Parse journals
        SortedMap<LocalDate, Journal> journals = new TreeMap<>();
        try (JsonParser parser = mapper.createParser(DataFile.JOURNAL.file)) {
            parser.nextToken();
            assertEqual(parser.nextName(), "version");
            int version =  parser.nextIntValue(0);

            assertEqual(parser.nextName(), "journals");
            parser.nextToken();
            parseJsonMap(journals, parser, LocalDate::parse, Journal::new, version);
        } catch (JsonAssertException | JacksonIOException e) {
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
        Map<DataFile, byte[]> liveHashes = genLiveDataHashes();
        List<DataFile> saveFiles = findOutOfDateFiles(liveHashes);
        System.out.println("Saving files: " + saveFiles);

        for (DataFile file : saveFiles) {
            switch (file) {
                case SLEEP -> {
                    try (JsonGenerator generator = mapper.createGenerator(DataFile.SLEEP.file, JsonEncoding.UTF8)) {
                        generator.writeStartObject();

                        generator.writeNumberProperty("version", Sleep.VERSION);
                        generator.writePOJOProperty("sleepProgressStart", this.sleepStart.getValue());
                        generator.writePOJOProperty("sleepRecords", this.sleepRecords);
                        generator.writeEndObject();
                    }
                }
                case WORKOUT -> {
                    try (JsonGenerator generator = mapper.createGenerator(DataFile.WORKOUT.file, JsonEncoding.UTF8)) {
                        generator.writeStartObject();

                        generator.writeNumberProperty("version", Workout.VERSION);
                        generator.writePOJOProperty("workoutProgressStart", this.workoutStart.getValue());
                        generator.writePOJOProperty("exercises", this.exerciseList);
                        generator.writePOJOProperty("workoutRecords", this.workoutRecords);

                        generator.writeEndObject();
                    }
                }
                case TASK -> {
                    try (JsonGenerator generator = mapper.createGenerator(DataFile.TASK.file, JsonEncoding.UTF8)) {
                        generator.writeStartObject();

                        generator.writeNumberProperty("version", Task.VERSION);

                        generator.writeArrayPropertyStart("tasks");
                        for (Task task : taskList) {
                            generator.writePOJO(task);
                        }
                        generator.writeEndArray();

                        generator.writeEndObject();
                    }
                }
                case LIST -> {
                    try (JsonGenerator generator = mapper.createGenerator(DataFile.LIST.file, JsonEncoding.UTF8)) {
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
                    try (JsonGenerator generator = mapper.createGenerator(DataFile.ROUTINE.file, JsonEncoding.UTF8)) {
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
                    try (JsonGenerator generator = mapper.createGenerator(DataFile.JOURNAL.file, JsonEncoding.UTF8)) {
                        generator.writeStartObject();

                        generator.writeNumberProperty("version", Journal.VERSION);

                        generator.writePOJOProperty("journals", this.journalMap);

                        generator.writeEndObject();
                    }
                }
            }
        }

        if (!saveFiles.isEmpty()) {
            try (JsonGenerator generator = mapper.createGenerator(COMMIT_FILE, JsonEncoding.UTF8)) {
                var digest = MessageDigest.getInstance("SHA-256");
                Map<DataFile, String> fileHashes = new TreeMap<>();
                for (Map.Entry<DataFile, byte[]> hash : liveHashes.entrySet()) {
                    fileHashes.put(hash.getKey(), Base64.getEncoder().encodeToString(hash.getValue()));
                    digest.update(hash.getValue());
                }
                String commitHash = Base64.getEncoder().encodeToString(digest.digest());

                generator.writeStartObject();

                generator.writePOJOProperty("timestamp", Instant.now());
                generator.writeStringProperty("commitHash", commitHash);
                generator.writePOJOProperty("fileHashes", fileHashes);

                generator.writeEndObject();

            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void assertEqual(Object actual, Object expected) throws JsonAssertException {
        if (!expected.equals(actual))
            throw new JsonAssertException("JSON parse encountered unexpected value. Expected: " + expected + ", actual: " + actual);
    }

    private <T> void parseJsonList(List<T> list, JsonParser parser, TwigJsonConstructor<T> callback, int version) {
        parser.nextToken();
        while (parser.currentToken() != JsonToken.END_ARRAY) {
            JsonNode node = parser.readValueAsTree();
            T value = callback.construct(node, version);
            list.add(value);
            parser.nextToken();
        }
    }

    private <K, V> void parseJsonMap(Map<K, V> map, JsonParser parser, Callback<String, K> keyCallback, TwigJsonConstructor<V> valueCallback, int version) {
        parser.nextToken();
        while (parser.currentToken() != JsonToken.END_OBJECT) {
            K key = keyCallback.call(parser.currentName());
            parser.nextToken();
            JsonNode node = parser.readValueAsTree();
            V value = valueCallback.construct(node, version);
            map.put(key, value);
            parser.nextToken();
        }
    }

    private List<DataFile> findOutOfDateFiles(Map<DataFile, byte[]> liveHashes) {

        try (JsonParser parser = mapper.createParser(COMMIT_FILE)) {
            List<DataFile> files = new ArrayList<>();
            CommitData diskCommit = readCommitData(parser);

            for (Map.Entry<DataFile, byte[]> liveHash : liveHashes.entrySet()) {

                if (!diskCommit.fileHashes.containsKey(liveHash.getKey())
                        || !Arrays.equals(liveHash.getValue(), Base64.getDecoder().decode(diskCommit.fileHashes.get(liveHash.getKey())))) {
                    files.add(liveHash.getKey());
                }
            }

            return files;
        }
        catch (JsonAssertException | JacksonException e) {
            System.out.println("Failed reading parse data: " + e.getMessage());
            System.out.println("Skipped reading parse data");

            return List.of(DataFile.values());
        }
    }

    private Map<DataFile, byte[]> genLiveDataHashes() {
        Map<DataFile, byte[]> hashes = new TreeMap<>();
        for (DataFile dataFile : DataFile.values()) {
            hashes.put(dataFile, hashLiveData(dataFile));
        }
        return hashes;
    }

    private byte[] hashLiveData(DataFile file) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            switch (file) {
                case SLEEP -> {
                    digest.update((byte) Sleep.VERSION);

                    if (callWithFXSafety(this::isSleeping))
                        digest.update(callWithFXSafety(sleepStart::get).toString().getBytes(StandardCharsets.UTF_8));

                    SortedMap<LocalDate, Sleep> sleepMap = callWithFXSafety(() -> new TreeMap<>(sleepRecords));
                    for (Map.Entry<LocalDate, Sleep> sleepEntry : sleepMap.entrySet()) {
                        digest.update(sleepEntry.getKey().toString().getBytes(StandardCharsets.UTF_8));
                        sleepEntry.getValue().hashContents(digest);
                    }
                }
                case WORKOUT -> {
                    digest.update((byte) Workout.VERSION);

                    if (callWithFXSafety(this::isWorkingOut))
                        digest.update(callWithFXSafety(workoutStart::get).toString().getBytes(StandardCharsets.UTF_8));

                    for (Exercise exercise : callWithFXSafety(() -> new ArrayList<>(exerciseList))) {
                        exercise.hashContents(digest);
                    }

                    for (Workout workout : callWithFXSafety(() -> new ArrayList<>(workoutRecords))) {
                        workout.hashContents(digest);
                    }
                }
                case TASK ->  {
                    digest.update((byte) Task.VERSION);

                    for (Task task : callWithFXSafety(() -> new ArrayList<>(taskList))) {
                        task.hashContents(digest);
                    }
                }
                case ROUTINE -> {
                    digest.update((byte) Routine.VERSION);

                    for (Routine routine : callWithFXSafety(() -> new ArrayList<>(routineList))) {
                        routine.hashContents(digest);
                    }
                }
                case LIST -> {
                    digest.update((byte) TwigList.VERSION);

                    for (TwigList twigList : callWithFXSafety(() -> new ArrayList<>(twigLists))) {
                        twigList.hashContents(digest);
                    }
                }
                case JOURNAL -> {
                    digest.update((byte) Journal.VERSION);

                    SortedMap<LocalDate, Journal> journals = callWithFXSafety(() -> new TreeMap<>(journalMap));
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
            DBX_CRED_FILE.createNewFile();
            DbxCredential.Writer.writeToFile(dbxCredential, DBX_CRED_FILE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean authDbxFromFile() {
        DbxRequestConfig config = DbxRequestConfig.newBuilder("TaskTwig/alpha").build();

        if (DBX_CRED_FILE.exists()) {
            try {
                dbxCredential = DbxCredential.Reader.readFromFile(DBX_CRED_FILE);
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
        DBX_CRED_FILE.delete();
        dbxCredential = null;
        dbxClient.set(null);
    }

    private void initDbxClient(DbxCredential credential) {
        DbxRequestConfig config = DbxRequestConfig.newBuilder("TaskTwig/alpha").build();
        Platform.runLater(() -> dbxClient.set(new DbxClientV2(config, credential)));
    }

    public FileAction dbxSync(Supplier<FileAction> conflictCallback) {
        CommitDiff commitDiff = compareCommitToDbx(conflictCallback);
        return dbxSync(commitDiff);
    }
    
    public FileAction dbxSync(CommitDiff commitDiff) {
        System.out.println("commitDiff: " + commitDiff);
        switch(commitDiff.action) {
            case UPLOAD -> {
                for (DataFile dataFile : commitDiff.files) {
                    try (InputStream fileStream = new FileInputStream(dataFile.file)) {
                        dbxClient.get().files().uploadBuilder("/" + dataFile.file.getName()).withMode(WriteMode.OVERWRITE).uploadAndFinish(fileStream);
                    }
                    catch (IOException | DbxException e) {
                        System.out.println("Error uploading file " + dataFile.file.getName() + ": " + e.getMessage());
                    }
                }
                try (InputStream fileStream = new FileInputStream(COMMIT_FILE)) {
                    dbxClient.get().files().uploadBuilder("/" + COMMIT_FILE.getName()).withMode(WriteMode.OVERWRITE).uploadAndFinish(fileStream);
                    lastSyncedHash = commitDiff.localCommitData().commitHash;
                }
                catch (IOException | DbxException e) {
                    System.out.println("Error uploading commit file: " + e.getMessage());
                }
            }
            case DOWNLOAD -> {
                for (DataFile dataFile : commitDiff.files) {
                    try (OutputStream fileStream = new FileOutputStream(dataFile.file)) {
                        dbxClient.get().files().downloadBuilder("/" + dataFile.file.getName()).download(fileStream);
                    }
                    catch (IOException | DbxException e) {
                        System.out.println("Error downloading file " + dataFile.file.getName() + ": " + e.getMessage());
                    }
                }
                try (OutputStream fileStream = new FileOutputStream(COMMIT_FILE)) {
                    dbxClient.get().files().downloadBuilder("/" + COMMIT_FILE.getName()).download(fileStream);
                    lastSyncedHash = commitDiff.remoteCommitData().commitHash;
                }
                catch (IOException | DbxException e) {
                    System.out.println("Error downloading commit file: " + e.getMessage());
                }

                readDataFiles();
            }
        }

        writeConfigFile();
        return commitDiff.action;
    }

    public record CommitData(Instant timestamp, String commitHash, Map<DataFile, String> fileHashes) {}
    public enum FileAction {
        DOWNLOAD,
        UPLOAD,
        NONE
    }
    private CommitData readCommitData(JsonParser parser) {
        parser.nextToken();
        assertEqual(parser.nextName(), "timestamp");
        Instant timestamp = Instant.parse(parser.nextStringValue());

        assertEqual(parser.nextName(), "commitHash");
        String commitHash = parser.nextStringValue();

        assertEqual(parser.nextName(), "fileHashes");
        parser.nextToken();
        Map<DataFile, String> fileHashes = parser.readValueAs(new TypeReference<>() {});

        return new CommitData(timestamp, commitHash, fileHashes);
    }

    private CommitData readLocalCommitData() {
        try (JsonParser parser = mapper.createParser(COMMIT_FILE)) {
            return readCommitData(parser);
        }
        catch (JacksonIOException | JsonAssertException e) {
            System.out.println("Couldn't read local commit file: " + e.getMessage());
            return null;
        }
    }

    private CommitData readDbxCommitData() {
        try (var remoteCommitFile = dbxClient.get().files().downloadBuilder("/" + COMMIT_FILE.getName()).start();
             JsonParser parser = mapper.createParser(remoteCommitFile.getInputStream()))
        {
            return readCommitData(parser);
        }
        catch (DbxException | JsonAssertException e) {
            System.out.println("Couldn't read remote commit file: " + e.getMessage());
            return null;
        }
    }

    public record CommitDiff(FileAction action, List<DataFile> files, CommitData localCommitData, CommitData remoteCommitData) {}
    public CommitDiff compareCommitToDbx(Supplier<FileAction> conflictCallback) {
        List<DataFile> filesToSync = new ArrayList<>();
        CommitData localCommit = readLocalCommitData();
        CommitData remoteCommit = readDbxCommitData();
        FileAction fileAction;

        if (localCommit == null || remoteCommit == null || localCommit.commitHash().equals(remoteCommit.commitHash())) {
            if (localCommit == null && remoteCommit != null) {
                filesToSync.addAll(List.of(DataFile.values()));
                fileAction = FileAction.DOWNLOAD;
            }
            else if (localCommit != null && remoteCommit == null) {
                filesToSync.addAll(List.of(DataFile.values()));
                fileAction = FileAction.UPLOAD;
            }
            else {
                fileAction = FileAction.NONE;
            }
        }
        else {

            if (remoteCommit.commitHash.equals(lastSyncedHash)) {
                fileAction = FileAction.UPLOAD;
            } else if (localCommit.commitHash.equals(lastSyncedHash)) {
                fileAction = FileAction.DOWNLOAD;
            } else {
                if (conflictCallback != null) {
                    fileAction = CompletableFuture.supplyAsync(conflictCallback, Platform::runLater).join();
                    System.out.println("User chose " + fileAction);
                } else {
                    return new CommitDiff(null, filesToSync, localCommit, remoteCommit);
                }
            }

            if (fileAction == FileAction.NONE) {
                return new CommitDiff(FileAction.NONE, filesToSync, localCommit, remoteCommit);
            }

            for (Map.Entry<DataFile, String> localHash : localCommit.fileHashes().entrySet()) {
                if (!localHash.getValue().equals(remoteCommit.fileHashes().get(localHash.getKey()))) {
                    filesToSync.add(localHash.getKey());
                }
            }
        }

        return new CommitDiff(fileAction, filesToSync, localCommit, remoteCommit);
    }

}
