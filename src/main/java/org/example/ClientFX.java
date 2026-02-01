package org.example;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ClientFX extends Application {

    @Override
    public void start(Stage stage) {
        Label title = new Label("💬 Chat klient");

        TextField username = new TextField();
        username.setPromptText("Jméno");

        PasswordField password = new PasswordField();
        password.setPromptText("Heslo");

        Button loginBtn = new Button("Přihlásit");

        loginBtn.setOnAction(e -> {
            System.out.println("Login: " + username.getText());
            // tady později napojíš socket
        });

        VBox root = new VBox(10, title, username, password, loginBtn);
        root.setStyle("-fx-padding: 20");

        stage.setScene(new Scene(root, 300, 200));
        stage.setTitle("Chat");
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
