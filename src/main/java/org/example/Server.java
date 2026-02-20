package org.example;

import utils.Conn;
import utils.SecurityUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private static final int PORT = 14000;

    public static void main(String[] args) {
        System.out.println("Server startuje na portu " + PORT + "...");
        Conn db = new Conn();
        int maxClients = Runtime.getRuntime().availableProcessors() * 2;
        ExecutorService pool = Executors.newFixedThreadPool(maxClients);

        try(ServerSocket serverSocket= new ServerSocket(PORT)){
            while(true){
                System.out.println("Waiting for Client");
                Socket clientSocket = serverSocket.accept();
                System.out.println("Klient připojen: " + clientSocket.getRemoteSocketAddress());
                pool.execute(() -> handleClient(clientSocket, db));
            }
        }catch(IOException e){
            System.out.println("Server error: " + e.getMessage());
        }
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
                String command = parts[0];

                try {
                    switch (command) {
                        case "REG":
                            if (parts.length >= 6) {
                                boolean success = registerUser(db, parts[1], parts[2], parts[3], parts[4], parts[5]);
                                out.write(success ? "REG_OK" : "REG_ERR");
                                out.newLine();
                                out.flush();
                            }
                            break;

                        case "LOG":
                            if (parts.length < 3) { out.write("LOGIN_ERR"); out.newLine(); out.flush(); break; }
                            String username = parts[1];
                            String pass = parts[2];
                            String userPubKeyStr = loginUser(db, username, pass);

                            if (userPubKeyStr != null) {
                                String challenge = String.valueOf(Math.random());
                                out.write("AUTH_CHALLENGE:" + challenge);
                                out.newLine();
                                out.flush();

                                String responseLine = in.readLine();
                                if (responseLine != null && responseLine.startsWith("AUTH_RESPONSE:")) {
                                    String signature = responseLine.split(":")[1];
                                    try {
                                        PublicKey pk = SecurityUtils.RSAUtils.getPublicKeyFromString(userPubKeyStr);
                                        boolean isSignatureValid = SecurityUtils.RSAUtils.verify(challenge, signature, pk);
                                        out.write(isSignatureValid ? "LOGIN_OK" : "LOGIN_ERR_SIG");
                                    } catch (Exception e) {
                                        out.write("LOGIN_ERR");
                                    }
                                } else {
                                    out.write("LOGIN_ERR");
                                }
                            } else {
                                out.write("LOGIN_ERR");
                            }
                            out.newLine();
                            out.flush();
                            break;

                        case "GET_PUBKEY":
                            if (parts.length >= 2) {
                                String targetUser = parts[1];
                                String key = getPublicKey(db, targetUser);
                                out.write(key != null ? "PUBKEY:" + key : "ERR_NO_USER");
                                out.newLine();
                                out.flush();
                            }
                            break;

                        case "JOIN_ROOM":
                            if (parts.length >= 3) {
                                String me = parts[1];
                                String other = parts[2];
                                String encryptedKey = getRoomKey(db, me, other);
                                out.write(encryptedKey != null ? "ROOM_OK:" + encryptedKey : "ROOM_MISSING");
                                out.newLine();
                                out.flush();
                            }
                            break;

                        case "CREATE_ROOM":
                            if (parts.length >= 5) {
                                boolean created = createRoom(db, parts[1], parts[2], parts[3], parts[4]);
                                out.write(created ? "CREATE_ROOM_OK" : "CREATE_ROOM_ERR");
                                out.newLine();
                                out.flush();
                            }
                            break;

                        case "MSG":
                            if (parts.length >= 4) {
                                String sender = parts[1];
                                String receiver = parts[2];
                                String message = parts[3];
                                if (!userExists(db, receiver)) {
                                    out.write("USER_NOT_FOUND");
                                } else {
                                    saveMessageToDb(db, sender, receiver, message);
                                    out.write("MSG OK");
                                }
                                out.newLine();
                                out.flush();
                            }
                            break;

                        case "HISTORY":
                            if (parts.length >= 3) {
                                loadHistory(db, out, parts[1], parts[2]);
                            }
                            break;

                        default:
                            System.out.println("Neznámý příkaz: " + command);
                    }
                } catch (Exception e) {
                    System.err.println("Chyba při zpracování příkazu " + command + ": " + e.getMessage());
                    e.printStackTrace();
                    // V případě chyby pošleme klientovi aspoň něco, ať nezamrzne
                    out.write("SERVER_ERROR");
                    out.newLine();
                    out.flush();
                }
            }
        } catch (IOException e) {
            System.out.println("Klient odpojen: " + clientSocket.getRemoteSocketAddress());
        }
    }

    // --- DB METODY ---

    private static void saveMessageToDb(Conn db, String sender, String receiver, String text) {
        String sql = "INSERT INTO messages (sender_name, receiver_name, message_text) VALUES (?, ?, ?)";
        try (Connection conn = db.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, receiver);
            pstmt.setString(3, text);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Chyba SQL MSG: " + e.getMessage());
        }
    }

    private static boolean registerUser(Conn db, String name, String password, String email, String public_Key, String securityHash) {
        if(userExists(db, name)) return false;
        String sql = "INSERT INTO users (name, password, email, public_Key, security_hash) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = db.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)){
            pstmt.setString(1, name);
            pstmt.setString(2, SecurityUtils.hashPassword(password));
            pstmt.setString(3, email);
            pstmt.setString(4, public_Key);
            pstmt.setString(5, securityHash);
            pstmt.executeUpdate();
            System.out.println("User " + name + " registered.");
            return true;
        } catch (SQLException e) {
            System.err.println("Chyba REG: " + e.getMessage());
            return false;
        }
    }

    private static String loginUser(Conn db, String name, String password) {
        String sql = "SELECT password, public_Key FROM users WHERE name = ?";
        try (Connection conn = db.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)){
            pstmt.setString(1, name);
            var rs = pstmt.executeQuery();
            if(rs.next()){
                String storedHash = rs.getString("password");
                String publicKey = rs.getString("public_Key");
                if (SecurityUtils.checkPassword(password, storedHash)) {
                    return publicKey;
                }
            }
        } catch(SQLException e){
            e.printStackTrace();
        }
        return null;
    }

    private static void loadHistory(Conn db, BufferedWriter out, String user1, String user2) {
        String sql = "SELECT sender_name, message_text FROM messages WHERE (sender_name = ? AND receiver_name = ?) OR (sender_name = ? AND receiver_name = ?) ORDER BY id ASC";
        try (Connection conn = db.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user1); pstmt.setString(2, user2);
            pstmt.setString(3, user2); pstmt.setString(4, user1);
            var rs = pstmt.executeQuery();
            while (rs.next()) {
                out.write("HIST:" + rs.getString("sender_name") + ":" + rs.getString("message_text"));
                out.newLine();
            }
            out.flush();
            out.write("HIST_END");
            out.newLine();
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean userExists(Conn db, String username){
        String sql = "SELECT 1 FROM users WHERE name = ?";
        try (Connection conn = db.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)){
            pstmt.setString(1, username);
            return pstmt.executeQuery().next();
        } catch(SQLException e){
            return false;
        }
    }

    private static String getPublicKey(Conn db, String username){
        String sql = "SELECT public_Key FROM users WHERE name = ?";
        try (Connection conn = db.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)){
            pstmt.setString(1, username);
            var rs = pstmt.executeQuery();
            if (rs.next()) return rs.getString("public_Key");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static boolean createRoom(Conn db, String u1, String u2, String keyForU1, String keyForU2) {
        String roomId = (u1.compareTo(u2) < 0) ? u1 + "_" + u2 : u2 + "_" + u1;
        String sql = "INSERT INTO rooms (room_id, user1_hash, user2_hash, aes_key_for_u1, aes_key_for_u2) VALUES (?, ?, ?, ?, ?)";
        try(Connection conn = db.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)){
            pstmt.setString(1, roomId);
            pstmt.setString(2, u1);
            pstmt.setString(3, u2);
            pstmt.setString(4, keyForU1);
            pstmt.setString(5, keyForU2);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Room create error: " + e.getMessage());
            return false;
        }
    }

    private static String getRoomKey(Conn db, String me, String other) {
        // Oprava: Získáváme user1_hash abychom věděli, který klíč vrátit
        String sql = "SELECT user1_hash, aes_key_for_u1, aes_key_for_u2 FROM rooms WHERE (user1_hash = ? AND user2_hash = ?) OR (user1_hash = ? AND user2_hash = ?)";
        try(Connection conn = db.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, me); pstmt.setString(2, other);
            pstmt.setString(3, other); pstmt.setString(4, me);
            var rs = pstmt.executeQuery();
            if (rs.next()) {
                String u1 = rs.getString("user1_hash");
                return u1.equals(me) ? rs.getString("aes_key_for_u1") : rs.getString("aes_key_for_u2");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}