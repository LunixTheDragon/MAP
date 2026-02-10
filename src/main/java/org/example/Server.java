package org.example;

import controllers.Conn;

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
        // Try-with-resources pro automatické zavření socketu
        try(ServerSocket serverSocket= new ServerSocket(PORT)){
            while(true){
                System.out.println("Waiting for Client");
                Socket clientSocket = serverSocket.accept(); //waiting on client
                System.out.println("Klient připojen: " + clientSocket.getRemoteSocketAddress());

                pool.execute(() ->handleClient(clientSocket, db));
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
                if (parts.length < 2) continue;

                String command = parts[0];

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
                        // 1. Fáze: Ověření hesla
                        if (parts.length < 3) {
                            out.write("LOGIN_ERR"); out.newLine(); out.flush();
                            break;
                        }

                        String username = parts[1];
                        String pass = parts[2];

                        // Získáme veřejný klíč z DB, pokud heslo sedí
                        String userPubKeyStr = loginUser(db, username, pass);

                        if (userPubKeyStr != null) {
                            // Heslo je správně, teď 2. Fáze: Challenge (Výzva)

                            // Vygenerujeme náhodný řetězec (např. náhodné číslo)
                            String challenge = String.valueOf(Math.random());

                            // Pošleme výzvu klientovi
                            out.write("AUTH_CHALLENGE:" + challenge);
                            out.newLine();
                            out.flush();

                            // Čekáme na odpověď (podpis)
                            String responseLine = in.readLine();

                            if (responseLine != null && responseLine.startsWith("AUTH_RESPONSE:")) {
                                String signature = responseLine.split(":")[1];

                                try {
                                    // Převedeme String klíč na PublicKey objekt
                                    PublicKey pk = SecurityUtils.RSAUtils.getPublicKeyFromString(userPubKeyStr);

                                    // Ověříme, zda klient podepsal náš "challenge" správně
                                    boolean isSignatureValid = SecurityUtils.RSAUtils.verify(challenge, signature, pk);

                                    if (isSignatureValid) {
                                        out.write("LOGIN_OK");
                                    } else {
                                        System.out.println("Podpis nesouhlasí!");
                                        out.write("LOGIN_ERR_SIG"); // Chyba podpisu
                                    }

                                } catch (Exception e) {
                                    System.out.println("Chyba krypto: " + e.getMessage());
                                    out.write("LOGIN_ERR");
                                }
                            } else {
                                out.write("LOGIN_ERR");
                            }
                        } else {
                            // Špatné jméno nebo heslo
                            out.write("LOGIN_ERR");
                        }

                        out.newLine();
                        out.flush();
                        break;

                    case "GET_PUBKEY":
                        if (parts.length >= 2) {
                            String targetUser = parts[1];
                            String key = getPublicKey(db, targetUser);
                            if (key != null) {
                                out.write("PUBKEY:" + key);
                            }else{
                                out.write("ERR_NO_USER");
                                out.newLine();
                                out.flush();
                            }
                        }
                        break;

                    case "JOIN_ROOM":
                        if (parts.length >= 3) {
                            String me = parts[1];
                            String other = parts[2];
                            String encryptedKey = getRoomKey(db, me, other);

                            if (encryptedKey != null) {
                                out.write("ROOM_OK:" + encryptedKey);
                            }else{
                                out.write("ROOM_MISSING");
                            }
                            out.newLine();
                            out.flush();
                        }
                        break;

                    case "CREATE_ROOM":
                        if (parts.length >= 5) {
                            boolean created = createRoom(db, parts[1], parts[2], parts[3], parts[4]);
                            if (created) {
                                out.write("CREATE_ROOM_OK");
                            } else {
                                out.write("CREATE_ROOM_ERR");
                            }
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
                                out.newLine();
                                out.flush();
                            }else{
                                saveMessageToDb(db, sender, receiver, message);
                                out.write("MSG OK");
                                out.newLine();
                                out.flush();
                            }
                        }
                        break;
                    case "HISTORY":
                        if (parts.length >= 3) {
                            String me = parts[1];      // Kdo žádá (já)
                            String other = parts[2];   // S kým si píšu

                            // Načteme zprávy z DB
                            loadHistory(db, out, me, other);
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



    private static void saveMessageToDb(Conn db, String sender,String receiver, String text) {
        String sql = """
        INSERT INTO messages (sender_name, receiver_name, message_text)
        VALUES (?, ?, ?)
        """;
        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, sender);
            pstmt.setString(2, receiver);
            pstmt.setString(3, text);
            pstmt.executeUpdate();
            System.out.println("-> Zpráva od " + sender + " pro " + receiver + " uložena.");

        } catch (SQLException e) {
            System.err.println("Chyba SQL: " + e.getMessage());
        }
    }

    private static boolean registerUser(Conn db, String name, String password, String email, String public_Key, String securityHash) {
       if(userExists(db, name)){

           System.out.println("Registrace zamítnuta: Uživatel " + name + " již existuje.");
           return false;

       }
        String sql = "INSERT INTO users (name, password, email, public_Key, security_hash) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = db.connect();
            PreparedStatement pstmt = conn.prepareStatement(sql)){
                pstmt.setString(1, name);

                String hashedPassword = SecurityUtils.hashPassword(password);
                pstmt.setString(2, hashedPassword);

                pstmt.setString(3, email);

                 pstmt.setString(4, public_Key);

                 pstmt.setString(5, securityHash);

                pstmt.executeUpdate();
                System.out.println("User " + name + " registrated.");
                return true;
            } catch (SQLException e) {
            System.out.println("user " + name + " already exists.");
             return false;
        }
    }
    private static String loginUser(Conn db, String name, String password) {
        String sql = "SELECT password, public_Key FROM users WHERE name = ?";

        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)){

            pstmt.setString(1, name);
            var rs = pstmt.executeQuery();

            if(rs.next()){
                String storedHash = rs.getString("password");
                String publicKey = rs.getString("public_Key");

                if (SecurityUtils.checkPassword(password, storedHash)) {
                    return publicKey; // Vracíme klíč pro ověření 2. fáze
                }
            }
        } catch(SQLException e){
            e.printStackTrace();
        }
        return null; // Chyba nebo špatné heslo
       /* String sql = "SELECT * FROM users WHERE name = ?";

        try (Connection conn = db.connect();
            PreparedStatement pstmt = conn.prepareStatement(sql)){

            pstmt.setString(1, name);
            var rs = pstmt.executeQuery(); //var abychom nemuseli pouzivat ResultSet datovy typ je to zastupce datoveho typ7u

            if(rs.next()){
                String storedHash = rs.getString("password");

                return SecurityUtils.checkPassword(password, storedHash);
            }else{
                return false;
            }

        }catch(SQLException e){
            e.printStackTrace();
            return false;
        }*/
    }
    private static void loadHistory(Conn db, BufferedWriter out, String user1, String user2) {
        // Vybereme zprávy kde (odesílatel je user1 A příjemce user2) NEBO (odesílatel je user2 A příjemce user1)
        // Seřadíme je podle času
        String sql = "SELECT sender_name, message_text FROM messages " +
                "WHERE (sender_name = ? AND receiver_name = ?) " +
                "OR (sender_name = ? AND receiver_name = ?) " +
                "ORDER BY id ASC"; // Pokud nemáš čas, řaď podle ID

        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user1);
            pstmt.setString(2, user2);
            pstmt.setString(3, user2);
            pstmt.setString(4, user1);

            var rs = pstmt.executeQuery();

            while (rs.next()) {
                String sender = rs.getString("sender_name");
                String msg = rs.getString("message_text");

                // Pošleme klientovi speciální zprávu s historií
                out.write("HIST:" + sender + ":" + msg);
                out.newLine();
            }
            out.flush();
            // Značka, že historie skončila
            out.write("HIST_END");
            out.newLine();
            out.flush();

        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean userExists(Conn db, String username){
        String sql = "SELECT * FROM users WHERE name = ?";

        try (Connection conn = db.connect();
            PreparedStatement pstmt = conn.prepareStatement(sql)){

            pstmt.setString(1, username);
            return  pstmt.executeQuery().next();
        }catch(SQLException e){
            e.printStackTrace();
            return false;
        }
    }

    private static String getPublicKey(Conn db, String username){
        String sql = "SELECT public_Key FROM users WHERE name = ?";
        try (Connection conn = db.connect()){
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, username);
            var rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("public_Key");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static boolean createRoom(Conn db, String u1, String u2, String keyForU1, String keyForU2) {

        //generate id room
        String roomId = (u1.compareTo(u2) < 0) ? u1 + "_" + u2 : u2 + "_" + u1;

        String sql = "INSERT INTO rooms (room_id, user1_hash, user2_hash, aes_key_for_u1, aes_key_for_u2) VALUES (?, ?, ?, ?, ?)";
        try(Connection conn = db.connect()){
            PreparedStatement pstmt = conn.prepareStatement(sql);
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