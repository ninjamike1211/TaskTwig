package ninjamica.tasktwig.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class TaskTwigApplication extends Application {
    private TaskTwigController controller;
    @Override
    public void start(Stage stage) throws IOException {

        FXMLLoader fxmlLoader = new FXMLLoader(TaskTwigApplication.class.getResource("fxml/main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
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
