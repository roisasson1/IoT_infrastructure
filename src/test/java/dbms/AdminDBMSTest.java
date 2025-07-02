package dbms;

import java.sql.*;

public class AdminDBMSTest {
    public static void main(String[] args) {
        String dbUrl = "jdbc:mysql://localhost:3306/Companies";
        String dbUser = "roi-sasson";
        String dbPassword = System.getenv("DB_PASSWORD");

        try (AdminDBMS dbms = new AdminDBMS(dbUrl, dbUser, dbPassword)) {
            String createTableQuery =
                    "CREATE TABLE IF NOT EXISTS CP (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "name VARCHAR(100) NOT NULL UNIQUE, " +
                            "location VARCHAR(100), " +
                            "income DECIMAL(10, 2)" +
                            ")";
            dbms.createTable(createTableQuery);
            System.out.println("Table 'CP' created.");

            String insertQuery = "INSERT INTO CP (name, location, income) VALUES ('SimpleCo', 'TLV', 5000.00)";
            int rowsInserted = dbms.executeStatement(insertQuery);
            if (rowsInserted > 0) {
                System.out.println("Insert succeeded.");
            } else {
                System.out.println("Insert failed.");
            }

            ResultSet rs = dbms.executeQuery("SELECT * FROM CP WHERE name = 'SimpleCo'");
            if (rs.next()) {
                System.out.println("Data found:");
                System.out.println("Name: " + rs.getString("name"));
                System.out.println("Location: " + rs.getString("location"));
                System.out.println("Income: " + rs.getDouble("income"));
            } else {
                System.out.println("Data not found.");
            }
            rs.close();
        } catch (SQLException e) {
            System.err.println("SQL Error: " + e.getMessage());
        }
        System.out.println("Done.");
    }
}