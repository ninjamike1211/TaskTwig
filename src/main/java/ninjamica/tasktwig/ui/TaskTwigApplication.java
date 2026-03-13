package ninjamica.tasktwig.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;

public class TaskTwigApplication extends Application {
    private TaskTwigController controller;
    @Override
    public void start(Stage stage) throws IOException {

        FXMLLoader fxmlLoader = new FXMLLoader(TaskTwigApplication.class.getResource("fxml/main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());

        stage.getIcons().addAll(
          new Image(TaskTwigApplication.class.getResourceAsStream("images/icon-64.png")),
          new Image(TaskTwigApplication.class.getResourceAsStream("images/icon-32.png")),
          new Image(TaskTwigApplication.class.getResourceAsStream("images/icon-16.png"))
        );
        stage.setTitle("TaskTwig");
        stage.setScene(scene);
        stage.show();

        controller = fxmlLoader.getController();
        controller.setStage(stage);
        controller.setApplication(this);
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        controller.closeTwig();
    }
}
