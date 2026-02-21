package graphics;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
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

                // 3. download history
                List<String> history = NetworkManager.getInstance().fetchHistory(user, receiver, currentChatKey);


                //return to graphics
                Platform.runLater(() -> {
                    chatArea.clear();
                    chatArea.appendText("Zabezpečený chat s " + receiver + " ---\n");
                    for (String msg : history) {
                        chatArea.appendText(msg + "\n");
                    }
                    messageField.setDisable(false);
                    sendBtn.setDisable(false);
                    targetUserField.setDisable(false);
                    messageField.requestFocus(); // Kurzor automaticky skočí do pole
                });
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
    }}