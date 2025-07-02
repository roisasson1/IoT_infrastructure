package website_backend;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.annotation.MultipartConfig;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import dbms.AdminDBMS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet(urlPatterns = "/add_contact", loadOnStartup = 1)
@MultipartConfig
public class AddContactServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(AddContactServlet.class);

    private static final String DB_URL = System.getenv("MYSQL_DB_URL");
    private static final String DB_USER = System.getenv("MYSQL_DB_USER");
    private static final String DB_PASSWORD = System.getenv("MYSQL_DB_PASSWORD");

    @Override
    public void init() throws ServletException {
        super.init();
        System.out.println("AddContactServlet initialized!");
        logger.info("AddContactServlet initialized!");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        System.out.println("GET /add_contact called. Forwarding to add_contact.html.");
        logger.debug("GET /add_contact called. Forwarding to add_contact.html.");

        String pathInfo = request.getPathInfo();
        String companyIdParam = null;
        if (pathInfo != null && pathInfo.length() > 1) {
            companyIdParam = pathInfo.substring(1);
            if (!companyIdParam.matches("\\d+")) {
                companyIdParam = null;
                logger.warn("Invalid company ID format in path info: {}", pathInfo);
            }
        }
        // companyIdParam is not used further in doGet, but logging its extraction is useful.
        logger.debug("Extracted companyIdParam from pathInfo (if any): {}", companyIdParam);


        request.getRequestDispatcher("/add_contact.html").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Map<String, String> params = extractParameters(request);

        System.out.println("POST /add_contact called. Processing contact addition.");
        logger.info("POST /add_contact called. Processing contact addition.");
        System.out.println("Contact Name: " + params.get("contact_name") + ", Email: " + params.get("email") +
                ", Phone: " + params.get("phone") + ", LinkedIn: " + params.get("linkedin") +
                ", Company ID: " + params.get("comp_id"));
        logger.debug("Received parameters: contact_name={}, email={}, phone={}, linkedin={}, comp_id={}",
                params.get("contact_name"), params.get("email"), params.get("phone"), params.get("linkedin"), params.get("comp_id"));

        if (!validateParameters(params, response)) {
            logger.warn("Parameter validation failed for contact addition request.");
            return;
        }

        int compId;
        try {
            compId = parseCompanyId(params.get("comp_id"), response);
            if (compId == -1) {
                logger.warn("Company ID parsing failed. Sending error response.");
                return;
            }
            logger.debug("Parsed company ID: {}", compId);
        } catch (NumberFormatException e) {
            logger.warn("Invalid Company ID format received: {}. Error: {}", params.get("comp_id"), e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid Company ID format.");
            return;
        }

        String responseMessage;
        int httpStatus;

        try {
            addContactToDatabase(params, compId);
            responseMessage = "Contact '" + params.get("contact_name") + "' added successfully for Company ID: " + compId;
            httpStatus = HttpServletResponse.SC_OK;
            logger.info("Contact '{}' added successfully for company ID {}.", params.get("contact_name"), compId);
        } catch (SQLException e) {
            System.err.println("Database error during contact addition for " + params.get("contact_name") + ": " + e.getMessage());
            logger.error("Database error during contact addition for {}: {}", params.get("contact_name"), e.getMessage(), e);
            responseMessage = "Failed to add contact: Database error - " + e.getMessage();
            httpStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        } catch (Exception e) {
            System.err.println("Unexpected error in AddContactServlet during addition: " + e.getMessage());
            logger.error("Unexpected error in AddContactServlet during addition: {}", e.getMessage(), e);
            responseMessage = "Failed to add contact: Unexpected server error - " + e.getMessage();
            httpStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }

        response.setContentType("text/plain;charset=UTF-8");
        response.setStatus(httpStatus);
        response.getWriter().write(responseMessage);
        logger.info("Sent response to client - Status: {}, Message: {}", httpStatus, responseMessage);
    }

    private Map<String, String> extractParameters(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        params.put("contact_name", request.getParameter("contact_name"));
        params.put("email", request.getParameter("email"));
        params.put("phone", request.getParameter("phone"));
        params.put("linkedin", request.getParameter("linkedin"));
        params.put("comp_id", request.getParameter("comp_id"));
        logger.debug("Extracted parameters from request.");
        return params;
    }

    private boolean validateParameters(Map<String, String> params, HttpServletResponse response) throws IOException {
        if (params.get("contact_name") == null || params.get("contact_name").trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing or empty required parameters for contact addition (contact_name).");
            logger.warn("Validation failed: contact_name is missing or empty.");
            return false;
        }
        if (params.get("email") == null || params.get("email").trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing or empty required parameters for contact addition (email).");
            logger.warn("Validation failed: email is missing or empty.");
            return false;
        }
        if (params.get("phone") == null || params.get("phone").trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing or empty required parameters for contact addition (phone).");
            logger.warn("Validation failed: phone is missing or empty.");
            return false;
        }
        if (params.get("linkedin") == null || params.get("linkedin").trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing or empty required parameters for contact addition (linkedin).");
            logger.warn("Validation failed: linkedin is missing or empty.");
            return false;
        }
        if (params.get("comp_id") == null || params.get("comp_id").trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing or empty required parameters for contact addition (comp_id).");
            logger.warn("Validation failed: comp_id is missing or empty.");
            return false;
        }
        logger.debug("All parameters validated successfully.");
        return true;
    }

    private int parseCompanyId(String compIdStr, HttpServletResponse response) throws IOException {
        int compId;
        try {
            compId = Integer.parseInt(compIdStr);
            if (compId <= 0) {
                logger.warn("Invalid Company ID: Must be a positive number. Received: {}", compId);
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid Company ID: Must be a positive number.");
                return -1;
            }
        } catch (NumberFormatException e) {
            logger.warn("Invalid Company ID format: {}. Error: {}", compIdStr, e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid Company ID format.");
            return -1;
        }
        return compId;
    }

    private void addContactToDatabase(Map<String, String> params, int compId) throws SQLException {
        logger.info("Attempting to add contact '{}' to database for company ID {}.", params.get("contact_name"), compId);
        try (AdminDBMS dbms = new AdminDBMS(DB_URL, DB_USER, DB_PASSWORD)) {
            try (ResultSet rs = dbms.executeQuery(String.format("SELECT COUNT(*) FROM Companies WHERE comp_id = %d", compId))) {
                if (rs.next() && rs.getInt(1) == 0) {
                    logger.error("Company with ID {} does not exist. Cannot add contact.", compId);
                    throw new SQLException("Company with ID " + compId + " does not exist.");
                }
                logger.debug("Company ID {} confirmed to exist in database.", compId);
            }

            String insertContactQuery = String.format(
                    "INSERT INTO Contacts (comp_id, name, email, phone_number, linkedin_account) VALUES (%d, '%s', '%s', '%s', '%s')",
                    compId, params.get("contact_name"), params.get("email"), params.get("phone"), params.get("linkedin")
            );
            logger.debug("Executing insert contact query: {}", insertContactQuery);
            int rowsInserted = dbms.executeStatement(insertContactQuery);

            if (rowsInserted == 0) {
                logger.error("Failed to add contact '{}' for company ID {}. No rows affected.", params.get("contact_name"), compId);
                throw new SQLException("Failed to add contact. No rows affected.");
            }
            logger.debug("Contact rows inserted: {}", rowsInserted);
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        System.out.println("AddContactServlet destroyed.");
        logger.info("AddContactServlet destroyed.");
    }
}