package graphics;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class ChatController {

    @FXML private Label currentUserLabel;
    @FXML private TextArea chatArea;
    @FXML private TextField targetUserField;
    @FXML private TextField messageField;

    @FXML
    public void initialize() {
        String currentUser = org.example.NetworkManager.getInstance().getLoggedUser();
        currentUserLabel.setText(currentUser != null ? currentUser : "Neznámý uživatel");        // Tato metoda se zavolá automaticky po načtení FXML
        // Zde budeme později inicializovat vlákno pro čtení příchozích zpráv ze sítě
        chatArea.appendText("Vítej v zabezpečeném chatu, " + currentUserLabel.getText() + "!\n");
    }

    @FXML
    protected void handleSendMessage() {
        String receiver = targetUserField.getText().trim();
        String message = messageField.getText().trim();

        if (receiver.isEmpty() || message.isEmpty()) {
            return; // Neposíláme prázdné zprávy
        }

        // Tady později napojíme šifrování a odeslání přes NetworkManager
        System.out.println("Odesílám zprávu pro " + receiver + ": " + message);

        // Zobrazení v okně
        chatArea.appendText("Ty (" + receiver + "): " + message + "\n");

        // Vyčištění pole pro novou zprávu
        messageField.clear();
    }
}