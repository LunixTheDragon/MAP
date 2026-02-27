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

        try {
            stage.getIcons().add(new javafx.scene.image.Image(ClientFX.class.getResourceAsStream("/logo2.png")));
        } catch (Exception e) {
            System.out.println("Obrázek logo.png nebyl nalezen v resources.");
        }
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        System.setProperty("prism.order", "sw");
        launch();
    }
}