package org.example;

import utils.SecurityUtils;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import javax.crypto.SecretKey;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

public class NetworkManager {
    private static final int PORT = 14000;
    private String host = "127.0.0.1";
    private NetworkManager() {
        loadServerIp();
    }

    private void loadServerIp() {
        File ipFile = new File(getAppFolder(), "server_ip.txt");
        if (ipFile.exists()) {
            try {
                host = java.nio.file.Files.readString(ipFile.toPath()).trim();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            saveServerIp("127.0.0.1"); // Vytvoří soubor s výchozí IP
        }
    }
    public void saveServerIp(String ip) {
        this.host = ip;
        File ipFile = new File(getAppFolder(), "server_ip.txt");
        try (FileWriter fw = new FileWriter(ipFile)) {
            fw.write(ip);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Socket socket;
    private BufferedWriter out;
    private BufferedReader in;
    private String loggedUser; // Uchováme si, kdo je přihlášen

    // Singleton pattern - abychom měli jen jedno připojení pro celou aplikaci
    private static NetworkManager instance;
    public static NetworkManager getInstance() {
        if (instance == null) instance = new NetworkManager();
        return instance;
    }

    private File getAppFolder() {
        String appData = System.getenv("APPDATA");
        // Pojistka, kdyby to někdo spustil na Macu/Linuxu místo Windows
        if (appData == null) {
            appData = System.getProperty("user.home");
        }
        File appFolder = new File(appData, "Cricket");
        if (!appFolder.exists()) {
            appFolder.mkdirs(); // Vytvoří složku Cricket, pokud ještě neexistuje
        }
        return appFolder;
    }

    public PrivateKey getMyPrivateKey(String username) throws Exception {
        // Vytáhne privátní klíč ze správné složky Cricket
        File keyFile = new File(getAppFolder(), username + "_private.key");
        if (!keyFile.exists()) {
            throw new FileNotFoundException("Privátní klíč pro uživatele " + username + " nebyl nalezen.");
        }
        String privKeyStr = java.nio.file.Files.readString(keyFile.toPath());
        return SecurityUtils.RSAUtils.getPrivateKeyFromString(privKeyStr);
    }

    public void connect() throws IOException {
        if (socket == null || socket.isClosed()) {
            try {
                // 1. Načtení certifikátu zevnitř aplikace (složka resources)
                InputStream trustStoreStream = getClass().getResourceAsStream("/server.jks");
                if (trustStoreStream == null) {
                    throw new FileNotFoundException("Certifikát server.jks nebyl nalezen v resources!");
                }

                // 2. Vytvoření "trezoru" (KeyStore) a načtení našeho souboru pomocí hesla
                KeyStore trustStore = KeyStore.getInstance("JKS");
                trustStore.load(trustStoreStream, "tajneheslo".toCharArray());

                // 3. Vytvoření manažera, který bude tomuto konkrétnímu certifikátu věřit
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);

                // 4. Nastavení celého SSL spojení (řekneme mu, ať použije našeho manažera)
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, tmf.getTrustManagers(), null);

                // 5. Vytvoření bezpečného TLS (SSL) Socketu místo toho obyčejného
                SSLSocketFactory sslsf = sslContext.getSocketFactory();
                socket = sslsf.createSocket(host, PORT);

                // Klasické streamy pro čtení a zápis, ty zůstávají stejné
                out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            } catch (Exception e) {
                e.printStackTrace();
                throw new IOException("Nepodařilo se navázat zabezpečené spojení: " + e.getMessage());
            }
        }
    }

    public boolean login(String name, String pass) {
        try {
            connect(); // Ujistíme se, že jsme připojeni

            out.write("LOG:" + name + ":" + pass);
            out.newLine(); out.flush();

            String response = in.readLine();

            if (response != null && response.startsWith("AUTH_CHALLENGE")){
                String challenge = response.split(":")[1];

                // OPRAVA: Cesta směřuje do složky Cricket
                File keyFile = new File(getAppFolder(), name +"_private.key");
                if (!keyFile.exists()) return false;

                String privKeyStr = java.nio.file.Files.readString(keyFile.toPath());
                PrivateKey privateKey = SecurityUtils.RSAUtils.getPrivateKeyFromString(privKeyStr);
                String signature = SecurityUtils.RSAUtils.sign(challenge, privateKey);

                out.write("AUTH_RESPONSE:" + signature);
                out.newLine(); out.flush();
                response = in.readLine();
            }

            if ("LOGIN_OK".equals(response)) {
                this.loggedUser = name;
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean register(String name, String pass, String email) {
        try {
            connect();

            //generating keys
            java.security.KeyPair keyPair = SecurityUtils.RSAUtils.generateKeyPair();
            String pubKeyStr = SecurityUtils.RSAUtils.keyToString(keyPair.getPublic());
            String privKeyStr = SecurityUtils.RSAUtils.keyToString(keyPair.getPrivate());

            // OPRAVA: Soubor s privátním klíčem se vytvoří ve složce Cricket
            File keyFile = new File(getAppFolder(), name + "_private.key");
            try (FileWriter fw = new FileWriter(keyFile)) {
                fw.write(privKeyStr);
            }

            // Vytvoření hashe a odeslání na server
            String securityHash = SecurityUtils.createUserSecurityHash(privKeyStr, pubKeyStr);
            out.write("REG:" + name + ":" + pass + ":" + email + ":" + pubKeyStr + ":" + securityHash);
            out.newLine();
            out.flush();

            String response = in.readLine();
            return "REG_OK".equals(response);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized SecretKey AESScryptingForChat(String user, String receiver, PrivateKey myPrivKey) throws Exception {
        // OPRAVA: Cesta pro AES klíče směřuje do složky Cricket
        File aesFile = new File(getAppFolder(), "chat_" + user + "_" + receiver +".key");
        if (aesFile.exists()) {
            String aesKey = java.nio.file.Files.readString(aesFile.toPath());
            return SecurityUtils.AESUtils.stringToKey(aesKey);
        }

        out.write("JOIN_ROOM:" + user + ":" + receiver);
        out.newLine();
        out.flush();
        String response = in.readLine();

        if (response != null && response.startsWith("ROOM_OK:")){
            String encryptedAesKey = response.split(":")[1];
            String decryptedAesKey = SecurityUtils.RSAUtils.decryptKey(encryptedAesKey, myPrivKey);
            SecretKey chatKey = SecurityUtils.AESUtils.stringToKey(decryptedAesKey);

            try (FileWriter fw = new FileWriter(aesFile)) {
                fw.write(SecurityUtils.AESUtils.keyToString(chatKey));
            }
            return chatKey;
        } else if ("ROOM_MISSING".equals(response)) {
            SecretKey chatKey = SecurityUtils.AESUtils.generateAESKey();
            String chatKeyStr = SecurityUtils.AESUtils.keyToString(chatKey);

            out.write("GET_PUBKEY:" + receiver);
            out.newLine();
            out.flush();
            String pubKeyResp = in.readLine();

            if (pubKeyResp == null || !pubKeyResp.startsWith("PUBKEY:")){
                throw new Exception("Uživatel " + receiver + " neexistuje.");
            }
            PublicKey receiverPubKey = SecurityUtils.RSAUtils.getPublicKeyFromString(pubKeyResp.split(":")[1]);

            out.write("GET_PUBKEY:" + user);
            out.newLine();
            out.flush();
            String userPubKeyResp = in.readLine();
            PublicKey userPubKey = SecurityUtils.RSAUtils.getPublicKeyFromString(userPubKeyResp.split(":")[1]);

            String encryptedForReceiver = SecurityUtils.RSAUtils.encryptKey(chatKeyStr, receiverPubKey);
            String encryptedForUser = SecurityUtils.RSAUtils.encryptKey(chatKeyStr, userPubKey);

            out.write("CREATE_ROOM:" + user + ":" + receiver + ":" + encryptedForUser + ":" + encryptedForReceiver);
            out.newLine();
            out.flush();
            String createResponse = in.readLine();

            if (!"CREATE_ROOM_OK".equals(createResponse)) {
                throw new Exception("Server odmítl vytvořit místnost.");
            }

            try (FileWriter fw = new FileWriter(aesFile)) {
                fw.write(chatKeyStr);
            }
            return chatKey;
        }
        throw new Exception("Chyba komunikace se serverem.");
    }

    public synchronized List<String> fetchHistory(String user, String receiver, SecretKey chatKey) throws Exception {
        List<String> history = new ArrayList<>();
        out.write("HISTORY:" + user + ":" + receiver);
        out.newLine();
        out.flush();

        String line;
        while ((line = in.readLine()) != null) {
            if (line.equals("HIST_END")){
                break;
            }
            if (line.startsWith("HIST:")){
                String[] parts = line.split(":", 3);
                String sender = parts[1];
                String encryptedMsg = parts[2];
                try{
                    String decryptedMsg = SecurityUtils.AESUtils.decrypt(encryptedMsg, chatKey);
                    if (sender.equals(user)) {
                        history.add("Ty: " + decryptedMsg);
                    } else {
                        history.add(sender + ": " + decryptedMsg);
                    }
                }catch (Exception e) {
                    history.add(sender + ": [Šifrovaná zpráva - nelze přečíst]");
                }
            }
        }
        return history;
    }

    // 3. Odeslání nové šifrované zprávy
    public synchronized boolean sendChatMessage(String me, String receiver, String msg, SecretKey chatKey) throws Exception {
        String encryptedMsg = SecurityUtils.AESUtils.encrypt(msg, chatKey);
        out.write("MSG:" + me + ":" + receiver + ":" + encryptedMsg);
        out.newLine();
        out.flush();
        String resp = in.readLine();
        return "MSG OK".equals(resp);
    }

    public synchronized String[] getPreferences(String user){
        try {
            out.write("GET_PREFS:" + user);
            out.newLine();
            out.flush();
            String resp = in.readLine();
            if (resp != null && resp.startsWith("PREFS:")){
                String[] p = resp.split(":", 3);
                if (p.length >= 3){
                    return new String[]{ p[1], p[2] };
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return new String[]{"false", "NULL"};
    }

    public synchronized void updatePreferences(String user, boolean darkMode, String base64Avatar) {
        try {
            String b64 = (base64Avatar == null || base64Avatar.isEmpty()) ? "NULL" : base64Avatar;
            out.write("UPDATE_PREFS:" + user + ":" + darkMode + ":" + b64);
            out.newLine();
            out.flush();
            in.readLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized List<String> getRecentChats(String user){
        List<String> chats = new ArrayList<>();
        try{
            out.write("GET_RECENT_CHATS:" + user);
            out.newLine();
            out.flush();
            String resp = in.readLine();

            if (resp != null && resp.startsWith("RECENT_CHATS:")){
                if (resp.length() > "RECENT_CHATS:".length()){
                    String data = resp.substring("RECENT_CHATS:".length());
                    String[] users = data.split(",");
                    for (String u : users){
                        if (!u.trim().isEmpty()){
                            chats.add(u.trim());
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return chats;
    }

    public void logout() {
        this.loggedUser = null;
    }

    public String getLoggedUser() {
        return loggedUser;
    }
    public String getServerIp() {
        return host;
    }
}