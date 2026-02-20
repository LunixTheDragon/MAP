package utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Conn {
    // DŮLEŽITÉ: Musí tam být "jdbc:mariadb://"
    String url = "jdbc:mariadb://ita03.vas-server.cz:3306/vlcek_MAP";

    private String username = "vlcek";
    private String password = "ElJam8rzcqykyiCR";

    public Connection connect() {
        try {
            // Zde musí být malá písmena proměnných
            return DriverManager.getConnection(url, username, password);
        } catch (SQLException e) {
            System.err.println("Chyba při připojování k DB: " + e.getMessage());
            return null;
        }
    }
}