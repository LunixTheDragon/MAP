package org.example;

import javax.crypto.SecretKey;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
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
                    String securityHash = SecurityUtils.createUserSecurityHash(privKeyStr, pubKeyStr);

                    out.write("REG:" + name + ":" + pass + ":" + email + ":" + pubKeyStr + ":" + securityHash );
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

                  try{
                      //priv key needed for unlocking AES key
                      File myFile = new File(myName + "_private.key");
                      String myString = java.nio.file.Files.readString(myFile.toPath());
                      PrivateKey privateKey = SecurityUtils.RSAUtils.getPrivateKeyFromString(myString);

                      //AES
                      SecretKey chatKEy = null;
                      File aesFile = new File("chat_" + myName + "_" + receiver1 + ".key" );

                      if (aesFile.exists()){
                          //we have key on disc
                          System.out.println("Načítám uložený šifrovací klíč z disku...");
                          String storedKey = java.nio.file.Files.readString(aesFile.toPath());
                          chatKEy = SecurityUtils.AESUtils.stringToKey(storedKey);
                      }else{
                          System.out.println("Nemam klic koukam na stav mistnosti");

                          //is there already chat with the one im writing to?
                          out.write("JOIN_ROOM:" + myName + ":" + receiver1);
                          out.newLine();
                          out.flush();

                          String serverResponse = in.readLine();

                          if (serverResponse.startsWith("ROOM_OK:")){
                              //Server sent key encrypted with my public key
                              String encryptedAesKey = serverResponse.split(":")[1];

                              //decrypt with privateKey
                              String decryptedAesKey = SecurityUtils.RSAUtils.decryptKey(encryptedAesKey, privateKey);
                              chatKEy = SecurityUtils.AESUtils.stringToKey(decryptedAesKey);

                              //save the file for next time
                              try(FileWriter fw = new FileWriter(aesFile)){
                                    fw.write(encryptedAesKey);
                              }
                              System.out.println("Klíč stažen ze serveru a uložen.");
                          } else if (serverResponse.equals("ROOM_MISSING")) {
                              //ROOM does not exist, it needs to be started
                              System.out.println("Místnost neexistuje. Zakládám nové zabezpečené spojení...");

                              //generate new AESKEy
                              chatKEy = SecurityUtils.AESUtils.generateAESKey();
                              String chatKeyStr = SecurityUtils.AESUtils.keyToString(chatKEy);

                              //need public key of receiver
                              out.write("GET_PUBKEY:" + receiver1);
                              out.newLine();
                              out.flush();

                              String pubKeyResp = in.readLine();
                              if (!pubKeyResp.startsWith("PUBKEY:")){
                                  System.out.println("Chyba: Uživatel " + receiver1 + " neexistuje nebo nemá klíč.");
                                  return;
                              }

                              // Získáme veřejný klíč kamaráda
                              PublicKey receiverPubKey = SecurityUtils.RSAUtils.getPublicKeyFromString(pubKeyResp.split(":")[1]);

                              // Získáme i svůj veřejný klíč (abychom si klíč mohli uložit i pro sebe na server)
                              // (Pro zjednodušení ho vytáhneme znovu z vygenerovaného páru nebo souboru, zde předpokládám reload ze souboru, pokud ho nemáš v paměti)
                              // Tady si to zjednodušíme - server už náš Public Key má v DB, ale pro šifrování ho potřebujeme tady.
                              // Načteme si ho z párů klíčů (v reálu bys ho měl mít uložený v proměnné při startu)
                              // Pro teď použijeme trik: vytvoříme klíč pro sebe tak, že ho zašifrujeme svým Public klíčem.
                              // Aby to fungovalo, musel by sis při startu načíst i svůj Public Key.
                              // ZDE PROZATÍMNÍ ŘEŠENÍ: Serveru pošleme AES klíč zašifrovaný pro NĚJ (receiver) a pro MĚ (sender).

                              // Potřebuji svůj Public Key. V kódu výše ho nemáme načtený.
                              // Přidej si do Client.java nahoru načtení i public key, nebo ho získej z privátního (nejde snadno).
                              // -> Pro tuto chvíli to uděláme tak, že si klíč uložíš jen lokálně a na server pošleš verzi pro kamaráda.
                              // (Ale správně bys měl na server poslat obě verze).

                          }
                      }
                  } catch (Exception e) {
                      throw new RuntimeException(e);
                  }
            }

        } catch (IOException e) {
            System.err.println("Chyba spojení: " + e.getMessage());
        }
    }
}