package org.example;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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

                out.write("REG:" + name + ":" + pass + ":" + email);
                out.newLine();
                out.flush();

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

                if ("LOGIN_OK".equals(response)) {
                    System.out.println("Přihlášení ÚSPĚŠNÉ! Vítej.");
                    myName = name;
                    isAuthenticated = true; // Pustíme ho dál
                } else {
                    System.err.println("CHYBA: Špatné jméno nebo heslo!");
                    return; // Konec programu, nepustíme ho k chatu
                }
            }

            // 3. Změna: Do chatu pustíme jen ověřeného
            if (isAuthenticated) {
                System.out.println("--- CHAT ---");
                while (true) {
                    System.out.println("Komu");
                    String receiver = scanner.nextLine();

                    System.out.println("Zprava ");
                    String msg = scanner.nextLine();
                    if ("konec".equalsIgnoreCase(msg)) break;

                    out.write("MSG:" + myName + ":" +receiver + ":" + msg);
                    out.newLine();
                    out.flush();
                }
            }

        } catch (IOException e) {
            System.err.println("Chyba spojení: " + e.getMessage());
        }
    }
}