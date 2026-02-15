package ninjamica.tasktwig.ui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

public class TimeDateDialog extends Dialog<LocalDateTime> {
    @FXML
    private Spinner<LocalTime> timeSpinner;
    @FXML
    private DatePicker dateSelector;
    @FXML
    private Label timeLabel;
    @FXML
    private Label dateLabel;

    public TimeDateDialog(Window owner, String label) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("fxml/timedate-dialog.fxml"));
            loader.setController(this);
            DialogPane dialogPane = loader.load();

            initOwner(owner);
            initModality(Modality.APPLICATION_MODAL);

            setTitle("Enter "+label+" Time");
            timeLabel.setText("Select "+label+" Time");
            dateLabel.setText("Select "+label+" Date");

            setDialogPane(dialogPane);
            setResultConverter(buttonType -> {
                if(Objects.equals(ButtonBar.ButtonData.OK_DONE, buttonType.getButtonData())) {
                    LocalTime time = timeSpinner.getValue();
                    LocalDateTime dateTime = time.atDate(dateSelector.getValue());
                    System.out.println(dateTime);
                    return dateTime;
                }

                return null;
            });
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @FXML
    private void initialize() {
        new TimeSpinner(timeSpinner);
        dateSelector.setValue(LocalDate.now());
    }
}
