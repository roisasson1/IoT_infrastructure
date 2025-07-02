package website_backend;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

import dbms.AdminDBMS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.mindrot.jbcrypt.BCrypt;

@WebServlet(urlPatterns = "/register", loadOnStartup = 1)
@MultipartConfig
public class RegisterCompanyServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(RegisterCompanyServlet.class);

    private static final String GATEWAY_SERVER_API_URL = "http://localhost:5006/iots";
    private static final String DB_URL = System.getenv("MYSQL_DB_URL");
    private static final String DB_USER = System.getenv("MYSQL_DB_USER");
    private static final String DB_PASSWORD = System.getenv("MYSQL_DB_PASSWORD");

    @Override
    public void init() throws ServletException {
        super.init();
        System.out.println("RegisterServlet initialized!");
        logger.info("RegisterServlet initialized!");

        if (DB_URL == null || DB_USER == null || DB_PASSWORD == null) {
            logger.error("Database environment variables (MYSQL_DB_URL, MYSQL_DB_USER, MYSQL_DB_PASSWORD) are not set.");
            throw new ServletException("Database configuration missing. Cannot initialize RegisterCompanyServlet.");
        } else {
            logger.info("Database configuration loaded from environment variables.");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        System.out.println("GET /register called. Forwarding to register_company.html.");
        logger.debug("GET /register called. Forwarding to register_company.html.");
        request.getRequestDispatcher("/register_company.html").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Map<String, String> params = extractParameters(request);
        System.out.println("POST /register called. Processing direct DB registration.");
        logger.info("POST /register called. Processing direct DB registration.");
        logger.debug("Received parameters: company_name={}, password=(masked), card_number=(masked), exp_date={}, cvc=(masked), contact_name={}, email={}, phone={}, linkedin={}",
                params.get("company_name"), params.get("exp_date"), params.get("contact_name"), params.get("email"), params.get("phone"), params.get("linkedin"));


        if (!validateParameters(params, response)) {
            logger.warn("Parameter validation failed for registration request.");
            return;
        }

        LocalDate expDate;
        try {
            expDate = parseExpirationDate(params.get("exp_date"));
            logger.debug("Parsed expiration date: {}", expDate);
        } catch (DateTimeParseException e) {
            logger.warn("Invalid expiration date format received: {}. Error: {}", params.get("exp_date"), e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid expiration date format. Use MM/YY.");
            return;
        }

        String responseMessage;
        int httpStatus;
        int compId = -1;
        String userEmail = params.get("email");
        String companyName = params.get("company_name");
        String userIdFromDb = null;

        try {
            Map<String, Object> registrationResults = registerCompanyInDatabase(params, expDate);
            compId = (int) registrationResults.get("compId");
            userIdFromDb = String.valueOf(registrationResults.get("userId"));

            responseMessage = "Registration successful! Company ID: " + compId;
            httpStatus = HttpServletResponse.SC_OK;
            logger.info("Company '{}' registered in database with ID: {}", params.get("company_name"), compId);
        } catch (SQLException e) {
            System.err.println("Database error during registration for " + params.get("company_name") + ": " + e.getMessage());
            logger.error("Database error during registration for {}: {}", params.get("company_name"), e.getMessage(), e);
            if (e.getMessage() != null && (e.getMessage().contains("Email already registered") || e.getMessage().contains("Company name already registered"))) {
                responseMessage = "Registration failed: " + e.getMessage();
                httpStatus = HttpServletResponse.SC_CONFLICT;
            } else {
                responseMessage = "Registration failed: Database error - " + e.getMessage();
                httpStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            }
        } catch (Exception e) {
            System.err.println("Unexpected error in RegisterServlet during registration: " + e.getMessage());
            logger.error("Unexpected error in RegisterServlet during registration: {}", e.getMessage(), e);
            responseMessage = "Registration failed: Unexpected server error - " + e.getMessage();
            httpStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }

        if (httpStatus == HttpServletResponse.SC_OK && compId != -1) {
            sendRegistrationToGateway(compId, companyName);

            String jwtToken = AuthService.generateJwtToken(userEmail, userIdFromDb, compId, companyName);
            AuthService.setAuthCookie(response, jwtToken, request);
            AuthService.setCompanyDisplayCookie(response, companyName);

            System.out.println("Registration successful. Redirecting to /company/" + compId);
            logger.info("Registration successful for user '{}'. Redirecting to /company/{}", userEmail, compId);
            response.sendRedirect(request.getContextPath() + "/company/" + compId);
            return;
        }

        response.setContentType("text/plain;charset=UTF-8");
        response.setStatus(httpStatus);
        response.getWriter().write(responseMessage);
        logger.info("Sent response to client - Status: {}, Message: {}", httpStatus, responseMessage);
    }

    private Map<String, String> extractParameters(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        params.put("company_name", request.getParameter("company_name"));
        params.put("password", request.getParameter("password"));
        params.put("card_number", request.getParameter("card_number"));
        params.put("exp_date", request.getParameter("exp_date"));
        params.put("cvc", request.getParameter("cvc"));
        params.put("contact_name", request.getParameter("contact_name"));
        params.put("email", request.getParameter("email"));
        params.put("phone", request.getParameter("phone"));
        params.put("linkedin", request.getParameter("linkedin"));
        logger.debug("Extracted parameters from request.");
        return params;
    }

    private boolean validateParameters(Map<String, String> params, HttpServletResponse response) throws IOException {
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() == null || entry.getValue().trim().isEmpty()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing or invalid required parameters. Please check all fields.");
                logger.warn("Missing or empty parameter: '{}'", entry.getKey());
                return false;
            }
        }

        String email = params.get("email");
        if (email == null || !email.contains("@") || !email.contains(".")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid email format.");
            logger.warn("Invalid email format received: '{}'", email);
            return false;
        }
        logger.debug("All parameters validated successfully.");
        return true;
    }

    private LocalDate parseExpirationDate(String expDateStr) {
        LocalDate expDate = LocalDate.parse("01/" + expDateStr, DateTimeFormatter.ofPattern("dd/MM/yy"));
        return expDate.withDayOfMonth(expDate.lengthOfMonth());
    }

    private Map<String, Object> registerCompanyInDatabase(Map<String, String> params, LocalDate expDate) throws SQLException {
        int compId = -1;
        int userId = -1;
        logger.info("Attempting to register company '{}' in database.", params.get("company_name"));

        String plainPassword = params.get("password");
        String hashedPassword = BCrypt.hashpw(plainPassword, BCrypt.gensalt());
        logger.debug("Hashed password for company '{}'", params.get("company_name"));

        try (AdminDBMS dbms = new AdminDBMS(DB_URL, DB_USER, DB_PASSWORD)) {

            String checkEmailQuery = String.format("SELECT email FROM Contacts WHERE email = '%s'", params.get("email"));
            try (ResultSet rs = dbms.executeQuery(checkEmailQuery)) {
                if (rs.next()) {
                    logger.warn("Registration failed: Email '{}' already exists.", params.get("email"));
                    throw new SQLException("Email already registered.");
                }
            }

            String checkCompanyNameQuery = String.format("SELECT name FROM Companies WHERE name = '%s'", params.get("company_name"));
            try (ResultSet rs = dbms.executeQuery(checkCompanyNameQuery)) {
                if (rs.next()) {
                    logger.warn("Registration failed: Company name '{}' already exists.", params.get("company_name"));
                    throw new SQLException("Company name already registered.");
                }
            }

            String insertCompanyQuery = String.format(
                    "INSERT INTO Companies (name, password, cc_number, cc_expiration_date, cc_charge_date, cvc) VALUES ('%s', '%s', '%s', '%s', CURDATE(), '%s')",
                    params.get("company_name"), plainPassword, params.get("card_number"), expDate.format(DateTimeFormatter.ISO_LOCAL_DATE), params.get("cvc")
            );
            logger.debug("Executing company insert query: {}", insertCompanyQuery);

            int companyRowsInserted = dbms.executeStatement(insertCompanyQuery);
            if (companyRowsInserted == 0) {
                logger.error("Failed to insert company data for {}. No rows affected.", params.get("company_name"));
                throw new SQLException("Failed to insert company data. No rows affected.");
            }
            logger.debug("Company rows inserted: {}", companyRowsInserted);

            try (ResultSet rs = dbms.executeQuery("SELECT LAST_INSERT_ID()")) {
                if (rs.next()) {
                    compId = rs.getInt(1);
                    logger.debug("Retrieved generated company ID: {}", compId);
                }
            }
            if (compId == -1) {
                logger.error("Failed to retrieve generated company ID after insert for {}.", params.get("company_name"));
                throw new SQLException("Failed to retrieve generated company ID after insert.");
            }

            String insertContactQuery = String.format(
                    "INSERT INTO Contacts (comp_id, name, email, phone_number, linkedin_account) VALUES (%d, '%s', '%s', '%s', '%s')",
                    compId, params.get("contact_name"), params.get("email"), params.get("phone"), params.get("linkedin")
            );
            logger.debug("Executing contact insert query: {}", insertContactQuery);

            int contactRowsInserted = dbms.executeStatement(insertContactQuery);
            if (contactRowsInserted == 0) {
                logger.error("Failed to insert contact data for company ID {}. No rows affected.", compId);
                throw new SQLException("Failed to insert contact data. No rows affected.");
            }
            logger.debug("Contact rows inserted: {}", contactRowsInserted);

            String insertUserQuery = String.format(
                    "INSERT INTO Users (email, password_hash, comp_id) VALUES ('%s', '%s', %d)",
                    params.get("email"), hashedPassword, compId
            );
            logger.debug("Executing user insert query: {}", insertUserQuery);
            int userRowsInserted = dbms.executeStatement(insertUserQuery);

            if (userRowsInserted == 0) {
                logger.error("Failed to insert user data for email {}. No rows affected.", params.get("email"));
                throw new SQLException("Failed to create user login credentials.");
            }
            logger.debug("User rows inserted: {}", userRowsInserted);

            try (ResultSet rs = dbms.executeQuery("SELECT LAST_INSERT_ID()")) {
                if (rs.next()) {
                    userId = rs.getInt(1);
                    logger.debug("Retrieved generated user ID: {}", userId);
                }
            }
            if (userId == -1) {
                logger.error("Failed to retrieve generated user ID after insert for {}.", params.get("email"));
                throw new SQLException("Failed to retrieve generated user ID after insert.");
            }
        }

        Map<String, Object> results = new HashMap<>();
        results.put("compId", compId);
        results.put("userId", userId);
        return results;
    }

    private void sendRegistrationToGateway(int compId, String companyName) {
        HttpClient client = HttpClient.newHttpClient();

        String jsonPayload = String.format(
                "{\"command\": \"Register Company\", \"data\": {\"company_id\": %d, \"company_name\": \"%s\"}}",
                compId, companyName
        );

        System.out.println("Sending to 5006: " + GATEWAY_SERVER_API_URL + " with payload: " + jsonPayload);
        logger.info("Sending company registration to Gateway server {}: Payload: {}", GATEWAY_SERVER_API_URL, jsonPayload);

        HttpRequest requestToSecondServer = HttpRequest.newBuilder()
                .uri(URI.create(GATEWAY_SERVER_API_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        try {
            HttpResponse<String> secondServerResponse = client.send(requestToSecondServer, HttpResponse.BodyHandlers.ofString());
            System.out.println("Response from 5006 server - Status: " + secondServerResponse.statusCode() + ", Body: " + secondServerResponse.body());
            logger.info("Response from Gateway server - Status: {}, Body: {}", secondServerResponse.statusCode(), secondServerResponse.body());

            if (secondServerResponse.statusCode() != HttpServletResponse.SC_OK &&
                    secondServerResponse.statusCode() != HttpServletResponse.SC_CREATED) {
                System.err.println("Second server (5006) returned non-OK/Created status: " + secondServerResponse.statusCode() + " - " + secondServerResponse.body());
                logger.error("Gateway server returned non-success status: {} - {}", secondServerResponse.statusCode(), secondServerResponse.body());
            } else {
                logger.info("Company registration successfully sent to Gateway server.");
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to send data to 5006 server: " + e.getMessage());
            logger.error("Failed to send data to Gateway server: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        System.out.println("RegisterServlet destroyed.");
        logger.info("RegisterServlet destroyed.");
    }
}