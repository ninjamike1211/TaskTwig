module ninjamica.tasktwig.tasktwig {
    requires javafx.controls;
    requires javafx.fxml;
    requires transitive tools.jackson.databind;
    requires org.jetbrains.annotations;
    requires transitive org.controlsfx.controls;
    requires atlantafx.base;

    requires transitive javafx.base;
    requires transitive javafx.graphics;
    requires java.xml;
    requires transitive dropbox.core.sdk;
    requires java.desktop;
    requires jdk.jdi;


    opens ninjamica.tasktwig to javafx.fxml;
    exports ninjamica.tasktwig;
    exports ninjamica.tasktwig.ui;
    opens ninjamica.tasktwig.ui to javafx.fxml;
}