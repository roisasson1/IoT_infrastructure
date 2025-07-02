package website_backend;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.annotation.MultipartConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import dbms.AdminDBMS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet(urlPatterns = "/register_product", loadOnStartup = 1)
@MultipartConfig
public class RegisterProductServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(RegisterProductServlet.class);

    private static final String GATEWAY_SERVER_API_URL = "http://localhost:5006/iots";
    private static final String DB_URL = System.getenv("MYSQL_DB_URL");
    private static final String DB_USER = System.getenv("MYSQL_DB_USER");
    private static final String DB_PASSWORD = System.getenv("MYSQL_DB_PASSWORD");

    private static final String COMPANY_NAME_COOKIE_KEY = "companyName";

    @Override
    public void init() throws ServletException {
        super.init();
        System.out.println("RegisterProductServlet initialized!");
        logger.info("RegisterProductServlet initialized!");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        System.out.println("GET /register_product called. Forwarding to register_product.html.");
        logger.debug("GET /register_product called. Forwarding to register_product.html.");
        request.getRequestDispatcher("/register_product.html").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Map<String, String> params = extractParameters(request);
        System.out.println("POST /register_product called. Processing product registration.");
        logger.info("POST /register_product called. Processing product registration.");
        System.out.println("Product Name: " + params.get("product_name") + ", Version: " + params.get("version") + ", Company ID String: " + params.get("comp_id"));
        logger.debug("Received parameters: product_name={}, version={}, comp_id={}",
                params.get("product_name"), params.get("version"), params.get("comp_id"));


        if (!validateParameters(params, response)) {
            logger.warn("Parameter validation failed for product registration request.");
            return;
        }

        int compId;
        try {
            compId = Integer.parseInt(params.get("comp_id"));
            logger.debug("Parsed company ID: {}", compId);
        } catch (NumberFormatException e) {
            logger.warn("Invalid Company ID format received: {}. Error: {}", params.get("comp_id"), e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid Company ID format.");
            return;
        }

        String companyName = getCookieValue(request);
        if (companyName == null || companyName.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Company name not found in cookies. Please login first.");
            System.err.println("Error: Company name missing from cookies for RegisterProduct.");
            logger.error("Company name missing from cookies for RegisterProductServlet. User unauthorized.");
            return;
        }
        System.out.println("Company Name from cookie: " + companyName + ", Company ID from cookie: " + compId);
        logger.debug("Company Name from cookie: {}, Company ID from cookie: {}", companyName, compId);

        String responseMessage;
        int httpStatus;

        try {
            registerProductInDatabase(params, compId);
            responseMessage = "Product '" + params.get("product_name") + "' registered";
            httpStatus = HttpServletResponse.SC_OK;
            logger.info("Product '{}' (v{}) registered successfully in database for company ID {}.",
                    params.get("product_name"), params.get("version"), compId);
        } catch (SQLException e) {
            System.err.println("Database error during product registration for " + params.get("product_name") + ": " + e.getMessage());
            logger.error("Database error during product registration for {}: {}", params.get("product_name"), e.getMessage(), e);
            responseMessage = "Product registration failed: Database error - " + e.getMessage();
            httpStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        } catch (Exception e) {
            System.err.println("Unexpected error in RegisterProductServlet during registration: " + e.getMessage());
            logger.error("Unexpected error in RegisterProductServlet during registration: {}", e.getMessage(), e);
            responseMessage = "Product registration failed: Unexpected server error - " + e.getMessage();
            httpStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }

        if (httpStatus == HttpServletResponse.SC_OK) {
            sendProductRegistrationToGateway(compId, companyName, params.get("product_name"), params.get("version"));
        }

        response.setContentType("text/plain;charset=UTF-8");
        response.setStatus(httpStatus);
        response.getWriter().write(responseMessage);
        logger.info("Sent response to client - Status: {}, Message: {}", httpStatus, responseMessage);
    }

    private Map<String, String> extractParameters(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        params.put("product_name", request.getParameter("product_name"));
        params.put("version", request.getParameter("version"));
        params.put("comp_id", request.getParameter("comp_id"));
        logger.debug("Extracted parameters from request.");
        return params;
    }

    private boolean validateParameters(Map<String, String> params, HttpServletResponse response) throws IOException {
        if (params.get("product_name") == null || params.get("product_name").trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing or empty required parameters for product registration. Please check all fields (product_name).");
            logger.warn("Validation failed: product_name is missing or empty.");
            return false;
        }
        if (params.get("version") == null || params.get("version").trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing or empty required parameters for product registration. Please check all fields (version).");
            logger.warn("Validation failed: version is missing or empty.");
            return false;
        }
        if (params.get("comp_id") == null || params.get("comp_id").trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing or empty required parameters for product registration. Please check all fields (comp_id).");
            logger.warn("Validation failed: comp_id is missing or empty.");
            return false;
        }
        logger.debug("All parameters validated successfully.");
        return true;
    }

    private void registerProductInDatabase(Map<String, String> params, int compId) throws SQLException {
        logger.info("Attempting to register product '{}' (v{}) in database for company ID {}.",
                params.get("product_name"), params.get("version"), compId);
        try (AdminDBMS dbms = new AdminDBMS(DB_URL, DB_USER, DB_PASSWORD)) {
            try (ResultSet rs = dbms.executeQuery(String.format("SELECT COUNT(*) FROM Companies WHERE comp_id = %d", compId))) {
                if (rs.next() && rs.getInt(1) == 0) {
                    logger.error("Company with ID {} does not exist in the database.", compId);
                    throw new SQLException("Company with ID " + compId + " does not exist.");
                }
                logger.debug("Company ID {} confirmed to exist in database.", compId);
            }

            String insertProductQuery = String.format(
                    "INSERT INTO Products (comp_id, name, version) VALUES (%d, '%s', '%s')",
                    compId, params.get("product_name"), params.get("version")
            );
            logger.debug("Executing product insert query: {}", insertProductQuery);
            int rowsInserted = dbms.executeStatement(insertProductQuery);

            if (rowsInserted == 0) {
                logger.error("Failed to register product '{}' (v{}) for company ID {}. No rows affected.",
                        params.get("product_name"), params.get("version"), compId);
                throw new SQLException("Failed to register product. No rows affected.");
            }
            logger.debug("Product rows inserted: {}", rowsInserted);
        }
    }

    private void sendProductRegistrationToGateway(int companyId, String companyName, String productName, String version) {
        HttpClient client = HttpClient.newHttpClient();

        String jsonPayload = String.format(
                "{\"command\": \"Register Product\", \"data\": {\"company_id\": \"%s\", \"company_name\": \"%s\", \"product_name\": \"%s\", \"product_version\": \"%s\"}}",
                companyId, companyName, productName, version
        );

        System.out.println("Sending to 5006: " + GATEWAY_SERVER_API_URL + " with payload: " + jsonPayload);
        logger.info("Sending product registration to Gateway server {}: Payload: {}", GATEWAY_SERVER_API_URL, jsonPayload);

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
                logger.info("Product registration successfully sent to Gateway server.");
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to send data to 5006 server: " + e.getMessage());
            logger.error("Failed to send data to Gateway server: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }

    private String getCookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COMPANY_NAME_COOKIE_KEY.equals(cookie.getName())) {
                    logger.debug("Found cookie '{}' with value '{}'", RegisterProductServlet.COMPANY_NAME_COOKIE_KEY, cookie.getValue());
                    return cookie.getValue();
                }
            }
        }
        logger.debug("Cookie '{}' not found.", RegisterProductServlet.COMPANY_NAME_COOKIE_KEY);
        return null;
    }

    @Override
    public void destroy() {
        super.destroy();
        System.out.println("RegisterProductServlet destroyed.");
        logger.info("RegisterProductServlet destroyed.");
    }
}