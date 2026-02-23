package graphics;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.NetworkManager;
import utils.SecurityUtils;

import javax.crypto.SecretKey;
import java.io.File;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.util.List;
public class ChatController {

    @FXML private Label currentUserLabel;
    @FXML private TextArea chatArea;
    @FXML private TextField targetUserField;
    @FXML private TextField messageField;
    @FXML private Button sendBtn;

    private SecretKey currentChatKey;
    private String currentReceiver;
    private Timeline autoRefreshTimeline; // javaFx class for refreshing
    private boolean isDarkMode = false;

    @FXML
    public void initialize() {
        String currentUser = org.example.NetworkManager.getInstance().getLoggedUser();
        currentUserLabel.setText(currentUser != null ? currentUser : "Neznámý uživatel");        // Tato metoda se zavolá automaticky po načtení FXML
        // Zde budeme později inicializovat vlákno pro čtení příchozích zpráv ze sítě
        chatArea.appendText("Vítej v zabezpečeném chatu, " + currentUserLabel.getText() + "!\n");
        chatArea.appendText("Zadej příjemce dole a klikni na 'Otevřít chat'.\n");

        //writing is blocked until secured connection happen
        messageField.setDisable(true);
        sendBtn.setDisable(true);
        setupAutoRefresh();
    }

    private void setupAutoRefresh(){
        autoRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(3), event -> {
            if (currentChatKey != null && currentReceiver != null) {
                reloadHistoryInBack();
            }
        }));
        autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        autoRefreshTimeline.play();
    }

    @FXML
    protected void handleManualRefresh(){
        if (currentChatKey != null && currentReceiver != null) {
            reloadHistoryInBack();
        }
    }

    private void reloadHistoryInBack(){
        String user = NetworkManager.getInstance().getLoggedUser();
        new Thread(() -> {
            try {
                List<String> history  = NetworkManager.getInstance().fetchHistory(user, currentReceiver, currentChatKey);
                Platform.runLater(() -> {
                    StringBuilder sb = new StringBuilder("--- 🔒 Zabezpečený chat s " + currentReceiver + " ---\n");
                    for (String s : history) {
                        sb.append(s).append("\n");
                    }
                    if (!chatArea.getText().equals(sb.toString())){
                        chatArea.setText(sb.toString());
                        chatArea.positionCaret(chatArea.getText().length()); //
                    }

                });
            } catch (Exception e) {
            }
        }).start();
    }

    @FXML
    protected void handleLoadChat(){
        String receiver = targetUserField.getText().trim();
        if (receiver.isEmpty()){
            return;
        }

        String user = NetworkManager.getInstance().getLoggedUser();
        if (user.equals(receiver)){
            chatArea.appendText(">> Nemůžeš psát sám sobě.\n");
            return;
        }

        chatArea.appendText(">> Vytvářím zabezpečené spojení s " + receiver + "...\n");
        targetUserField.setDisable(true);

        new Thread(() -> {
            try {
                //user private key
                File keyFile = new File(user + "_private.key");
                String privKeyStr = Files.readString(keyFile.toPath());
                PrivateKey userPrivKey = SecurityUtils.RSAUtils.getPrivateKeyFromString(privKeyStr);

                //  AESKey from NetworkManager
                currentChatKey = NetworkManager.getInstance().AESScryptingForChat(user, receiver, userPrivKey);
                currentReceiver = receiver;

                //return to graphics
                Platform.runLater(() -> {
                    messageField.setDisable(false);
                    sendBtn.setDisable(false);
                    targetUserField.setDisable(false);
                    messageField.requestFocus(); // Kurzor automaticky skočí do pole
                });

                reloadHistoryInBack();
            } catch (Exception e) {
                Platform.runLater(() -> {
                    chatArea.appendText(">> Chyba: " + e.getMessage() + "\n");
                    targetUserField.setDisable(false);
                });
            }
        }).start();
    }
    @FXML
    protected void handleSendMessage() {
        String message = messageField.getText().trim();
        if (message.isEmpty() || currentChatKey == null || currentReceiver == null) return;

        String user = NetworkManager.getInstance().getLoggedUser();
        messageField.setDisable(true);
        sendBtn.setDisable(true);

        // Vlákno pro odeslání zprávy
        new Thread(() -> {
            try {
                boolean ok = NetworkManager.getInstance().sendChatMessage(user, currentReceiver, message, currentChatKey);
                Platform.runLater(() -> {
                    if (ok) {
                        chatArea.appendText("Ty: " + message + "\n");
                        messageField.clear();
                        reloadHistoryInBack(); // Okamžitě obnovit po odeslání
                    } else {
                        chatArea.appendText(">> Chyba při odesílání serveru.\n");
                    }
                    messageField.setDisable(false);
                    sendBtn.setDisable(false);
                    messageField.requestFocus();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    chatArea.appendText(">> Kritická chyba šifrování: " + e.getMessage() + "\n");
                    messageField.setDisable(false);
                    sendBtn.setDisable(false);
                });
            }
        }).start();
    }
    //methods for menu
    @FXML
    protected void handleNewChat() {
        currentChatKey = null;
        currentReceiver = null;
        targetUserField.clear();
        targetUserField.setDisable(false);
        messageField.setDisable(true);
        sendBtn.setDisable(true);
        chatArea.clear();
        chatArea.setText(">> Režim nového chatu. Zadej příjemce dole a klikni na 'Otevřít chat'.\n");
    }

    @FXML
    protected void toggleDarkMode() {
        isDarkMode = !isDarkMode;
        if (isDarkMode) {
            chatArea.getScene().getRoot().getStyleClass().add("dark-mode");
        } else {
            chatArea.getScene().getRoot().getStyleClass().remove("dark-mode");
        }
    }

    @FXML
    protected void handleSignOut() {
        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop(); // Vypneme smyčku stahování
        }
        NetworkManager.getInstance().logout(); // Odstraníme session

        try {
            // Návrat do Login obrazovky
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/graphics/login.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) chatArea.getScene().getWindow();
            stage.setScene(new Scene(root, 600, 600));
            stage.setTitle("Cricket - Přihlášení");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

