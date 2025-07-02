package website_backend;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import dbms.AdminDBMS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet(urlPatterns = "/display_contacts", loadOnStartup = 1)
public class DisplayContactsServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(DisplayContactsServlet.class);

    private final Gson gson = new Gson();
    private static final String DB_URL = System.getenv("MYSQL_DB_URL");
    private static final String DB_USER = System.getenv("MYSQL_DB_USER");
    private static final String DB_PASSWORD = System.getenv("MYSQL_DB_PASSWORD");

    @Override
    public void init() throws ServletException {
        super.init();
        System.out.println("DisplayContactsServlet initialized!");
        logger.info("DisplayContactsServlet initialized!");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        System.out.println("GET /display_contacts called.");
        logger.debug("GET /display_contacts called.");

        int compId;
        String compIdStr = request.getParameter("comp_id");
        try {
            compId = parseCompanyId(compIdStr);
            logger.debug("Parsed company ID: {}", compId);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request for /display_contacts: {}", e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Error: " + e.getMessage());
            return;
        }

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        List<Contact> contacts;

        try {
            contacts = fetchContactsFromDatabase(compId);
            String jsonResponse = gson.toJson(contacts);
            out.print(jsonResponse);
            System.out.println("Sent contacts JSON: " + jsonResponse);
            logger.info("Successfully sent contacts JSON for company ID {}: {}", compId, jsonResponse);
        } catch (SQLException e) {
            System.err.println("Database error fetching contacts for company " + compId + ": " + e.getMessage());
            logger.error("Database error fetching contacts for company {}: {}", compId, e.getMessage(), e);
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "{\"error\": \"Database error: " + e.getMessage() + "\"}");
        } catch (Exception e) {
            System.err.println("Unexpected error in DisplayContactsServlet: " + e.getMessage());
            logger.error("Unexpected error in DisplayContactsServlet: {}", e.getMessage(), e);
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "{\"error\": \"Unexpected server error: " + e.getMessage() + "\"}");
        } finally {
            out.flush();
        }
    }

    private int parseCompanyId(String compIdStr) {
        if (compIdStr == null || compIdStr.trim().isEmpty()) {
            logger.warn("Company ID is missing from request parameters.");
            throw new IllegalArgumentException("Company ID is missing.");
        }

        int compId;
        try {
            compId = Integer.parseInt(compIdStr);
            if (compId <= 0) {
                logger.warn("Invalid Company ID. Must be a positive number: {}", compId);
                throw new IllegalArgumentException("Invalid Company ID. Must be a positive number.");
            }
        } catch (NumberFormatException e) {
            logger.warn("Invalid Company ID format received: {}. Error: {}", compIdStr, e.getMessage());
            throw new IllegalArgumentException("Invalid Company ID format.");
        }
        return compId;
    }

    private List<Contact> fetchContactsFromDatabase(int compId) throws SQLException {
        List<Contact> contacts = new ArrayList<>();
        logger.info("Fetching contacts from database for company ID: {}", compId);
        try (AdminDBMS dbms = new AdminDBMS(DB_URL, DB_USER, DB_PASSWORD)) {
            String selectContactsQuery = String.format(
                    "SELECT name, email, phone_number, linkedin_account FROM Contacts WHERE comp_id = %d", compId
            );
            System.out.println("Executing query: " + selectContactsQuery);
            logger.debug("Executing query: {}", selectContactsQuery);

            try (ResultSet rs = dbms.executeQuery(selectContactsQuery)) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    String email = rs.getString("email");
                    String phoneNumber = rs.getString("phone_number");
                    String linkedinAccount = rs.getString("linkedin_account");
                    contacts.add(new Contact(name, email, phoneNumber, linkedinAccount));
                    logger.trace("Found contact: Name='{}', Email='{}'", name, email);
                }
            }
            logger.info("Found {} contacts for company ID {}.", contacts.size(), compId);
        }
        return contacts;
    }

    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.getWriter().write(message);
        logger.debug("Sent error response - Status: {}, Message: {}", status, message);
    }

    @Override
    public void destroy() {
        super.destroy();
        System.out.println("DisplayContactsServlet destroyed.");
        logger.info("DisplayContactsServlet destroyed.");
    }

    private static class Contact {
        String name;
        String email;
        String phone_number;
        String linkedin_account;

        public Contact(String name, String email, String phone_number, String linkedin_account) {
            this.name = name;
            this.email = email;
            this.phone_number = phone_number;
            this.linkedin_account = linkedin_account;
        }
    }
}