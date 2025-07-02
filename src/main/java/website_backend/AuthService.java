package website_backend;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private static final SecretKey jwtSecretKey;
    private static final long TOKEN_VALIDITY_SECONDS = 30 * 60;

    static {
        String secretString = System.getenv("JWT_SECRET_KEY");
        if (secretString != null && !secretString.isEmpty()) {
            jwtSecretKey = Keys.hmacShaKeyFor(secretString.getBytes(StandardCharsets.UTF_8));
            logger.info("JWT Secret Key loaded from environment variable.");
        } else {
            jwtSecretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
            logger.warn("JWT_SECRET_KEY environment variable not set. Generating a new random key. " +
                    "This is NOT recommended for production environments as tokens will be invalidated on server restart.");
        }
    }

    public static String generateJwtToken(String email, String userId, int companyId, String companyName) {
        Instant now = Instant.now();
        Date expirationDate = Date.from(now.plusSeconds(TOKEN_VALIDITY_SECONDS));

        String jwtToken = Jwts.builder()
                .setSubject(email)
                .claim("user_id", userId)
                .claim("comp_id", String.valueOf(companyId))
                .claim("company_name", companyName)
                .setIssuedAt(Date.from(now))
                .setExpiration(expirationDate)
                .signWith(jwtSecretKey, SignatureAlgorithm.HS256)
                .compact();

        logger.debug("Generated JWT for user: {}", email);
        return jwtToken;
    }

    public static Claims validateJwtAndGetClaims(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String jwtToken = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("authToken".equals(cookie.getName())) {
                    jwtToken = cookie.getValue();
                    break;
                }
            }
        }

        if (jwtToken == null || jwtToken.isEmpty()) {
            logger.warn("JWT token missing from request for path: {}", request.getRequestURI());
            handleAuthenticationFailure(response, "Authentication required.");
            return null;
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(jwtSecretKey)
                    .build()
                    .parseSignedClaims(jwtToken)
                    .getPayload();

            logger.debug("JWT validated successfully for subject: {}", claims.getSubject());
            return claims;

        } catch (ExpiredJwtException e) {
            logger.warn("JWT token expired for path: {}. Subject: {}", request.getRequestURI(), e.getClaims().getSubject());
            handleAuthenticationFailure(response, "Session expired. Please log in again.");
            return null;
        } catch (UnsupportedJwtException | MalformedJwtException | SignatureException | IllegalArgumentException e) {
            logger.error("Invalid JWT token for path: {}. Error: {}", request.getRequestURI(), e.getMessage());
            handleAuthenticationFailure(response, "Invalid authentication token.");
            return null;
        }
    }

    public static void setAuthCookie(HttpServletResponse response, String jwtToken, HttpServletRequest request) {
        StringBuilder cookieHeader = new StringBuilder("authToken=");
        cookieHeader.append(jwtToken);
        cookieHeader.append("; HttpOnly");
        cookieHeader.append("; Path=/");
        cookieHeader.append("; Max-Age=").append(TOKEN_VALIDITY_SECONDS);

        if (request.isSecure()) {
            cookieHeader.append("; Secure");
        }
        cookieHeader.append("; SameSite=Lax");
        response.addHeader("Set-Cookie", cookieHeader.toString());
        logger.debug("Set HttpOnly JWT cookie via header.");
    }

    public static void setCompanyDisplayCookie(HttpServletResponse response, String companyName) {
        String encodedCompanyName = URLEncoder.encode(companyName, StandardCharsets.UTF_8);
        Cookie companyNameCookie = new Cookie("companyName", encodedCompanyName);
        companyNameCookie.setPath("/");
        companyNameCookie.setMaxAge(60 * 30);
        response.addCookie(companyNameCookie);
        logger.debug("Added display cookie: companyName={}", encodedCompanyName);
    }

    public static void handleAuthenticationFailure(HttpServletResponse response, String message) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer realm=\"MyOT\"");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401 Unauthorized
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message);
        logger.warn("Authentication failure: {}", message);
    }
}