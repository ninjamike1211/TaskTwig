module ninjamica.tasktwig.tasktwig {
    requires javafx.controls;
    requires javafx.fxml;
    requires tools.jackson.databind;
    requires org.jetbrains.annotations;
    requires org.controlsfx.controls;
    requires atlantafx.base;


    opens ninjamica.tasktwig to javafx.fxml;
    exports ninjamica.tasktwig;
    exports ninjamica.tasktwig.ui;
    opens ninjamica.tasktwig.ui to javafx.fxml;
    exports ninjamica.tasktwig.task;
    opens ninjamica.tasktwig.task to javafx.fxml;
}