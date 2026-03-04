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

        SpinnerValueFactory<LocalTime> valueFactory = new SpinnerValueFactory<>() {
            @Override
            public void decrement(int steps) {
                switch (editSection) {
                    case HOUR:
                        setValue(getValue().minusHours(steps));
                        break;

                    case MINUTE:
                        setValue(getValue().minusMinutes(steps));
                        break;

                    case AM_PM:
                        setValue(getValue().minusHours(12 * (steps % 2)));
                        break;
                }
            }

            @Override
            public void increment(int steps) {
                switch (editSection) {
                    case HOUR:
                        setValue(getValue().plusHours(steps));
                        break;

                    case MINUTE:
                        setValue(getValue().plusMinutes(steps));
                        break;

                    case AM_PM:
                        setValue(getValue().plusHours(12 * (steps % 2)));
                        break;
                }
            }
        };

        StringConverter<LocalTime> timeConverter = new StringConverter<>() {
            @Override
            public String toString(LocalTime time) {
                return time.format(timeFormat);
            }

            @Override
            public LocalTime fromString(String time) {
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

        if (caretPos <= colonPos) {
            editSection = EditMode.HOUR;
        }
        else if (caretPos <= spacePos) {
            editSection = EditMode.MINUTE;
        }
        else {
            editSection = EditMode.AM_PM;
        }
    }

    enum EditMode {
        HOUR,
        MINUTE,
        AM_PM
    }
}
