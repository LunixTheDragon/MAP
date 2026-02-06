package org.example;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.Scanner;

public class Client {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 14000;

    public static void main(String[] args) {
        System.out.println("Připojuji se k serveru...");

        try (Socket socket = new Socket(HOST, PORT);
             // Pro psaní serveru
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
             // 1. Změna: Pro čtení OD serveru (aby klient slyšel odpověď)
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("--- MENU ---");
            System.out.println("1. Registrace");
            System.out.println("2. Přihlášení");
            String choice = scanner.nextLine();

            String myName = "";
            boolean isAuthenticated = false; // Pomocná proměnná

            if (choice.equals("1")) {
                // ... načtení údajů ...
                System.out.print("Jméno: "); String name = scanner.nextLine();
                System.out.print("Heslo: "); String pass = scanner.nextLine();
                System.out.print("Email: "); String email = scanner.nextLine();

                try {
                    //generating keys

                    KeyPair keyPair = SecurityUtils.RSAUtils.generateKeyPair();
                    String pubKeyStr = SecurityUtils.RSAUtils.keyToString(keyPair.getPublic());
                    String privKeyStr = SecurityUtils.RSAUtils.keyToString(keyPair.getPrivate());

                    //Saving private key on disc
                    try(FileWriter fw = new FileWriter(name +"_private.key" )){
                        fw.write(privKeyStr);
                    }
                    System.out.println("Váš privátní klíč byl uložen do souboru: " + name + "_private.key");
                    System.out.println("NIKOMU HO NEPOSÍLEJTE!");

                    //sending reg to server (adding public key on the end)

                    out.write("REG:" + name + ":" + pass + ":" + email + ":" + pubKeyStr);
                    out.newLine();
                    out.flush();

                } catch (Exception e) {
                    System.err.println("Chyba při generování klíčů: " + e.getMessage());
                    return;
                }


                // Čekáme na odpověď serveru
                String response = in.readLine();
                if ("REG_OK".equals(response)) {
                    System.out.println("Registrace úspěšná! Můžeš se přihlásit (restartuj app).");
                    return; // Ukončíme, ať se přihlásí znovu
                } else {
                    System.out.println("Chyba registrace (jméno asi existuje).");
                    return;
                }

            } else if (choice.equals("2")) {
                System.out.print("Jméno: "); String name = scanner.nextLine();
                System.out.print("Heslo: "); String pass = scanner.nextLine();

                out.write("LOG:" + name + ":" + pass);
                out.newLine();
                out.flush();

                // 2. Změna: Čekáme, co řekne server
                String response = in.readLine();

                if (response.startsWith("AUTH_CHALLENGE")){
                    String challenge = response.split(":")[1];
                    System.out.println("Server vyžaduje ověření klíčem. Hledám klíč...");

                    try {
                        // Načtení privátního klíče ze souboru
                        File keyFile = new File(name +"_private.key");
                        if (!keyFile.exists()) {
                            System.err.println("Chyba: Soubor s klíčem '" + keyFile.getName() + "' neexistuje! Nemůžete se přihlásit.");
                            return;
                        }

                        String privKeyStr = java.nio.file.Files.readString(keyFile.toPath());

                        // Převod na objekt PrivateKey (použijeme novou metodu z SecurityUtils)
                        PrivateKey privateKey = SecurityUtils.RSAUtils.getPrivateKeyFromString(privKeyStr);

                        // Podepíšeme tu "výzvu", co poslal server
                        String signature = SecurityUtils.RSAUtils.sign(challenge, privateKey);

                        out.write("AUTH_RESPONSE:" + signature);
                        out.newLine();
                        out.flush();

                        // 3. Čekáme na finální verdikt
                        response = in.readLine();
                    }  catch (Exception e) {
                        System.err.println("Chyba při zpracování klíče: " + e.getMessage());
                        return;
                }
                }
                

                if ("LOGIN_OK".equals(response)) {
                    System.out.println("Přihlášení ÚSPĚŠNÉ! Vítej.");
                    myName = name;
                    isAuthenticated = true; // Pustíme ho dál
                } else if ("LOGIN_ERR_SIG".equals(response)) {
                    System.err.println("CHYBA: Ověření klíče selhalo! Máte správný soubor?");
                    return;
                } else {
                    System.err.println("CHYBA: Špatné jméno nebo heslo!");
                    return; // Konec programu, nepustíme ho k chatu
                }
            }
            System.out.println("S kým si chceš psát?");
            String receiver1 = scanner.nextLine();

            // Požádáme server o historii
            out.write("HISTORY:" + myName + ":" + receiver1);
            out.newLine();
            out.flush();

            // Čteme historii, dokud nepřijde konec
            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals("HIST_END")) break; // Konec načítání

                if (line.startsWith("HIST:")) {
                    String[] parts = line.split(":", 3); // Rozdělíme na 3 části
                    System.out.println(parts[1] + ": " + parts[2]); // Vypíše: "Pepa: Ahoj"
                }
            }
            // 3. Změna: Do chatu pustíme jen ověřeného
            if (isAuthenticated) {
                System.out.println("--- CHAT s uživatelem " + receiver1 + " ---"); // Informace pro uživatele

                    while (true) {

                    System.out.println("You ");
                    String msg = scanner.nextLine();

                    if ("konec".equalsIgnoreCase(msg)) break;

                    out.write("MSG:" + myName + ":" +receiver1 + ":" + msg);
                    out.newLine();
                    out.flush();

                    String response = in.readLine();

                    switch (response) {
                        case "MSG_OK" -> {

                        }
                        case "USER_NOT_FOUND" ->
                                System.out.println(" Uživatel neexistuje");
                        default ->
                                System.out.println("Server: " + response);
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("Chyba spojení: " + e.getMessage());
        }
    }
}