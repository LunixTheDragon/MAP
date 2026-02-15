package org.example;

import javax.crypto.SecretKey;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Scanner;

public class Client {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 14000;

    public static void main(String[] args) {
        System.out.println("Připojuji se k serveru...");

        try (Socket socket = new Socket(HOST, PORT);
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("--- MENU ---");
            System.out.println("1. Registrace");
            System.out.println("2. Přihlášení");
            String choice = scanner.nextLine();

            String myName = "";
            boolean isAuthenticated = false;

            if (choice.equals("1")) {
                System.out.print("Jméno: "); String name = scanner.nextLine();
                System.out.print("Heslo: "); String pass = scanner.nextLine();
                System.out.print("Email: "); String email = scanner.nextLine();

                try {
                    KeyPair keyPair = SecurityUtils.RSAUtils.generateKeyPair();
                    String pubKeyStr = SecurityUtils.RSAUtils.keyToString(keyPair.getPublic());
                    String privKeyStr = SecurityUtils.RSAUtils.keyToString(keyPair.getPrivate());

                    try(FileWriter fw = new FileWriter(name +"_private.key" )){
                        fw.write(privKeyStr);
                    }
                    System.out.println("Váš privátní klíč byl uložen do souboru: " + name + "_private.key");
                    String securityHash = SecurityUtils.createUserSecurityHash(privKeyStr, pubKeyStr);
                    out.write("REG:" + name + ":" + pass + ":" + email + ":" + pubKeyStr + ":" + securityHash );
                    out.newLine(); out.flush();
                } catch (Exception e) {
                    System.err.println("Chyba při generování klíčů: " + e.getMessage());
                    return;
                }

                String response = in.readLine();
                if ("REG_OK".equals(response)) {
                    System.out.println("Registrace úspěšná! Můžeš se přihlásit.");
                    return;
                } else {
                    System.out.println("Chyba registrace.");
                    return;
                }

            } else if (choice.equals("2")) {
                System.out.print("Jméno: "); String name = scanner.nextLine();
                System.out.print("Heslo: "); String pass = scanner.nextLine();
                out.write("LOG:" + name + ":" + pass);
                out.newLine(); out.flush();

                String response = in.readLine();

                if (response != null && response.startsWith("AUTH_CHALLENGE")){
                    String challenge = response.split(":")[1];
                    try {
                        File keyFile = new File(name +"_private.key");
                        if (!keyFile.exists()) {
                            System.err.println("Chyba: Soubor s klíčem neexistuje!");
                            return;
                        }
                        String privKeyStr = java.nio.file.Files.readString(keyFile.toPath());
                        PrivateKey privateKey = SecurityUtils.RSAUtils.getPrivateKeyFromString(privKeyStr);
                        String signature = SecurityUtils.RSAUtils.sign(challenge, privateKey);
                        out.write("AUTH_RESPONSE:" + signature);
                        out.newLine(); out.flush();
                        response = in.readLine();
                    }  catch (Exception e) {
                        System.err.println("Chyba při zpracování klíče: " + e.getMessage());
                        return;
                    }
                }

                if ("LOGIN_OK".equals(response)) {
                    System.out.println("Přihlášení ÚSPĚŠNÉ! Vítej.");
                    myName = name;
                    isAuthenticated = true;
                } else {
                    System.err.println("CHYBA: Přihlášení selhalo.");
                    return;
                }
            }

            if (isAuthenticated) {
                System.out.println("S kým si chceš psát?");
                String receiver1 = scanner.nextLine();
                System.out.println("--- CHAT s uživatelem " + receiver1 + " ---");

                try{
                    File myFile = new File(myName + "_private.key");
                    String myString = java.nio.file.Files.readString(myFile.toPath());
                    PrivateKey privateKey = SecurityUtils.RSAUtils.getPrivateKeyFromString(myString);

                    SecretKey chatKEy = null;
                    File aesFile = new File("chat_" + myName + "_" + receiver1 + ".key" );

                    if (aesFile.exists()){
                        System.out.println("Načítám uložený šifrovací klíč z disku...");
                        String storedKey = java.nio.file.Files.readString(aesFile.toPath());
                        chatKEy = SecurityUtils.AESUtils.stringToKey(storedKey);
                    } else {
                        System.out.println("Hledám existující místnost...");
                        out.write("JOIN_ROOM:" + myName + ":" + receiver1);
                        out.newLine(); out.flush();

                        String serverResponse = in.readLine();

                        if (serverResponse != null && serverResponse.startsWith("ROOM_OK:")){
                            String encryptedAesKey = serverResponse.split(":")[1];
                            String decryptedAesKey = SecurityUtils.RSAUtils.decryptKey(encryptedAesKey, privateKey);
                            chatKEy = SecurityUtils.AESUtils.stringToKey(decryptedAesKey);

                            // Uložení klíče pro příště
                            try(FileWriter fw = new FileWriter(aesFile)){
                                fw.write(SecurityUtils.AESUtils.keyToString(chatKEy));
                            }
                            System.out.println("Klíč stažen ze serveru a uložen.");

                        } else if ("ROOM_MISSING".equals(serverResponse)) {
                            System.out.println("Místnost neexistuje. Zakládám nové zabezpečené spojení...");

                            chatKEy = SecurityUtils.AESUtils.generateAESKey();
                            String chatKeyStr = SecurityUtils.AESUtils.keyToString(chatKEy);

                            // 1. Získat klíč příjemce
                            out.write("GET_PUBKEY:" + receiver1);
                            out.newLine(); out.flush();
                            String pubKeyResp = in.readLine();

                            if (pubKeyResp == null || !pubKeyResp.startsWith("PUBKEY:")){
                                System.out.println("CHYBA: Uživatel " + receiver1 + " neexistuje.");
                                return; // Konec, nemůžeme šifrovat
                            }
                            PublicKey receiverPubKey = SecurityUtils.RSAUtils.getPublicKeyFromString(pubKeyResp.split(":")[1]);

                            // 2. Získat můj klíč
                            out.write("GET_PUBKEY:" + myName);
                            out.newLine(); out.flush();
                            String myPubKeyResp = in.readLine();
                            PublicKey myPubKey = SecurityUtils.RSAUtils.getPublicKeyFromString(myPubKeyResp.split(":")[1]);

                            String encryptedForReceiver = SecurityUtils.RSAUtils.encryptKey(chatKeyStr, receiverPubKey);
                            String encryptedForMe = SecurityUtils.RSAUtils.encryptKey(chatKeyStr, myPubKey);

                            out.write("CREATE_ROOM:" + myName + ":" + receiver1 + ":" + encryptedForMe + ":" + encryptedForReceiver);
                            out.newLine(); out.flush();

                            String createResp = in.readLine();
                            if (!"CREATE_ROOM_OK".equals(createResp)) {
                                System.out.println("Chyba při vytváření místnosti na serveru!");
                                return;
                            }

                            // DŮLEŽITÉ: Uložit klíč i v tomto případě
                            try(FileWriter fw = new FileWriter(aesFile)){
                                fw.write(chatKeyStr);
                            }
                            System.out.println("Místnost založena a klíč uložen.");
                        }
                    }
                out.write("HISTORY:" + myName + ":" + receiver1);
                out.newLine(); out.flush();

                String line;
                while ((line = in.readLine()) != null) {
                    if (line.equals("HIST_END")) break;
                    if (line.startsWith("HIST:")) {
                        String[] parts = line.split(":", 3);
                        String sender = parts[1];
                        String encryptedMsg = parts[2];

                        try{
                            //AES decrypt
                            String decryptedMsg =   SecurityUtils.AESUtils.decrypt(encryptedMsg, chatKEy);
                            System.out.println(sender + ": " + decryptedMsg);

                        }catch(Exception e){
                            System.out.println("Error decrypting message: " + e.getMessage());
                        }
                    }
                }



                    // Chat smyčka
                    while (true){
                        System.out.print("You: "); // print místo println, aby to bylo na řádku
                        String msg = scanner.nextLine();
                        if ("konec".equalsIgnoreCase(msg)) break;

                        String encryptedMsg = SecurityUtils.AESUtils.encrypt(msg, chatKEy);
                        out.write("MSG:" + myName + ":" + receiver1 + ":" + encryptedMsg);
                        out.newLine(); out.flush();

                        String resp = in.readLine();
                        if (!"MSG OK".equals(resp)){
                            System.out.println("Server error: " + resp);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("CRITICAL ERROR: " + e.getMessage());
                    e.printStackTrace();
                }
            }

        } catch (IOException e) {
            System.err.println("Chyba spojení: " + e.getMessage());
        }
    }
}