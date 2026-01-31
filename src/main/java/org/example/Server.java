package org.example;

import controllers.Conn;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class Server {

    private static final int PORT = 14000;

    public static void main(String[] args) {
        System.out.println("Server startuje na portu " + PORT + "...");
        Conn db = new Conn();

        // Try-with-resources pro automatické zavření socketu
        try(ServerSocket serverSocket= new ServerSocket(PORT)){
            while(true){
                System.out.println("Waiting for Client");
                Socket clientSocket = serverSocket.accept(); //waiting on client
                System.out.println("Klient připojen: " + clientSocket.getRemoteSocketAddress());

                handleClient(clientSocket, db);
            }
        }catch(IOException e){
            System.out.println("Server error: " + e.getMessage());
        }
       /*


        // Try-with-resources pro automatické zavření socketu

            // Čekání na klienta (blokující operace)
            Socket clientSocket = serverSocket.accept(); //waiting on client
            System.out.println("Klient připojen: " + clientSocket.getRemoteSocketAddress());

            // V Server.java uvnitř main metody

// 1. Změna: Přidáme 'BufferedWriter out' do závorek try(...)
            // V Server.java uvnitř main metody

// 1. Změna: Přidáme 'BufferedWriter out' do závorek try(...)
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println("Přijato: " + line);
                    String[] parts = line.split(":");
                    if (parts.length < 2) continue;

                    String command = parts[0];

                    if (command.equals("REG")) {
                        // ... (tvůj kód pro registraci) ...
                        if (parts.length >= 4) {
                            boolean success = registerUser(db, parts[1], parts[2], parts[3]);
                            // Server odpoví klientovi výsledkem
                            if (success) {
                                out.write("REG_OK");
                            } else {
                                out.write("REG_ERR");
                            }
                            out.newLine(); // Důležité! Ukončit řádek
                            out.flush();   // Odeslat hned
                        }

                    } else if (command.equals("LOG")) {
                        // ... (tvůj kód pro login) ...
                        boolean success = false;
                        if (parts.length >= 3) {
                            success = loginUser(db, parts[1], parts[2]);
                        }

                        // 2. Změna: Server pošle výsledek klientovi
                        if (success) {
                            System.out.println("Uživatel " + parts[1] + " přihlášen.");
                            out.write("LOGIN_OK"); // Tajný kód pro úspěch
                        } else {
                            System.out.println("Chybné přihlášení: " + parts[1]);
                            out.write("LOGIN_ERR"); // Tajný kód pro chybu
                        }
                        out.newLine(); // Důležité!
                        out.flush();   // Odeslat

                    } else if (command.equals("MSG")) {
                        // ... (tvůj kód pro zprávy) ...
                        if (parts.length >= 3) {
                            saveMessageToDb(db, parts[1], parts[2]);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }*/
    }
    private static void handleClient(Socket clientSocket, Conn db) {
        try (
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8)
                );
                BufferedWriter out = new BufferedWriter(
                        new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8)
                )
        ) {
            String line;

            while ((line = in.readLine()) != null) {
                System.out.println("Přijato: " + line);

                String[] parts = line.split(":");
                if (parts.length < 2) continue;

                String command = parts[0];

                switch (command) {

                    case "REG":
                        if (parts.length >= 4) {
                            boolean success = registerUser(db, parts[1], parts[2], parts[3]);
                            out.write(success ? "REG_OK" : "REG_ERR");
                            out.newLine();
                            out.flush();
                        }
                        break;

                    case "LOG":
                        boolean success =
                                parts.length >= 3 && loginUser(db, parts[1], parts[2]);

                        out.write(success ? "LOGIN_OK" : "LOGIN_ERR");
                        out.newLine();
                        out.flush();
                        break;

                    case "MSG":
                        if (parts.length >= 3) {
                            saveMessageToDb(db, parts[1], parts[2]);
                        }
                        break;

                    default:
                        System.out.println("Neznámý příkaz: " + command);
                }
            }

        } catch (IOException e) {
            System.out.println("Klient odpojen: " + clientSocket.getRemoteSocketAddress());
        }
    }

    private static void saveMessageToDb(Conn db, String sender, String text) {
        String sql = "INSERT INTO messages (sender_name, message_text) VALUES (?, ?)";

        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, sender);
            pstmt.setString(2, text);
            pstmt.executeUpdate();
            System.out.println("-> Uloženo do DB.");

        } catch (SQLException e) {
            System.err.println("Chyba SQL: " + e.getMessage());
        }
    }

    private static boolean registerUser(Conn db, String name, String password, String email) {
        String sql = "INSERT INTO users (name, password, email) VALUES (?, ?, ?)";

        try (Connection conn = db.connect();
            PreparedStatement pstmt = conn.prepareStatement(sql)){
                pstmt.setString(1, name);
                pstmt.setString(2, password);
                pstmt.setString(3, email);

                pstmt.executeUpdate();
                System.out.println("User " + name + " registrated.");
                return true;
            } catch (SQLException e) {
            System.out.println("user " + name + " already exists.");
             return false;
        }
    }
    private static boolean loginUser(Conn db, String name, String password) {
        String sql = "SELECT * FROM users WHERE name = ? AND password = ?";

        try (Connection conn = db.connect();
            PreparedStatement pstmt = conn.prepareStatement(sql)){

            pstmt.setString(1, name);
            pstmt.setString(2, password);

            return pstmt.executeQuery().next();
        }catch(SQLException e){
            e.printStackTrace();
            return false;
        }
    }
}