package graphics;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.example.NetworkManager;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;

    @FXML
    protected void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        System.out.println("Zkouším přihlásit uživatele: " + username);

        // Zablokujeme pole během načítání
        usernameField.setDisable(true);
        passwordField.setDisable(true);

        // Vlákno na pozadí, které neruší grafiku
        new Thread(() -> {
            boolean success = NetworkManager.getInstance().login(username, password);

            // Zpět do grafického (hlavního) vlákna
            Platform.runLater(() -> {
                if (success) {
                    System.out.println("Přihlášení úspěšné!");
                    goToChatScreen(); // Přepnutí grafiky!
                } else {
                    System.out.println("Chybné jméno nebo heslo.");
                    // Tady bys ideálně zobrazil nějaký červený text "Chyba přihlášení"
                    usernameField.setDisable(false);
                    passwordField.setDisable(false);
                }
            });
        }).start();
    }

    // Metoda pro přidání další grafiky (přepnutí scény)
    private void goToChatScreen() {
        try {
            // Načteme novou grafiku chatu
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/graphics/chat.fxml"));
            Parent root = loader.load();

            // Získáme aktuální okno podle jednoho z prvků (např. textového pole)
            Stage stage = (Stage) usernameField.getScene().getWindow();

            // Nastavíme oknu novou scénu
            stage.setScene(new Scene(root, 600, 400));
            stage.setTitle("Chat Room - " + usernameField.getText());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}