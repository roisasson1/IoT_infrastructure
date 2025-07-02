package dbms;

import java.sql.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdminDBMS implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(AdminDBMS.class);
    private final Connection connection;

    public AdminDBMS(String dbUrl, String dbUser, String dbPassword) throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            logger.info("MySQL JDBC Driver loaded successfully.");
        } catch (ClassNotFoundException e) {
            logger.error("MySQL JDBC Driver not found", e);
            throw new RuntimeException("MySQL JDBC Driver not found", e);
        }
        connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
        logger.info("Database connection established to URL: {}", dbUrl);
    }

    public void createTable(String query) throws SQLException {
        logger.debug("Executing create table query: {}", query);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(query);
            logger.info("Table creation query executed successfully.");
        }
    }

    public ResultSet executeQuery(String query) throws SQLException {
        logger.debug("Executing query: {}", query);
        Statement statement = connection.createStatement();
        return statement.executeQuery(query);
    }

    public int executeStatement(String query) throws SQLException {
        logger.debug("Executing update statement: {}", query);
        try (Statement statement = connection.createStatement()) {
            int rowsAffected = statement.executeUpdate(query);
            logger.info("Update statement executed. Rows affected: {}", rowsAffected);
            return rowsAffected;
        }
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
            logger.info("Database connection closed successfully.");
        } else {
            logger.debug("Attempted to close connection, but it was null or already closed.");
        }
    }
}