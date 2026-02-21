package graphics;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
    @FXML private Label errorLabel;

    private boolean isLogged = true;

    // Metoda pro přepnutí UI (zvětšení/zmenšení okna)
    @FXML
    protected void switchMode() {
        isLogged = !isLogged; // Přepnutí stavu
        errorLabel.setText(""); //restarting error label

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

        if(username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Vyplňte všechna pole");
            return;
        }

        errorLabel.setText("Connecting..");
        errorLabel.setStyle("-fx-text-fill: #3390ec;");//blue color at loading state


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
                        errorLabel.setStyle("-fx-text-fill: #10b981;");
                        errorLabel.setText("Registrace úspěšná! Můžete se přihlásit.");
                        emailField.clear();
                        switchMode();
                    }
                } else {
                    System.out.println(isLogged ? "Chyba přihlášení." : "Chyba registrace.");
                    errorLabel.setStyle("-fx-text-fill: #ef4444;");
                    errorLabel.setText(isLogged ?
                            "Nesprávné jméno nebo heslo.\n(Nebo je server nedostupný)" :
                            "Registrace selhala. Uživatel už možná existuje.");
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
            stage.setScene(new Scene(root, 700, 500));
            stage.setTitle("Telegram Style Chat - " + usernameField.getText());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}