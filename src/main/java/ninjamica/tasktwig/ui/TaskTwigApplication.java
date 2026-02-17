package ninjamica.tasktwig.ui;

import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.PrimerDark;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class TaskTwigApplication extends Application {
    private TaskTwigController controller;
    @Override
    public void start(Stage stage) throws IOException {
//        Application.setUserAgentStylesheet(new NordDark().getUserAgentStylesheet());

        FXMLLoader fxmlLoader = new FXMLLoader(TaskTwigApplication.class.getResource("fxml/main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
//        scene.getRoot().setStyle("-fx-base:black");
//        scene.getStylesheets().add(TaskTwigApplication.class.getResource("fxml/dark-theme.css").toExternalForm());
        stage.setTitle("TaskTwig");
        stage.setScene(scene);
        stage.show();

        controller = fxmlLoader.getController();
        controller.setStage(stage);
    }

    @Override
    public void stop() throws Exception {
        controller.closeTwig();
        super.stop();
    }
}
