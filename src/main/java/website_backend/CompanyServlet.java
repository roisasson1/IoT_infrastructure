package website_backend;

import dbms.AdminDBMS;
import io.jsonwebtoken.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet(urlPatterns = "/company/*")
@MultipartConfig
public class CompanyServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(CompanyServlet.class);
    private static final String COMPANY_PAGE_HTML = "/company_page.html";
    private static final String REGISTER_PRODUCT_HTML = "/register_product.html";
    private static final String ADD_CONTACT_HTML = "/add_contact.html";
    private static final String DISPLAY_PRODUCTS_HTML = "/display_products.html";
    private static final String DISPLAY_CONTACTS_HTML = "/display_contacts.html";
    private static final String INDEX_PAGE = "/index.html";

    private static final String DB_URL = System.getenv("MYSQL_DB_URL");
    private static final String DB_USER = System.getenv("MYSQL_DB_USER");
    private static final String DB_PASSWORD = System.getenv("MYSQL_DB_PASSWORD");

    @Override
    public void init() throws ServletException {
        super.init();
        System.out.println("CompanyServlet initialized!");
        logger.info("CompanyServlet initialized!");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String requestURI = request.getRequestURI();
        String contextPath = request.getContextPath();
        String pathInfo = requestURI.substring(contextPath.length());

        System.out.println("GET " + requestURI + " called (CompanyServlet)");
        logger.debug("GET {} called (CompanyServlet)", requestURI);

        if (isStaticResource(pathInfo)) {
            System.out.println("Serving static resource: " + pathInfo);
            logger.debug("Serving static resource: {}", pathInfo);
            return;
        }

        // only protect paths under /company/
        if (pathInfo.startsWith("/company/")) {
            Claims claims = AuthService.validateJwtAndGetClaims(request, response);
            if (claims == null) {
                return;
            }

            // if validation passed, set the claims as request attributes for later use
            request.setAttribute("userClaims", claims);
            request.setAttribute("userEmail", claims.getSubject());
            request.setAttribute("userId", claims.get("user_id", String.class));
            request.setAttribute("companyId", claims.get("comp_id", String.class));
            request.setAttribute("companyName", claims.get("company_name", String.class));
        }

        handleCompanyGetRequest(request, response, pathInfo);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String requestURI = request.getRequestURI();
        String contextPath = request.getContextPath();

        System.out.println("POST " + requestURI + " called (website_backend.CompanyServlet)");
        logger.debug("POST {} called (website_backend.CompanyServlet)", requestURI);

        if ((contextPath + "/login").equals(requestURI)) {
            handleLoginPostRequest(request, response);
        } else {
            handleCompanyPostRequest(request, response, requestURI, contextPath);
        }
    }

    private boolean isStaticResource(String pathInfo) {
        boolean isStatic = pathInfo.startsWith("/css/") || pathInfo.startsWith("/js/") ||
                pathInfo.startsWith("/images/") ||
                pathInfo.endsWith(".css") || pathInfo.endsWith(".js") ||
                pathInfo.endsWith(".png") || pathInfo.endsWith(".jpg") ||
                pathInfo.endsWith(".jpeg") || pathInfo.endsWith(".gif");
        logger.trace("Is '{}' a static resource? {}", pathInfo, isStatic);
        return isStatic;
    }

    private void handleCompanyGetRequest(HttpServletRequest request, HttpServletResponse response, String pathInfo)
            throws ServletException, IOException {
        if (pathInfo.startsWith("/company/")) {
            Map<String, String> parsedPath = parseCompanyPath(pathInfo);
            String companyId = parsedPath.get("companyId");
            String subPath = parsedPath.get("subPath");

            System.out.println("Extracted Company ID: " + companyId);
            System.out.println("Sub-path: " + subPath);
            logger.debug("Handling GET request for company ID: {} with sub-path: {}", companyId, subPath);

            forwardToCompanyPage(request, response, companyId, subPath);
        } else {
            System.out.println("Unexpected GET request to CompanyServlet: " + request.getRequestURI());
            logger.warn("Unexpected GET request to CompanyServlet: {}. Redirecting to index.", request.getRequestURI());
            response.sendRedirect(request.getContextPath() + INDEX_PAGE);
        }
    }

    private Map<String, String> parseCompanyPath(String pathInfo) {
        Map<String, String> parsed = new HashMap<>();
        String remainingPath = pathInfo.substring("/company/".length());
        String companyId;
        String subPath = "";

        int slashIndex = remainingPath.indexOf("/");
        if (slashIndex != -1) {
            companyId = remainingPath.substring(0, slashIndex);
            subPath = remainingPath.substring(slashIndex);
        } else {
            companyId = remainingPath;
        }
        parsed.put("companyId", companyId);
        parsed.put("subPath", subPath);
        logger.trace("Parsed pathInfo '{}' into companyId='{}', subPath='{}'", pathInfo, companyId, subPath);
        return parsed;
    }

    private void forwardToCompanyPage(HttpServletRequest request, HttpServletResponse response, String companyId, String subPath)
            throws ServletException, IOException {
        String forwardPath;
        switch (subPath) {
            case "/product":
                forwardPath = REGISTER_PRODUCT_HTML;
                break;
            case "/contact":
                forwardPath = ADD_CONTACT_HTML;
                break;
            case "/products":
                forwardPath = DISPLAY_PRODUCTS_HTML;
                break;
            case "/contacts":
                forwardPath = DISPLAY_CONTACTS_HTML;
                break;
            case "":
                forwardPath = COMPANY_PAGE_HTML;
                break;
            default:
                System.out.println("Unknown sub-path under /company/: " + subPath);
                logger.warn("Unknown sub-path under /company/: {} for company ID: {}. Sending 404.", subPath, companyId);
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Resource not found for company ID: " + companyId + " with sub-path: " + subPath);
                return;
        }
        System.out.println("Forwarding to " + forwardPath + " for Company ID: " + companyId);
        logger.info("Forwarding to {} for Company ID: {}", forwardPath, companyId);
        request.getRequestDispatcher(forwardPath).forward(request, response);
    }

    private void handleLoginPostRequest(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String email = request.getParameter("email");
        String password = request.getParameter("password");

        System.out.println("Attempting login for: " + email);
        logger.info("Attempting login for user: {}", email);

        String userIdFromDb = null;
        String companyIdFromDb = null;
        String companyNameFromDb = null;
        String userPasswordHash;
        boolean isAuthenticated = false;

        try (AdminDBMS dbms = new AdminDBMS(DB_URL, DB_USER, DB_PASSWORD)) {
            String queryUser = String.format("SELECT u.user_id, u.password_hash, u.comp_id, c.name FROM Users u JOIN Companies c ON u.comp_id = c.comp_id WHERE u.email = '%s'", email);
            logger.debug("Executing user lookup query: {}", queryUser);

            try (ResultSet rs = dbms.executeQuery(queryUser)) {
                if (rs.next()) {
                    userIdFromDb = String.valueOf(rs.getInt("user_id"));
                    userPasswordHash = rs.getString("password_hash");
                    companyIdFromDb = String.valueOf(rs.getInt("comp_id"));
                    companyNameFromDb = rs.getString("name");

                    isAuthenticated = BCrypt.checkpw(password, userPasswordHash);
                    logger.debug("Password check result for user {}: {}", email, isAuthenticated);
                } else {
                    logger.warn("Login failed: User '{}' not found in database.", email);
                }
            }
        } catch (SQLException e) {
            logger.error("Database error during login for user {}: {}", email, e.getMessage(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error during login.");
            return;
        } catch (Exception e) {
            logger.error("Unexpected error during login for user {}: {}", email, e.getMessage(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An unexpected server error occurred.");
            return;
        }

        if (isAuthenticated) {
            String jwtToken = AuthService.generateJwtToken(email, userIdFromDb, Integer.parseInt(companyIdFromDb), companyNameFromDb);
            logger.info("Generated JWT for user {}", email);

            AuthService.setAuthCookie(response, jwtToken, request);
            AuthService.setCompanyDisplayCookie(response, companyNameFromDb);

            System.out.println("Login successful. Redirecting to /company/" + companyIdFromDb);
            logger.info("Login successful for user '{}'. Redirecting to /company/{}", email, companyIdFromDb);
            response.sendRedirect(request.getContextPath() + "/company/" + companyIdFromDb);

        } else {
            logger.warn("Login failed for user: {}", email);
            AuthService.handleAuthenticationFailure(response, "Invalid email or password.");
        }
    }

    private void handleCompanyPostRequest(HttpServletRequest request, HttpServletResponse response, String requestURI, String contextPath)
            throws ServletException, IOException {
        String pathInfo = requestURI.substring(contextPath.length());
        Map<String, String> parsedPath = parseCompanyPath(pathInfo);
        String companyId = parsedPath.get("companyId");
        String subPath = parsedPath.get("subPath");

        if ("/product".equals(subPath) && companyId != null) {
            System.out.println("Forwarding POST /company/" + companyId + "/product to RegisterProductServlet.");
            logger.info("Forwarding POST /company/{}/product to RegisterProductServlet.", companyId);
            request.getRequestDispatcher("/register_product").forward(request, response);
        } else {
            System.out.println("Unexpected POST request to website_backend.CompanyServlet: " + requestURI);
            logger.warn("Unexpected POST request to CompanyServlet: {}. Sending 404.", requestURI);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Unexpected POST request.");
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        System.out.println("CompanyServlet destroyed.");
        logger.info("CompanyServlet destroyed.");
    }
}