package org.example;

import org.mindrot.jbcrypt.BCrypt;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class SecurityUtils {
    //reg
    public static String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }
    //sign
    public static boolean checkPassword(String password, String hashedPassword) {
        return BCrypt.checkpw(password, hashedPassword);
    }
    public static String createUserSecurityHash(String privKeyStr, String pubKeyStr) throws Exception {
        //connected hashes
        String combined = privKeyStr + pubKeyStr;

        //hash combined
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));

        return Base64.getEncoder().encodeToString(hash);

    }


    public class RSAUtils{
        //generating a pair of keys
        public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        }

        //retype key to string for sending
        public static String keyToString(Key key){
            return Base64.getEncoder().encodeToString(key.getEncoded());
        }

        //String to PUblic key (server method)
        public static PublicKey getPublicKeyFromString(String key64) throws Exception{
            byte[] data = Base64.getDecoder().decode(key64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(data);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return factory.generatePublic(spec);
        }

        //Signature (Client)
        public static String sign(String data, PrivateKey privateKey) throws Exception{
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(data.getBytes());
            return Base64.getEncoder().encodeToString(signature.sign());
        }

        //Check (server)
        public static boolean verify(String data, String signature64, PublicKey publicKey) throws Exception{
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(data.getBytes());
            return signature.verify(Base64.getDecoder().decode(signature64));
        }
        public static PrivateKey getPrivateKeyFromString(String key64) throws Exception {
            byte[] data = Base64.getDecoder().decode(key64);
            // Privátní klíče v Javě používají standard PKCS8
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(data);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return factory.generatePrivate(spec);
        }

        //AES key method (rooms)
        public static String encryptKey(String aesKeyStr, PublicKey publicKey) throws Exception {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(Base64.getDecoder().decode(aesKeyStr));
            return Base64.getEncoder().encodeToString(encrypted);
        }

        //RSA decrypt for accepting key
        public static String decryptKey(String encryptedaesKeyStr, PrivateKey privateKey) throws Exception {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedaesKeyStr));
            return Base64.getEncoder().encodeToString(decrypted);
        }
    }

    //encrypting messages logic
    public static class AESUtils{

        //random key generator for AES
        public static SecretKey generateAESKey() throws Exception {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(128);
            return keyGenerator.generateKey();
        }

        //string to SecretKey
        public static SecretKey stringToKey(String keyStr)  {
            byte[] decoded = Base64.getDecoder().decode(keyStr);
            return new SecretKeySpec(decoded, 0, decoded.length,  "AES");
        }

        //secrekey to string
        public static String keyToString(SecretKey secretKey){
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        }

        //encrypt message
        public static String encrypt(String tex, SecretKey secretKey) throws Exception {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(tex.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        }

        //decrypt message
        public static String decrypt(String cryptedText, SecretKey secretKey) throws Exception {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(cryptedText));
            return new String(decrypted, StandardCharsets.UTF_8);
        }
    }
}
