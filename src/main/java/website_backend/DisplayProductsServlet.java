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

@WebServlet(urlPatterns = "/display_products", loadOnStartup = 1)
public class DisplayProductsServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(DisplayProductsServlet.class);

    private final Gson gson = new Gson();
    private static final String DB_URL = System.getenv("MYSQL_DB_URL");
    private static final String DB_USER = System.getenv("MYSQL_DB_USER");
    private static final String DB_PASSWORD = System.getenv("MYSQL_DB_PASSWORD");

    @Override
    public void init() throws ServletException {
        super.init();
        System.out.println("DisplayProductsServlet initialized!");
        logger.info("DisplayProductsServlet initialized!");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        System.out.println("GET /display_products called.");
        logger.debug("GET /display_products called.");

        int compId;
        String compIdStr = request.getParameter("comp_id");
        try {
            compId = parseCompanyId(compIdStr);
            logger.debug("Parsed company ID: {}", compId);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request for /display_products: {}", e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Error: " + e.getMessage());
            return;
        }

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        List<Product> products;

        try {
            products = fetchProductsFromDatabase(compId);
            String jsonResponse = gson.toJson(products);
            out.print(jsonResponse);
            System.out.println("Sent products JSON: " + jsonResponse);
            logger.info("Successfully sent products JSON for company ID {}: {}", compId, jsonResponse);
        } catch (SQLException e) {
            System.err.println("Database error fetching products for company " + compId + ": " + e.getMessage());
            logger.error("Database error fetching products for company {}: {}", compId, e.getMessage(), e);
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "{\"error\": \"Database error: " + e.getMessage() + "\"}");
        } catch (Exception e) {
            System.err.println("Unexpected error in DisplayProductsServlet: " + e.getMessage());
            logger.error("Unexpected error in DisplayProductsServlet: {}", e.getMessage(), e);
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

    private List<Product> fetchProductsFromDatabase(int compId) throws SQLException {
        List<Product> products = new ArrayList<>();
        logger.info("Fetching products from database for company ID: {}", compId);
        try (AdminDBMS dbms = new AdminDBMS(DB_URL, DB_USER, DB_PASSWORD)) {
            String selectProductsQuery = String.format(
                    "SELECT name, version FROM Products WHERE comp_id = %d", compId
            );
            System.out.println("Executing query: " + selectProductsQuery);
            logger.debug("Executing query: {}", selectProductsQuery);

            try (ResultSet rs = dbms.executeQuery(selectProductsQuery)) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    String version = rs.getString("version");
                    products.add(new Product(name, version));
                    logger.trace("Found product: Name='{}', Version='{}'", name, version);
                }
            }
            logger.info("Found {} products for company ID {}.", products.size(), compId);
        }
        return products;
    }

    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.getWriter().write(message);
        logger.debug("Sent error response - Status: {}, Message: {}", status, message);
    }

    @Override
    public void destroy() {
        super.destroy();
        System.out.println("DisplayProductsServlet destroyed.");
        logger.info("DisplayProductsServlet destroyed.");
    }

    private static class Product {
        String name;
        String version;

        public Product(String name, String version) {
            this.name = name;
            this.version = version;
        }
    }
}