
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.opencv.core.Core;

public class Main extends Application
{
    public static void main(String[] args) {
        nu.pattern.OpenCV.loadShared();
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("scene.fxml"));

        BorderPane root = loader.load();
        root.setStyle("-fx-background-color: whitesmoke;");

        Scene scene = new Scene(root, 1600, 650);
        primaryStage.setTitle("Распознование образов");
        primaryStage.setScene(scene);
        primaryStage.show();

        Controller controller = loader.getController();

        primaryStage.setOnCloseRequest((we -> controller.setClosed()));
    }
}
