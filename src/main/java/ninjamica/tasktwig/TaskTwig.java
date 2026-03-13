package ninjamica.tasktwig;

import com.dropbox.core.*;
import com.dropbox.core.json.JsonReader;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.WriteMode;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.util.Callback;
import tools.jackson.core.JsonEncoding;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
    public record TwigJsonNode(JsonNode node, int version) {}

    private static final String DBX_API_KEY = "ul8ujplgavm586q";
    private static final File DATA_DIR = new File("data");
    private static final File DBX_DIR = new File(DATA_DIR.getPath() + "/dbx");
    private static final File DBX_CRED_FILE = new File(DBX_DIR.getPath() + "/credential.app");
    private static final File COMMIT_FILE = new File(DATA_DIR.getPath()+"/commit.json");
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
    private static LocalTime dayStart = LocalTime.of(5,00);
    private static LocalTime nightStart = LocalTime.of(18,00);
    private static boolean useFXThread = false;

    private ObservableMap<LocalDate, Sleep> sleepRecords;
    private ObservableList<Workout> workoutRecords;
    private ObservableList<Exercise> exerciseList;
    private ObservableList<Task> taskList;
    private ObservableList<TwigList> twigLists;
    private ObservableList<Routine> routineList;
    private ObservableMap<LocalDate, Journal> journalMap;
    private final ObjectProperty<LocalDateTime> sleepStart = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime> workoutStart = new SimpleObjectProperty<>();
    private final ObjectMapper mapper = new ObjectMapper();

    private DbxCredential dbxCredential;
    private final ObjectProperty<DbxClientV2> dbxClient = new SimpleObjectProperty<>();
    private final StringProperty dbxName = new SimpleStringProperty("No active account");
    private DbxPKCEWebAuth currentDbxAuthAttempt;


    public TaskTwig() {
        dbxClient.addListener((ob, o, n) -> {
            try {
                if (n == null) {
                    dbxName.set("No active account");
                }
                else {
                    dbxName.set(n.users().getCurrentAccount().getName().getDisplayName());
                }
            } catch (DbxException e) {
                throw new RuntimeException(e);
            }
        });

        if (!DATA_DIR.exists())
            DATA_DIR.mkdirs();

        if (!DBX_DIR.exists())
            DBX_DIR.mkdirs();

        this.readFromFile();
        TaskTwig.instance = this;

        authDbxFromFile();
    }

    static TaskTwig instance() {
        return TaskTwig.instance;
    }

    public static void setDayStart(LocalTime dayStart) {
        TaskTwig.dayStart = dayStart;
    }

    public static LocalTime getNightStart() {
        return TaskTwig.nightStart;
    }

    public static void setNightStart(LocalTime nightStart) {
        TaskTwig.nightStart = nightStart;
    }
    
    
    public static LocalDate effectiveDate(LocalDateTime date) {
        if (date.toLocalTime().isBefore(dayStart))
            return date.toLocalDate().minusDays(1);
        
        else
            return date.toLocalDate();
    }

    public static LocalDate effectiveDate() {
        if (LocalTime.now().isBefore(dayStart))
            return LocalDate.now().minusDays(1);
        else
            return LocalDate.now();
    }

    public static boolean isNight(LocalTime time) {
        if (time.isBefore(dayStart))
            return true;

        else
            return time.isAfter(nightStart);
    }

    public static boolean isNight() {
        return TaskTwig.isNight(LocalTime.now());
    }

    static boolean useFxThread() {
        return TaskTwig.useFXThread;
    }

    static <U> U callWithFXSafety(Supplier<U> supplier) {
        if (TaskTwig.useFXThread)
            return CompletableFuture.supplyAsync(supplier, Platform::runLater).join();
        else
            return supplier.get();
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

    public void finishWorkout(Map<Exercise, Integer> exercises, LocalDateTime finishTime) {
        workoutRecords.add(new Workout(workoutStart.getValue(), finishTime, exercises));
        workoutStart.setValue(null);
    }

    public boolean isWorkingOut() {
        return workoutStart.getValue() != null;
    }

    public ReadOnlyObjectProperty<LocalDateTime> workoutStart() {
        return workoutStart;
    }

    public void finishWorkout(Map<Exercise, Integer> exercises) {
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

    public ReadOnlyStringProperty dbxName() {
        return dbxName;
    }

    public Journal todaysJournal() {
        journalMap.putIfAbsent(effectiveDate(), new Journal());
        return journalMap.get(effectiveDate());
    }

    public void printSleepRecords() {
        System.out.println("Sleep Records\n----------");
        for (Map.Entry<LocalDate, Sleep> sleep : sleepRecords.entrySet()) {
            System.out.println(sleep.getKey() + ": " + (sleep.getValue().length().toMinutes()/60.0) + " hours");
        }
    }

    public void printWorkoutRecords() {
        System.out.println("Workout Records\n----------");

        for (Workout workout : workoutRecords) {
            System.out.println("  " + workout.start().toLocalDate() + workout.start().toLocalTime().truncatedTo(ChronoUnit.MINUTES) + " - " + workout.end().toLocalTime().truncatedTo(ChronoUnit.MINUTES) + ": " + workout.length().toMinutes() + " minutes:");

            for (Map.Entry<Exercise, Integer> exercise : workout.exercises().entrySet()) {
                System.out.println("\t" + exercise.getKey().name() + ": " + exercise.getValue() + " " + exercise.getKey().unit().displayName);
            }
        }
    }

    public void printExercises() {
        System.out.println("Exercises\n----------");

        for (Exercise exercise : exerciseList) {
            System.out.println("\t" + exercise.name());
        }
    }

    public void printAllTasks() {
        System.out.println("Tasks\n----------");

        for (Task task : taskList) {
            if (task.isDone()) {
                System.out.println("\t" + task.name() + " done!");
            }
            else {
                System.out.println("\t" + task.name() + " due " + task.getInterval().next() + ((task.dueTime() == null) ? "" : (" at " + task.dueTime())));
            }
        }
    }

    public void saveToFileFX() {
        TaskTwig.useFXThread = true;
        saveToFiles();
        TaskTwig.useFXThread = false;
    }

    public void saveToFiles() {
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
                Map<DataFile, String> fileHashes = new HashMap<>();
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

    private void readFromFile() {
        // Parse sleep records
        SortedMap<LocalDate, Sleep> sleepMap = new TreeMap<>();
        try (JsonParser parser = mapper.createParser(DataFile.SLEEP.file)) {
            parser.nextToken();
            assertEqual(parser.nextName(), "version");
            int version = parser.nextIntValue(0);

            assertEqual(parser.nextName(), "sleepProgressStart");
            if (parser.nextToken() == JsonToken.VALUE_STRING)
                this.sleepStart.setValue(LocalDateTime.parse(parser.getString()));
            else
                assertEqual(parser.currentToken(), JsonToken.VALUE_NULL);

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
            else
                assertEqual(parser.currentToken(), JsonToken.VALUE_NULL);

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
        this.taskList = FXCollections.observableArrayList();
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
        this.routineList = FXCollections.observableArrayList();
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
        try (JsonParser parser = mapper.createParser(DataFile.JOURNAL.file)) {
            parser.nextToken();
            assertEqual(parser.nextName(), "version");
            int version =  parser.nextIntValue(0);

            SortedMap<LocalDate, Journal> journalMap = new TreeMap<>();
            assertEqual(parser.nextName(), "journals");
            parser.nextToken();
            parseJsonMap(journalMap, parser, LocalDate::parse, Journal::new, version);
            this.journalMap = FXCollections.observableMap(journalMap);
        } catch (JsonAssertException | JacksonIOException e) {
            this.journalMap = FXCollections.observableMap(new TreeMap<>());
        }
    }

    private void assertEqual(Object actual, Object expected) throws JsonAssertException {
        if (!expected.equals(actual))
            throw new JsonAssertException("JSON parse encountered unexpected value. Expected: " + expected + ", actual: " + actual);
    }

    private <T> void parseJsonList(List<T> list, JsonParser parser, Callback<TwigJsonNode, T> callback, int version) {
        parser.nextToken();
        while (parser.currentToken() != JsonToken.END_ARRAY) {
            JsonNode node = parser.readValueAsTree();
            T value = callback.call(new TwigJsonNode(node, version));
            list.add(value);
            parser.nextToken();
        }
    }

    private <K, V> void parseJsonMap(Map<K, V> map, JsonParser parser, Callback<String, K> keyCallback, Callback<TwigJsonNode, V> valueCallback, int version) {
        parser.nextToken();
        while (parser.currentToken() != JsonToken.END_OBJECT) {
            K key = keyCallback.call(parser.currentName());
            parser.nextToken();
            JsonNode node = parser.readValueAsTree();
            V value = valueCallback.call(new TwigJsonNode(node, version));
            map.put(key, value);
            parser.nextToken();
        }
    }

    private List<DataFile> findOutOfDateFiles(Map<DataFile, byte[]> liveHashes) {
        List<DataFile> files = new ArrayList<>();

        try (JsonParser parser = mapper.createParser(COMMIT_FILE)) {
            CommitData diskCommit = readCommitData(parser);

            for (Map.Entry<DataFile, byte[]> liveHash : liveHashes.entrySet()) {

                if (!diskCommit.fileHashes.containsKey(liveHash.getKey())
                        || !Arrays.equals(liveHash.getValue(), Base64.getDecoder().decode(diskCommit.fileHashes.get(liveHash.getKey())))) {
                    files.add(liveHash.getKey());
                }
            }
        }

        return files;
    }

    private Map<DataFile, byte[]> genLiveDataHashes() {
        Map<DataFile, byte[]> hashes = new HashMap<>();
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
                    if (callWithFXSafety(this::isSleeping))
                        digest.update(callWithFXSafety(sleepStart::get).toString().getBytes(StandardCharsets.UTF_8));

                    Map<LocalDate, Sleep> sleepMap = callWithFXSafety(() -> new HashMap<>(sleepRecords));
                    for (Map.Entry<LocalDate, Sleep> sleepEntry : sleepMap.entrySet()) {
                        digest.update(sleepEntry.getKey().toString().getBytes(StandardCharsets.UTF_8));
                        sleepEntry.getValue().hashContents(digest);
                    }
                }
                case WORKOUT -> {
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
                    for (Task task : callWithFXSafety(() -> new ArrayList<>(taskList))) {
                        task.hashContents(digest);
                    }
                }
                case ROUTINE -> {
                    for (Routine routine : callWithFXSafety(() -> new ArrayList<>(routineList))) {
                        routine.hashContents(digest);
                    }
                }
                case LIST -> {
                    for (TwigList twigList : callWithFXSafety(() -> new ArrayList<>(twigLists))) {
                        twigList.hashContents(digest);
                    }
                }
                case JOURNAL -> {
                    Map<LocalDate, Journal> journals = callWithFXSafety(() -> new HashMap<>(journalMap));
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
        dbxClient.set(new DbxClientV2(config, credential));
    }

    public FileAction dbxSync() {
        CommitDiff commitDiff = compareCommitToDbx();
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
                } catch (IOException | DbxException e) {
                    System.out.println("Error downloading commit file: " + e.getMessage());
                }
            }
        }

        return commitDiff.action;
    }

    private record CommitData(Instant timestamp, String commitHash, Map<DataFile, String> fileHashes) {}
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

    private record CommitDiff(FileAction action, List<DataFile> files) {}
    private CommitDiff compareCommitToDbx() {
        CommitData localCommit, remoteCommit;

        try (JsonParser parser = mapper.createParser(COMMIT_FILE)) {
            localCommit = readCommitData(parser);
        }

        try (var remoteCommitFile = dbxClient.get().files().downloadBuilder("/" + COMMIT_FILE.getName()).start();
             JsonParser parser = mapper.createParser(remoteCommitFile.getInputStream()))
        {
            remoteCommit = readCommitData(parser);
        }
        catch (DbxException | JsonAssertException e) {
            System.out.println("Error loading remote commit file: " + e.getMessage());
            System.out.println("Overwriting dropbox files");

            return new CommitDiff(FileAction.UPLOAD, Arrays.asList(DataFile.values()));
        }

        List<DataFile> filesToSync = new ArrayList<>();
        if (localCommit.commitHash().equals(remoteCommit.commitHash())) {
            return new CommitDiff(FileAction.NONE, filesToSync);
        }

        FileAction fileAction = localCommit.timestamp.isAfter(remoteCommit.timestamp) ? FileAction.UPLOAD : FileAction.DOWNLOAD;

        for (Map.Entry<DataFile, String> localHash : localCommit.fileHashes().entrySet()) {
            if (!localHash.getValue().equals(remoteCommit.fileHashes().get(localHash.getKey()))) {
                filesToSync.add(localHash.getKey());
            }
        }

        return new CommitDiff(fileAction, filesToSync);
    }


    public static void main() {
        TaskTwig tracker = new TaskTwig();

        Scanner userIn = new Scanner(System.in);

        String command;
        String[] commandSplit = {""};

        while (!commandSplit[0].equals("done")) {
            System.out.print("Command: ");

            do {
                command = userIn.nextLine();
            }
            while (command.isBlank());

            System.out.println("Entered command: " + command);
            commandSplit = command.split(" ");

            switch (commandSplit[0]) {

                case "sleep":
                    switch (commandSplit[1]) {
                        case "print":
                            tracker.printSleepRecords();
                            break;

                        case "start":
                            tracker.startSleep();
                            break;

                        case "finish":
                            tracker.finishSleep();
                            break;

                        default:
                            System.out.println("Error: \"" + commandSplit[1] + "\" not recognized");
                            break;
                    }
                    break;

                case "workout":
                    switch (commandSplit[1]) {
                        case "print":
                            tracker.printWorkoutRecords();
                            break;

                        case "start":
                            tracker.startWorkout();
                            break;

                        case "finish":
                            tracker.printExercises();
                            List<Exercise> exerciseList = tracker.getExerciseList();
                            Map<Exercise, Integer> exercises = new HashMap<>();
                            String indexStr = "";

                            while (!indexStr.equals("done")) {
                                System.out.print("Index of exercise: ");

                                do {
                                    indexStr = userIn.nextLine();
                                }
                                while (indexStr.isEmpty());

                                try {
                                    int index = Integer.parseInt(indexStr);

                                    if (index >= 0 && index < exerciseList.size()) {
                                        System.out.print("Exercise Quantity: ");
                                        int quantity = userIn.nextInt();
                                        exercises.put(exerciseList.get(index), quantity);
                                    }
                                } catch (Exception e) {
                                    if (!indexStr.equals("done")) {
                                        System.out.println("Index \"" + indexStr + "\" not recognized");
                                        System.err.println(e.getMessage());
                                    }
                                }
                            }

                            tracker.finishWorkout(exercises);
                            break;

                        default:
                            System.out.println("Error: \"" + commandSplit[1] + "\" not recognized");
                            break;
                    }
                    break;

                case "exercise":
                    switch (commandSplit[1]) {
                        case "print":
                            tracker.printExercises();
                            break;

                        case "add":
                            System.out.print("Exercise name: ");
                            String name = userIn.nextLine();

                            System.out.print("Exercise unit: ");
                            Exercise.ExerciseUnit unit = Exercise.ExerciseUnit.valueOf(userIn.nextLine());

                            tracker.addExercise(name, unit);

                        default:
                            break;
                    }
                    break;

                case "task":
                    switch (commandSplit[1]) {
                        case "print":
                            tracker.printAllTasks();
                            break;

                        case "finish":
                            System.out.print("Task index: ");
                            int index = userIn.nextInt();
                            tracker.finishTask(index);
                            break;

                        default:
                            break;
                    }
                    break;

                case "status":
                    if (tracker.sleepStart.getValue() != null) {
                        System.out.println("Sleeping, started " + tracker.sleepStart.getValue().format(DateTimeFormatter.ISO_DATE_TIME));
                    }
                    if (tracker.workoutStart.getValue() != null) {
                        System.out.println("Working out, started " + tracker.workoutStart.getValue().format(DateTimeFormatter.ISO_DATE_TIME));
                    }
                    else {
                        System.out.println("Nothing in progress");
                    }
                    break;

                case "done":
                    break;

                default:
                    System.out.println("Error: \"" + commandSplit[0] + "\" not recognized");
                    break;
            }
        }

        userIn.close();
        tracker.saveToFiles();
    }

}
