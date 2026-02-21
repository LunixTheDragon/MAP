package graphics;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.example.NetworkManager;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class LoginController {

    @FXML private VBox loginCard;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField emailField;
    @FXML private Button actionBtn;
    @FXML private Button switchModeBtn;
    @FXML private Label errorLabel;

    private boolean isLogged = true;

    //animations
    @FXML
    public void initialize() {

        //starting point
        loginCard.setOpacity(0);
        loginCard.setTranslateY(30);

        //animace pruhlednosti
        FadeTransition fade = new FadeTransition(Duration.seconds(0.8), loginCard);
        fade.setToValue(1);
        fade.setFromValue(0);

        // Animace pohybu nahoru (Slide Up)
        TranslateTransition translate = new TranslateTransition(Duration.seconds(0.8), loginCard);
        translate.setToY(0);
        translate.setFromY(30);

        // Spustíme obě animace najednou
        ParallelTransition transition = new ParallelTransition(fade, translate);
        transition.setDelay(Duration.seconds(0.2)); // Malé zpoždění po startu
        transition.play();
    }

    // Metoda pro přepnutí UI (zvětšení/zmenšení okna)
    @FXML
    protected void switchMode() {
        isLogged = !isLogged; // Přepnutí stavu
        errorLabel.setText(""); //restarting error label

        if (isLogged) {
            emailField.setVisible(false);
            emailField.setManaged(false);
            actionBtn.setText("Přihlásit se");
            switchModeBtn.setText("Nemáte účet? Zaregistrujte se");
        } else {
            emailField.setVisible(true);
            emailField.setManaged(true);
            actionBtn.setText("Vytvořit účet");
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
            shakeCard();
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
                        errorLabel.setStyle("-fx-text-fill: #34c759;");
                        errorLabel.setText("Registrace úspěšná! Můžete se přihlásit.");
                        emailField.clear();
                        switchMode();
                    }
                } else {
                    System.out.println(isLogged ? "Chyba přihlášení." : "Chyba registrace.");
                    errorLabel.setStyle("-fx-text-fill: #ff3b30;");
                    errorLabel.setText(isLogged ?
                            "Nesprávné jméno nebo heslo.\n(Nebo je server nedostupný)" :
                            "Registrace selhala. Uživatel už možná existuje.");
                    shakeCard();
                }
                setFieldsDisabled(false);

            });
        }).start();
    }

    private void goToChatScreen() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/graphics/chat.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) usernameField.getScene().getWindow();
            // Animace odchodu (Fade Out) před přepnutím
            FadeTransition fadeOut = new FadeTransition(Duration.seconds(0.4), loginCard);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(event -> {
                stage.setScene(new Scene(root, 800, 600));
                stage.setTitle("Chat - " + usernameField.getText());
                stage.centerOnScreen();
            });
            fadeOut.play();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void setFieldsDisabled(boolean disabled) {
        usernameField.setDisable(disabled);
        passwordField.setDisable(disabled);
        emailField.setDisable(disabled);
        actionBtn.setDisable(disabled);
    }
    //shaking animation
    private void shakeCard() {
        TranslateTransition shake = new TranslateTransition(Duration.millis(50), loginCard);
        shake.setByX(10);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.play();
    }
}