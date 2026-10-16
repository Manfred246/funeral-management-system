package ru.funeralagency;

import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class JavaFxApplication extends Application{

    @Override
    public void start(Stage stage) throws Exception {
        Text text = new Text("Hello World");
        text.setLayoutX(80);
        text.setLayoutY(80);

        Group group = new Group(text);
        Scene scene = new Scene(group);

        stage.setScene(scene);
        stage.setTitle("JavaFxApplication");
        stage.setWidth(300);
        stage.setHeight(250);
        stage.show();
    }
}