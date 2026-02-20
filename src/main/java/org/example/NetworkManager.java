package org.example;

import utils.SecurityUtils;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;

public class NetworkManager {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 14000;

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

    public void connect() throws IOException {
        if (socket == null || socket.isClosed()) {
            socket = new Socket(HOST, PORT);
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        }
    }

    // Zde je přenesená logika z tvého Client.java
    public boolean login(String name, String pass) {
        try {
            connect(); // Ujistíme se, že jsme připojeni

            out.write("LOG:" + name + ":" + pass);
            out.newLine(); out.flush();

            String response = in.readLine();

            if (response != null && response.startsWith("AUTH_CHALLENGE")){
                String challenge = response.split(":")[1];
                File keyFile = new File(name +"_private.key");
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
}