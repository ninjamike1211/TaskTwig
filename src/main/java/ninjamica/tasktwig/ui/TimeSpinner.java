package ninjamica.tasktwig.ui;

import javafx.event.Event;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.util.StringConverter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * A modified Spinner which allows the user to select a specific time.
 * Uses 12 hour AM/PM time, returns as LocalTime.
 */
public class TimeSpinner {

    private EditMode editSection = EditMode.HOUR;
    private final Spinner<LocalTime> timeSpinner;
    public static final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("h:mm a");

    public TimeSpinner(Spinner<LocalTime> spinner) {
        this(spinner, LocalTime.now());
    }

    public TimeSpinner(Spinner<LocalTime> spinner, LocalTime time) {
        this.timeSpinner = spinner;
        spinner.setEditable(true);

        SpinnerValueFactory<LocalTime> valueFactory = new SpinnerValueFactory<LocalTime>() {
            @Override
            public void decrement(int steps) {
                switch (editSection) {
                    case HOUR:
                        setValue(getValue().minusHours(steps));
                        System.out.println("Decrement hour to: " + getValue().format(timeFormat));
                        break;

                    case MINUTE:
                        setValue(getValue().minusMinutes(steps));
                        System.out.println("Decrement minute to: " + getValue().format(timeFormat));
                        break;

                    case AM_PM:
                        setValue(getValue().minusHours(12 * (steps % 2)));
                        System.out.println("Decrement AM/PM to: " + getValue().format(timeFormat));
                        break;
                }
            }

            @Override
            public void increment(int steps) {
                switch (editSection) {
                    case HOUR:
                        setValue(getValue().plusHours(steps));
                        System.out.println("Increment hour to: " + getValue().format(timeFormat));
                        break;

                    case MINUTE:
                        setValue(getValue().plusMinutes(steps));
                        System.out.println("Increment minute to: " + getValue().format(timeFormat));
                        break;

                    case AM_PM:
                        setValue(getValue().plusHours(12 * (steps % 2)));
                        System.out.println("Increment AM/PM to: " + getValue().format(timeFormat));
                        break;
                }
            }
        };

        StringConverter<LocalTime> timeConverter = new StringConverter<LocalTime>() {
            @Override
            public String toString(LocalTime time) {
                System.out.println("StringConverter.toString(): " + time.format(timeFormat));
                return time.format(timeFormat);
            }

            @Override
            public LocalTime fromString(String time) {
                System.out.println("StringConverter.fromString: " + time + " -> " + LocalTime.parse(time, timeFormat));
                return LocalTime.parse(time, timeFormat);
            }
        };

        valueFactory.setConverter(timeConverter);
        valueFactory.setValue(time);
        spinner.setValueFactory(valueFactory);

        spinner.getEditor().addEventHandler(MouseEvent.MOUSE_CLICKED, this::setEditMode);
        spinner.getEditor().addEventHandler(KeyEvent.KEY_PRESSED, keyEvent -> {
            if (keyEvent.getCode() == KeyCode.LEFT || keyEvent.getCode() == KeyCode.RIGHT) {
                this.setEditMode(keyEvent);
            }
        });
        spinner.getEditor().setOnScroll(event -> {
            if (event.getDeltaY() < 0) {
                spinner.decrement();
            }
            else if (event.getDeltaY() > 0) {
                spinner.increment();
            }
        });

        spinner.valueProperty().addListener((observable, oldValue, newValue) -> {
            int colonPos = timeSpinner.getEditor().getText().indexOf(":");
            int spacePos = timeSpinner.getEditor().getText().indexOf(" ");

            switch (editSection) {
                case HOUR:
                    spinner.getEditor().positionCaret(colonPos);
                    break;

                case MINUTE:
                    spinner.getEditor().positionCaret(spacePos);
                    break;

                case AM_PM:
                    spinner.getEditor().positionCaret(spacePos+3);
                    break;
            }
        });
    }

    private void setEditMode(Event event) {
        int caretPos = timeSpinner.getEditor().getCaretPosition();
        if (event instanceof KeyEvent) {
            if (((KeyEvent) event).getCode() == KeyCode.LEFT) {
                caretPos = (caretPos > 0) ? (caretPos - 1) : 0;
            }
            else if (((KeyEvent) event).getCode() == KeyCode.RIGHT) {
                caretPos = (caretPos < timeSpinner.getEditor().getText().length()) ? (caretPos + 1) : caretPos;
            }
        }

        int colonPos = timeSpinner.getEditor().getText().indexOf(":");
        int spacePos = timeSpinner.getEditor().getText().indexOf(" ");

        System.out.println(event.getEventType());
        System.out.println("caretPos: "+caretPos+" colonPos: "+colonPos+" spacePos: "+ spacePos);

        if (caretPos <= colonPos) {
            System.out.println("Editing HOUR");
            editSection = EditMode.HOUR;
        }
        else if (caretPos <= spacePos) {
            System.out.println("Editing MINUTE");
            editSection = EditMode.MINUTE;
        }
        else {
            System.out.println("Editing AM_PM");
            editSection = EditMode.AM_PM;
        }
    }

    enum EditMode {
        HOUR,
        MINUTE,
        AM_PM
    }
}
