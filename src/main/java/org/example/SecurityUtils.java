package org.example;

import org.mindrot.jbcrypt.BCrypt;

public class SecurityUtils {
    //reg
    public static String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }
    //sign
    public static boolean checkPassword(String password, String hashedPassword) {
        return BCrypt.checkpw(password, hashedPassword);
    }
}
