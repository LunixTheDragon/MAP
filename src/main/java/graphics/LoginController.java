package graphics;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
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
    @FXML private TextField emailField;
    @FXML private Button actionBtn;
    @FXML private Button switchModeBtn;

    private boolean isLogged = true;

    // Metoda pro přepnutí UI (zvětšení/zmenšení okna)
    @FXML
    protected void switchMode() {
        isLogged = !isLogged; // Přepnutí stavu

        if (isLogged) {
            emailField.setVisible(false);
            emailField.setManaged(false);
            actionBtn.setText("PŘIHLÁSIT SE");
            switchModeBtn.setText("Nemáte účet? Zaregistrujte se");
        } else {
            emailField.setVisible(true);
            emailField.setManaged(true);
            actionBtn.setText("ZAREGISTROVAT SE");
            switchModeBtn.setText("Už máte účet? Přihlaste se");
        }

        //adjusting to what is inside
        Stage stage = (Stage) actionBtn.getScene().getWindow();
        stage.sizeToScene();
    }


    @FXML
    protected void handleSubmit() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        // Zablokujeme pole během načítání
        usernameField.setDisable(true);
        passwordField.setDisable(true);
        emailField.setDisable(true);
        actionBtn.setDisable(true);

        new Thread(() -> {
            boolean success;
            if (isLogged) {
                // Login
                success = NetworkManager.getInstance().login(username, password);
            } else {
                // signIn
                String email = emailField.getText();
                success = NetworkManager.getInstance().register(username, password, email);
            }

            // waiting for thread to finish task
            Platform.runLater(() -> {
                if (success) {
                    if (isLogged) {
                        System.out.println("Přihlášení úspěšné!");
                        goToChatScreen();
                    } else {
                        System.out.println("Registrace úspěšná!");
                        emailField.clear();
                        switchMode();
                    }
                } else {
                    System.out.println(isLogged ? "Chyba přihlášení." : "Chyba registrace.");
                }


                usernameField.setDisable(false);
                passwordField.setDisable(false);
                emailField.setDisable(false);
                actionBtn.setDisable(false);
            });
        }).start();
    }

    private void goToChatScreen() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/graphics/chat.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(new Scene(root, 600, 400));
            stage.setTitle("Chat Room - " + usernameField.getText());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}