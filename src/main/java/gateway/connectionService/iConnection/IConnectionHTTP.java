package gateway.connectionService.iConnection;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class IConnectionHTTP implements IConnection {
    private final HttpExchange exchange;
    private final JsonObject requestPayload;

    public IConnectionHTTP(HttpExchange exchange, JsonObject requestPayload) {
        this.exchange = exchange;
        this.requestPayload = requestPayload;
    }

    @Override
    public ByteBuffer receive() {
        if (requestPayload != null) {
            String jsonString = requestPayload.toString();
            return ByteBuffer.wrap(jsonString.getBytes(StandardCharsets.UTF_8));
        }
        return null;
    }

    @Override
    public void send(ByteBuffer buffer) throws IOException {
        String responseString = new String(buffer.array(), 0, buffer.limit(), StandardCharsets.UTF_8);
        int statusCode = 200;
        String contentType = "application/json";

        try {
            JsonObject parsedResponse = JsonParser.parseString(responseString).getAsJsonObject();
        } catch (JsonSyntaxException e) {
            contentType = "text/plain";
            statusCode = 500;
            System.err.println("IConnectionHTTP: Response from RPS is invalid JSON: " + e.getMessage());
        }
        responseString += "\n";

        byte[] responseBytes = responseString.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}