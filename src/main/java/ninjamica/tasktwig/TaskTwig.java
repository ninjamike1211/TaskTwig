package ninjamica.tasktwig;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import ninjamica.tasktwig.task.Task;
import tools.jackson.core.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class TaskTwig implements Serializable {

    private static final class JsonAssertException extends RuntimeException {}
    public static abstract class HasVersion {
        public static final int VERSION = 0;
    }

    private static LocalTime dayStart = LocalTime.MIN;

    private ObservableMap<LocalDate, Sleep> sleepRecords;
    private ObservableList<Workout> workoutRecords;
    private ObservableList<Exercise> exerciseList;
    private ObservableList<Task> taskList;
    private ObservableList<TwigList> twigLists;
    private LocalDateTime sleepStart;
    private LocalDateTime workoutStart;
    private final ObjectMapper mapper = new ObjectMapper();


    public TaskTwig() {
//        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
//                .allowIfSubType(Task.class)
//                .build();


//        mapper = JsonMapper.builder()
//                .activateDefaultTyping(typeValidator, DefaultTyping.JAVA_LANG_OBJECT, JsonTypeInfo.As.PROPERTY)
//                .activateDefaultTypingAsProperty(typeValidator, DefaultTyping.JAVA_LANG_OBJECT, "@name")
//                .configure(MapperFeature.USE_STATIC_TYPING, false)
//                .build();
//        mapper.serializationConfig().

        this.readFromFile();

    }

    public static void setDayStart(LocalTime dayStart) {
        TaskTwig.dayStart = dayStart;
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

    public void startSleep() {
        this.startSleep(LocalDateTime.now());
    }

    public void startSleep(LocalDateTime sleepStart) {
        this.sleepStart = sleepStart;
    }

    public void finishSleep() {
        this.finishSleep(LocalDateTime.now());
    }

    public void finishSleep(LocalDateTime finishTime) {
        sleepRecords.put(finishTime.toLocalDate().minusDays(1), new Sleep(sleepStart, finishTime));
        sleepStart = null;
    }

    public boolean isSleeping() {
        return this.sleepStart != null;
    }

    public LocalDateTime sleepStart() {
        return this.sleepStart;
    }

    public ObservableMap<LocalDate, Sleep> sleepRecords() {
        return sleepRecords;
    }

    public void startWorkout() {
        this.startWorkout(LocalDateTime.now());

    }
    public void startWorkout(LocalDateTime startTime) {
        workoutStart = startTime;
    }

    public void finishWorkout(Map<Exercise, Integer> exercises, LocalDateTime finishTime) {
        workoutRecords.add(new Workout(workoutStart, finishTime, exercises));
        workoutStart = null;
    }

    public boolean isWorkingOut() {
        return workoutStart != null;
    }

    public LocalDateTime workoutStart() {
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

//    public List<Workout> getWorkoutRecords(LocalDate date) {
//        return workoutRecords.get(date);
//    }

    public void addTask(Task task) {
        taskList.add(task);
    }

    public void finishTask(int index) {
        taskList.get(index).setCompletion(true);
    }

    public List<Task> getTodaysTasks() {
        ArrayList<Task> tasks = new ArrayList<>();
        long today = LocalDate.now().toEpochDay();

        for (Task task : taskList) {
            if (task.nextDueDate().toEpochDay() >= today) {
                tasks.add(task);
            }
        }

        return tasks;
    }

    public ObservableList<Task> taskList() {
        return taskList;
    }

    public ObservableList<TwigList> twigLists() {
        return twigLists;
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
                System.out.println("\t" + task.name() + " due " + task.nextDueDate() + ((task.dueTime() == null) ? "" : (" at " + task.dueTime())));
            }
        }
    }

    public void saveToFile() {
        File sleepJson = new File("data/sleep.json");
        File workoutJson = new File("data/workout.json");
        File taskJson = new File("data/task.json");
        File listJson = new File("data/list.json");

        try (JsonGenerator generator = mapper.createGenerator(sleepJson, JsonEncoding.UTF8)) {
            generator.writeStartObject();

            generator.writeNumberProperty("version", Sleep.VERSION);
            generator.writePOJOProperty("sleepProgressStart", this.sleepStart);
            generator.writePOJOProperty("sleepRecords", this.sleepRecords);
            generator.writeEndObject();
        }

        try (JsonGenerator generator = mapper.createGenerator(workoutJson, JsonEncoding.UTF8)) {
            generator.writeStartObject();

            generator.writeNumberProperty("version", Workout.VERSION);
            generator.writePOJOProperty("workoutProgressStart", this.workoutStart);
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

            generator.writeArrayPropertyStart("lists");
            for (TwigList list : twigLists) {
                generator.writePOJO(list);
            }
            generator.writeEndArray();

            generator.writeEndObject();
        }
    }

    private void readFromFile() {
        File sleepJson = new File("data/sleep.json");
        File workoutJson = new File("data/workout.json");
        File taskJson = new File("data/task.json");
        File listJson = new File("data/list.json");

        try {
            // Parse sleep records
            try (JsonParser parser = mapper.createParser(sleepJson)) {
                parser.nextToken();
                assertEqual(parser.nextName(), "version");
                assertVersion(parser.nextIntValue(0), Sleep.class);

                assertEqual(parser.nextName(), "sleepProgressStart");
                if (parser.nextToken() == JsonToken.VALUE_STRING)
                    this.sleepStart = LocalDateTime.parse(parser.getString());
                else
                    assertEqual(parser.currentToken(), JsonToken.VALUE_NULL);

                assertEqual(parser.nextName(), "sleepRecords");
                parser.nextToken();
                this.sleepRecords = FXCollections.observableMap(parser.readValueAs(new TypeReference<>() {}));
            } catch (JsonAssertException e) {
                this.sleepStart = null;
                this.sleepRecords = FXCollections.observableMap(new TreeMap<>());
            }

            // Parse workout records
            try (JsonParser parser = mapper.createParser(workoutJson)) {
                parser.nextToken();
                assertEqual(parser.nextName(), "version");
                assertVersion(parser.nextIntValue(0), Workout.class);

                assertEqual(parser.nextName(), "workoutProgressStart");
                if (parser.nextToken() == JsonToken.VALUE_STRING)
                    this.workoutStart = LocalDateTime.parse(parser.getString());
                else
                    assertEqual(parser.currentToken(), JsonToken.VALUE_NULL);

                assertEqual(parser.nextName(), "exercises");
                parser.nextToken();
                this.exerciseList = FXCollections.observableList(parser.readValueAs(new TypeReference<>() {}));

                assertEqual(parser.nextName(), "workoutRecords");
                parser.nextToken();
                this.workoutRecords = FXCollections.observableList(parser.readValueAs(new TypeReference<>() {}));
            } catch (JsonAssertException e) {
                this.workoutStart = null;
                this.exerciseList = FXCollections.observableArrayList();
                this.workoutRecords = FXCollections.observableArrayList();
            }

            // Parse tasks
            try (JsonParser parser = mapper.createParser(taskJson)) {
                parser.nextToken();
                assertEqual(parser.nextName(), "version");
                assertVersion(parser.nextIntValue(0), Task.class);

                assertEqual(parser.nextName(), "tasks");
                parser.nextToken();
                this.taskList = FXCollections.observableList(parser.readValueAs(new TypeReference<>() {}));
            } catch (JsonAssertException e) {
                this.taskList = FXCollections.observableArrayList();
            }

            this.twigLists = FXCollections.observableArrayList();
            try (JsonParser parser = mapper.createParser(listJson)) {
                parser.nextToken();
                assertEqual(parser.nextName(), "lists");
                parser.nextToken();
                this.twigLists = FXCollections.observableList(parser.readValueAs(new TypeReference<>() {}));
            } catch (JsonAssertException e) {
                this.twigLists = FXCollections.observableArrayList();
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void assertEqual(Object actual, Object expected) throws JsonAssertException {
        if (!expected.equals(actual))
            throw new JsonAssertException();
    }

    private void assertVersion(int version, Class<? extends HasVersion> tClass) throws JsonAssertException, NoSuchFieldException, IllegalAccessException {

        int currentVersion = tClass.getField("VERSION").getInt(null);
        if (version < 1 || version > currentVersion)
            throw new JsonAssertException();

        if (version < currentVersion) {
            // TODO: implement fallback for older version
            throw new JsonAssertException();
        }
    }


    public static void main() {
//        TaskTwig tracker = TaskTwig.initialize();
        TaskTwig tracker = new TaskTwig();
        // tracker.addTask(new Task("testTask", LocalDate.now(), null));
        // tracker.addTask(new Task("EAT SOMETHING", LocalDate.now(), LocalTime.of(19, 0)));
//        tracker.addTask(new DailyTask("GO TO SLEEEEP", LocalTime.of(23, 0)));

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
            command = "";

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
                            Map<Exercise, Integer> exercises = new TreeMap<>();
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
                    if (tracker.sleepStart != null) {
                        System.out.println("Sleeping, started " + tracker.sleepStart.format(DateTimeFormatter.ISO_DATE_TIME));
                    }
                    if (tracker.workoutStart != null) {
                        System.out.println("Working out, started " + tracker.workoutStart.format(DateTimeFormatter.ISO_DATE_TIME));
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
