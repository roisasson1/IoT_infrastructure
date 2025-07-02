package httpServer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import gateway.connectionService.iConnection.IConnection;
import gateway.connectionService.iConnection.IConnectionHTTP;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class HttpRequestHandler implements HttpHandler {
    private final Map<Method, Pair> callbacks;
    private final Gson gson = new Gson();

    private static final int BAD_REQUEST_CODE = 400;
    private static final int METHOD_NOT_ALLOWED_CODE = 405;
    private static final int UNSUPPORTED_MEDIA_TYPE_CODE = 415;
    private static final int INTERNAL_SERVER_CODE = 500;

    public HttpRequestHandler(Map<Method, Pair> callbacks) {
        this.callbacks = callbacks;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Method method = extractMethod(exchange);
            if (method == null) {
                return;
            }

            if (!isMethodSupported(method, exchange)) {
                return;
            }

            JsonObject requestPayload = buildRequestObject(method, exchange);
            if (requestPayload == null) {
                return;
            }

            IConnection IConnectionHTTP = new IConnectionHTTP(exchange, requestPayload);
            Pair pair = callbacks.get(method);

            if (pair.getValidator().test(IConnectionHTTP)) {
                pair.getController().accept(IConnectionHTTP);
                System.out.println("HTTP request successfully passed to controller/RPS pipeline.");
            } else {
                System.out.println("HTTP request failed validation; error response sent by validator.");
            }
        } catch (Exception e) {
            System.err.println("HttpRequestHandler: Unexpected internal error: " + e.getMessage());
            sendErrorResponse(exchange, INTERNAL_SERVER_CODE, "Unexpected server error: " + e.getMessage());
        }
    }

    private Method extractMethod(HttpExchange exchange) throws IOException {
        String requestMethod = exchange.getRequestMethod();
        try {
            return Method.valueOf(requestMethod);
        } catch (IllegalArgumentException e) {
            sendErrorResponse(exchange, METHOD_NOT_ALLOWED_CODE, "Method Not Allowed");
            return null;
        }
    }

    private boolean isMethodSupported(Method method, HttpExchange exchange) throws IOException {
        if (!callbacks.containsKey(method)) {
            sendErrorResponse(exchange, METHOD_NOT_ALLOWED_CODE, "Method Not Allowed");
            return false;
        }
        return true;
    }

    private JsonObject buildRequestObject(Method method, HttpExchange exchange) throws IOException {
        JsonObject request = new JsonObject();
        JsonObject queryParams = createQueryJson(exchange);

        try {
            if (method != Method.GET && method != Method.DELETE) {
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                if (contentType == null || !contentType.contains("application/json")) {
                    sendErrorResponse(exchange, UNSUPPORTED_MEDIA_TYPE_CODE, "Unsupported Content-Type. Expected application/json");
                    return null;
                }

                JsonObject bodyParams = createBodyJson(exchange);
                if (bodyParams == null) {
                    sendErrorResponse(exchange, BAD_REQUEST_CODE, "Invalid JSON body");
                    return null;
                }
                bodyParams.entrySet().forEach(entry -> request.add(entry.getKey(), entry.getValue()));
            }

            queryParams.entrySet().forEach(entry -> request.add(entry.getKey(), entry.getValue()));
            return request;

        } catch (JsonParseException e) {
            sendErrorResponse(exchange, BAD_REQUEST_CODE, "Invalid JSON body");
            return null;
        }
    }

    private void sendResponse(HttpExchange exchange, JsonObject response, int code) throws IOException {
        byte[] responseBytes = response.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, responseBytes.length);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(responseBytes);
        }
    }

    private void sendErrorResponse(HttpExchange exchange, int code, String message) throws IOException {
        JsonObject response = new JsonObject();
        response.addProperty("status", "error");
        response.addProperty("message", message);
        sendResponse(exchange, response, code);
    }

    private JsonObject createQueryJson(HttpExchange exchange) {
        JsonObject request = new JsonObject();
        String query = exchange.getRequestURI().getQuery();

        if (query != null) {
            String[] pairs = query.split("&");

            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    request.addProperty(keyValue[0], keyValue[1]);
                } else if (keyValue.length == 1) {
                    request.addProperty(keyValue[0], "");
                }
            }
        }

        return request;
    }

    private JsonObject createBodyJson(HttpExchange exchange) throws IOException {
        StringBuilder body = new StringBuilder();
        String line;

        try (InputStreamReader in = new InputStreamReader(exchange.getRequestBody());
             BufferedReader reader = new BufferedReader(in)) {
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }

        try {
            return gson.fromJson(body.toString(), JsonObject.class);
        } catch (JsonParseException e) {
            return null;
        }
    }

}