package org.example;

import org.mindrot.jbcrypt.BCrypt;

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
    }
}
