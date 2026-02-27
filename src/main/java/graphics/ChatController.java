package graphics;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.NetworkManager;
import utils.SecurityUtils;
import javafx.scene.control.ScrollPane;
import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.List;


public class ChatController {

    @FXML private Label currentUserLabel;
    @FXML private TextField targetUserField;
    @FXML private TextField messageField;
    @FXML private Button sendBtn;
    @FXML private Button themeBtn;
    @FXML private Circle profileImageCircle;
    @FXML private javafx.scene.layout.BorderPane rootPane;

    private SecretKey currentChatKey;
    private String currentReceiver;
    private Timeline autoRefreshTimeline;
    @FXML private ScrollPane scrollPane;
    @FXML private VBox chatContainer;
    @FXML private Circle receiverImageCircle;
    @FXML private Label receiverUserLabel;
    @FXML private javafx.scene.layout.HBox receiverProfileBox;

    // Globální nastavení profilu stahované ze serveru
    private boolean isDarkMode = false;
    private String currentBase64Avatar = "NULL";

    @FXML
    public void initialize() {
        String currentUser = NetworkManager.getInstance().getLoggedUser();
        if (currentUser != null) {
            currentUserLabel.setText(currentUser);

            // 1. Spustíme asynchronní vlákno pro stažení nastavení (Dark mode + Avatar) ze serveru
            new Thread(() -> {
                String[] prefs = NetworkManager.getInstance().getPreferences(currentUser);
                boolean dm = Boolean.parseBoolean(prefs[0]);
                String b64 = prefs[1];

                Platform.runLater(() -> {
                    isDarkMode = dm;
                    currentBase64Avatar = b64;

                    // Aplikace Dark Mode
                    if (isDarkMode) {
                        rootPane.getStyleClass().add("dark-mode"); // ZMĚNĚNO ZDE
                        themeBtn.setText("☀️");
                    }

                    // Aplikace Avataru
                    Image img = decodeBase64ToImage(b64);
                    if (img != null) {
                        profileImageCircle.setFill(new ImagePattern(img));
                    }
                });
            }).start();
        } else {
            currentUserLabel.setText("Neznámý uživatel");
        }

        addSystemMessage("Vítej v zabezpečeném chatu, " + currentUserLabel.getText() + "!\n");
        addSystemMessage("Zadej příjemce dole a klikni na 'Otevřít chat'.\n");

        chatContainer.heightProperty().addListener((observable, oldValue, newValue) -> scrollPane.setVvalue(1.0));

        messageField.setDisable(true);
        sendBtn.setDisable(true);
        setupAutoRefresh();
    }

    // --- POMOCNÉ METODY PRO PŘEVOD OBRÁZKU NA TEXT (BASE64) ---
    private String encodeFileToBase64(File file) {
        try {
            byte[] fileContent = Files.readAllBytes(file.toPath());
            return Base64.getEncoder().encodeToString(fileContent);
        } catch (Exception e) {
            return "NULL";
        }
    }

    private Image decodeBase64ToImage(String b64) {
        if (b64 == null || b64.equals("NULL")) return null;
        try {
            byte[] imgBytes = Base64.getDecoder().decode(b64);
            return new Image(new ByteArrayInputStream(imgBytes));
        } catch (Exception e) {
            return null;
        }
    }
    // ---------------------------------------------------------

