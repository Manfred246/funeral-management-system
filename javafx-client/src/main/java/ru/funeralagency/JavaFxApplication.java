package ru.funeralagency;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class JavaFxApplication extends Application{

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                JavaFxApplication.class.getResource(
                        "/ru/funeralagency/view/main-view.fxml"
                )
        );

        Scene scene = new Scene(loader.load());
        scene.getStylesheets().add(
                JavaFxApplication.class.getResource("/ru/funeralagency/css/application.css").toExternalForm()
        );

        stage.setTitle("Funeral Management System");
        stage.setScene(scene);
        stage.setMinWidth(960);
        stage.setMinHeight(600);
        stage.setHeight(760);
        stage.show();
    }
}
