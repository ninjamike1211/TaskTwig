package ninjamica.tasktwig;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.WriteMode;
import com.dropbox.core.v2.users.FullAccount;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.util.Callback;
import tools.jackson.core.JsonEncoding;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

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

    private static TaskTwig instance;
    private static LocalTime dayStart = LocalTime.of(5,00);
    private static LocalTime nightStart = LocalTime.of(18,00);

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


    public TaskTwig() {
        this.readFromFile();
        TaskTwig.instance = this;

        try {
            initDbxClient();
        } catch (DbxException e) {
            throw new RuntimeException(e);
        }
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

    public void saveToFile() {
        File sleepJson = new File("data/sleep.json");
        File workoutJson = new File("data/workout.json");
        File taskJson = new File("data/task.json");
        File listJson = new File("data/list.json");
        File routineJson = new File("data/routine.json");
        File journalJson = new File("data/journal.json");

        try (JsonGenerator generator = mapper.createGenerator(sleepJson, JsonEncoding.UTF8)) {
            generator.writeStartObject();

            generator.writeNumberProperty("version", Sleep.VERSION);
            generator.writePOJOProperty("sleepProgressStart", this.sleepStart.getValue());
            generator.writePOJOProperty("sleepRecords", this.sleepRecords);
            generator.writeEndObject();
        }

        try (JsonGenerator generator = mapper.createGenerator(workoutJson, JsonEncoding.UTF8)) {
            generator.writeStartObject();

            generator.writeNumberProperty("version", Workout.VERSION);
            generator.writePOJOProperty("workoutProgressStart", this.workoutStart.getValue());
            generator.writePOJOProperty("exercises", this.exerciseList);
            generator.writePOJOProperty("workoutRecords", this.workoutRecords);

            generator.writeEndObject();
        }

        try (JsonGenerator generator = mapper.createGenerator(taskJson, JsonEncoding.UTF8)) {
            generator.writeStartObject();

            generator.writeNumberProperty("version", Task.VERSION);

            generator.writeArrayPropertyStart("tasks");
            for (Task task : taskList) {
                generator.writePOJO(task);
            }
            generator.writeEndArray();

            generator.writeEndObject();
        }

        try (JsonGenerator generator = mapper.createGenerator(listJson, JsonEncoding.UTF8)) {
            generator.writeStartObject();

            generator.writeNumberProperty("version", TwigList.VERSION);

            generator.writeArrayPropertyStart("lists");
            for (TwigList list : twigLists) {
                generator.writePOJO(list);
            }
            generator.writeEndArray();

            generator.writeEndObject();
        }

        try (JsonGenerator generator = mapper.createGenerator(routineJson, JsonEncoding.UTF8)) {
            generator.writeStartObject();

            generator.writeNumberProperty("version", Routine.VERSION);

            generator.writeArrayPropertyStart("routines");
            for (Routine routine : routineList) {
                generator.writePOJO(routine);
            }
            generator.writeEndArray();

            generator.writeEndObject();
        }

        try (JsonGenerator generator = mapper.createGenerator(journalJson, JsonEncoding.UTF8)) {
            generator.writeStartObject();

            generator.writeNumberProperty("version", Journal.VERSION);

            generator.writePOJOProperty("journals", this.journalMap);

            generator.writeEndObject();
        }
    }

    private void readFromFile() {
        File sleepJson = new File("data/sleep.json");
        File workoutJson = new File("data/workout.json");
        File taskJson = new File("data/task.json");
        File listJson = new File("data/list.json");
        File routineJson = new File("data/routine.json");
        File journalJson = new File("data/journal.json");

        // Parse sleep records
        SortedMap<LocalDate, Sleep> sleepMap = new TreeMap<>();
        try (JsonParser parser = mapper.createParser(sleepJson)) {
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
        try (JsonParser parser = mapper.createParser(workoutJson)) {
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
        try (JsonParser parser = mapper.createParser(taskJson)) {
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
        try (JsonParser parser = mapper.createParser(listJson)) {
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
        try (JsonParser parser = mapper.createParser(routineJson)) {
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
        try (JsonParser parser = mapper.createParser(journalJson)) {
            parser.nextToken();
            assertEqual(parser.nextName(), "version");
            int version =  parser.nextIntValue(0);

            SortedMap<LocalDate, Journal> journalMap = new TreeMap<>();
            assertEqual(parser.nextName(), "journals");
            parser.nextToken();
            parseJsonMap(journalMap, parser, LocalDate::parse, Journal::new, version);
            this.journalMap = FXCollections.observableMap(journalMap);
//            this.journalMap = FXCollections.observableMap(parser.readValueAs(new TypeReference<SortedMap<LocalDate, Journal>>() {}));
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

    private DbxClientV2 initDbxClient() throws DbxException {
        String ACCESS_TOKEN = "";
        DbxRequestConfig config = DbxRequestConfig.newBuilder("TaskTwig/alpha").build();
        DbxClientV2 client = new DbxClientV2(config, ACCESS_TOKEN);

        FullAccount account = client.users().getCurrentAccount();
        System.out.println(account.getName().getDisplayName());



//        uploadDbxFiles(client);
        downloadDbxFiles(client);

        return client;
    }

    private void downloadDbxFiles(DbxClientV2 client) throws DbxException {
        ListFolderResult result = client.files().listFolder("");
        while (true) {
            for (Metadata metadata : result.getEntries()) {
                System.out.println(metadata.getPathLower());
                try (OutputStream fileOut = new FileOutputStream("data/dbx/"+metadata.getName())) {
                    client.files().downloadBuilder(metadata.getPathLower()).download(fileOut);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            if (!result.getHasMore()) {
                break;
            }

            result = client.files().listFolderContinue(result.getCursor());
        }
    }

    private void uploadDbxFiles(DbxClientV2 client) throws DbxException {
        for (File file : new File("data").listFiles(f -> f.getName().endsWith(".json"))) {

            try (InputStream fileStream = new FileInputStream(file)) {
                client.files().uploadBuilder("/" + file.getName()).withMode(WriteMode.OVERWRITE).uploadAndFinish(fileStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
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
        tracker.saveToFile();
    }

}
