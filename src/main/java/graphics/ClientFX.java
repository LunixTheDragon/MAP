package graphics;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClientFX extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        // Načteme FXML soubor (cestu uprav podle své struktury složek)
        FXMLLoader fxmlLoader = new FXMLLoader(ClientFX.class.getResource("/graphics/login.fxml"));

        Scene scene = new Scene(fxmlLoader.load(), 600, 600);

        stage.setTitle("Cricket");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}