    // --- KLASICKÉ CHATOVACÍ METODY ---
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
        if (currentChatKey != null && currentReceiver != null) reloadHistoryInBack();
    }

    private void reloadHistoryInBack(){
        String user = NetworkManager.getInstance().getLoggedUser();
        new Thread(() -> {
            try {
                List<String> history  = NetworkManager.getInstance().fetchHistory(user, currentReceiver, currentChatKey);
                Platform.runLater(() -> {
                    clearChat();
                    addSystemMessage("--- 🔒 Zabezpečený chat s " + currentReceiver + " ---");
                    for (String msg : history) {
                        if (msg.startsWith("Ty: ")) {
                            addBubble(msg.substring(4), true); // Ustřihneme "Ty: "
                        }else if (msg.contains(": ")) {
                            int colonIndex = msg.indexOf(": ");
                            addBubble(msg.substring(colonIndex + 2), false); // Ustřihneme jméno příjemce
                        } else {
                            addSystemMessage(msg);
                        }
                    }
                });
            } catch (Exception e) { }
        }).start();
    }

    @FXML
    protected void handleLoadChat(){
        String receiver = targetUserField.getText().trim();
        if (receiver.isEmpty()){ return; }
        String user = NetworkManager.getInstance().getLoggedUser();
        if (user.equals(receiver)){ addSystemMessage(">> Nemůžeš psát sám sobě.\n"); return; }

        addSystemMessage(">> Vytvářím zabezpečené spojení s " + receiver + "...\n");
        targetUserField.setDisable(true);

        new Thread(() -> {
            try {
                File keyFile = new File(user + "_private.key");
                String privKeyStr = Files.readString(keyFile.toPath());
                PrivateKey userPrivKey = SecurityUtils.RSAUtils.getPrivateKeyFromString(privKeyStr);

                currentChatKey = NetworkManager.getInstance().AESScryptingForChat(user, receiver, userPrivKey);
                currentReceiver = receiver;

                Platform.runLater(() -> {
                    messageField.setDisable(false);
                    sendBtn.setDisable(false);
                    targetUserField.setDisable(false);
                    messageField.requestFocus();
                    //pfp of receiver
                    receiverProfileBox.setVisible(true);
                    receiverUserLabel.setText(receiver);
                    receiverImageCircle.setFill(javafx.scene.paint.Color.valueOf("#e5e5ea"));
                });
                reloadHistoryInBack();
                //download of receiver pfp
                new Thread(() -> {
                    String[] prefs = NetworkManager.getInstance().getPreferences(receiver);
                    String b64 = prefs[1];
                    Platform.runLater(() -> {
                        Image img = decodeBase64ToImage(b64);
                        if (img != null) {
                            receiverImageCircle.setFill(new ImagePattern(img));
                        }
                    });
                }).start();
            } catch (Exception e) {
                Platform.runLater(() -> {
                    addSystemMessage(">> Chyba: " + e.getMessage() + "\n");
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

        new Thread(() -> {
            try {
                boolean ok = NetworkManager.getInstance().sendChatMessage(user, currentReceiver, message, currentChatKey);
                Platform.runLater(() -> {
                    if (ok) {
                        messageField.clear();
                        reloadHistoryInBack();
                    } else {
                        addSystemMessage(">> Chyba při odesílání serveru.\n");
                    }
                    messageField.setDisable(false); sendBtn.setDisable(false); messageField.requestFocus();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    addSystemMessage(">> Kritická chyba šifrování: " + e.getMessage() + "\n");
                    messageField.setDisable(false); sendBtn.setDisable(false);
                });
            }
        }).start();
    }

    @FXML
    protected void handleNewChat() {
        currentChatKey = null; currentReceiver = null; targetUserField.clear(); targetUserField.setDisable(false);
        messageField.setDisable(true); sendBtn.setDisable(true); clearChat();
        addSystemMessage("Režim nového chatu. Zadej příjemce dole a klikni na 'Otevřít chat'.");
        receiverProfileBox.setVisible(false);
    }

    // --- METODY PRO ULOŽENÍ NA SERVER ---
    @FXML
    protected void toggleDarkMode() {
        isDarkMode = !isDarkMode;

        if (isDarkMode) {
            rootPane.getStyleClass().add("dark-mode"); // ZMĚNĚNO ZDE
            themeBtn.setText("☀");
        } else {
            rootPane.getStyleClass().remove("dark-mode"); // ZMĚNĚNO ZDE
            themeBtn.setText("🌙");
        }

        // Uložit do DB na pozadí
        new Thread(() -> {
            NetworkManager.getInstance().updatePreferences(NetworkManager.getInstance().getLoggedUser(), isDarkMode, currentBase64Avatar);
        }).start();
    }

    @FXML
    protected void handleProfileSettings(){
        String currentUser = NetworkManager.getInstance().getLoggedUser();

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Profil uživatele");

        VBox vbox = new VBox(20);
        vbox.setAlignment(Pos.CENTER);
        String bgColor = isDarkMode ? "#1e1e28" : "#ffffff";
        String textColor = isDarkMode ? "#ffffff" : "#1d1d1f";
        vbox.setStyle("-fx-padding: 40; -fx-background-color: " + bgColor + ";");

        Circle previewCircle = new Circle(60);
        previewCircle.setFill(profileImageCircle.getFill());
        previewCircle.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 15, 0, 0, 5);");

        Label nameLabel = new Label("@" + currentUser);
        nameLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: 900; -fx-text-fill: " + textColor + ";");

        Button changePicBtn = new Button("Vybrat novou profilovku");
        changePicBtn.setStyle("-fx-background-color: #007AFF; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-padding: 10 20; -fx-cursor: hand;");

        changePicBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Vyberte obrázek (Ideálně do 2 MB)"); // Omezení pro bezpečí Socketů
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Obrázky", "*.png", "*.jpg", "*.jpeg"));

            File file = fileChooser.showOpenDialog(dialog);
            if (file != null) {
                try {
                    // Převod na Base64 a uložení
                    currentBase64Avatar = encodeFileToBase64(file);

                    Image newImg = decodeBase64ToImage(currentBase64Avatar);
                    ImagePattern pattern = new ImagePattern(newImg);
                    previewCircle.setFill(pattern);
                    profileImageCircle.setFill(pattern);

                    // Uložit do DB na pozadí
                    new Thread(() -> {
                        NetworkManager.getInstance().updatePreferences(currentUser, isDarkMode, currentBase64Avatar);
                    }).start();

                } catch (Exception ex) { ex.printStackTrace(); }
            }
        });

        vbox.getChildren().addAll(previewCircle, nameLabel, changePicBtn);
        Scene scene = new Scene(vbox, 350, 400);
        dialog.setScene(scene);
        dialog.setResizable(false);
        dialog.show();
    }

    @FXML
    protected void handleSignOut() {
        if (autoRefreshTimeline != null) autoRefreshTimeline.stop();
        NetworkManager.getInstance().logout();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/graphics/login.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) rootPane.getScene().getWindow();
            stage.setScene(new Scene(root, 600, 600));
            stage.setTitle("Cricket - Přihlášení");
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void addBubble(String text, boolean isMe){
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().addAll("chat-bubble", isMe ? "bubble-me" : "bubble-other");

        javafx.scene.layout.HBox hBox = new javafx.scene.layout.HBox(label);
        hBox.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        chatContainer.getChildren().add(hBox);
    }

    private void addSystemMessage(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("bubble-system");

        javafx.scene.layout.HBox hbox = new javafx.scene.layout.HBox(label);
        hbox.setAlignment(Pos.CENTER);
        chatContainer.getChildren().add(hbox);
    }

    private void clearChat() {
        chatContainer.getChildren().clear();
    }
}