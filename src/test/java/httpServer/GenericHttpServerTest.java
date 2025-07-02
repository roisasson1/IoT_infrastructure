package httpServer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import gateway.connectionService.iConnection.IConnection;
import gateway.connectionService.server.Handler;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class GenericHttpServerTest {
    private static final String BASE_URL = "http://localhost:8080";
    private static final String TEST_PATH = "/test";

    private static class MockConnectionHandler implements Handler {
        @Override
        public JsonObject onReceive(IConnection connection) {
            System.out.println("MockConnectionHandler.onReceive called. This should typically be handled by Pair's controller for HTTP tests.");
            return null;
        }

        @Override
        public boolean onAccept(IConnection connection) {
            System.out.println("MockConnectionHandler.onAccept called.");
            return true;
        }

        @Override
        public void send(IConnection connection, JsonObject message) throws IOException {
            System.out.println("MockConnectionHandler.send called. This should be handled by IConnection.send() directly.");
            connection.send(ByteBuffer.wrap(message.toString().getBytes(StandardCharsets.UTF_8)));
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        new MockConnectionHandler();

        GenericHttpServer server = new GenericHttpServer("localhost", 8080);
        Map<Method, Pair> callbacks = new HashMap<>();

        Function<ByteBuffer, JsonObject> parseByteBufferToJson = buffer -> {
            if (buffer == null) return null;
            String rawMessage = new String(buffer.array(), 0, buffer.limit(), StandardCharsets.UTF_8);
            try {
                return JsonParser.parseString(rawMessage).getAsJsonObject();
            } catch (JsonSyntaxException | IllegalStateException e) {
                System.err.println("Failed to parse JSON from ByteBuffer: " + rawMessage + ", Error: " + e.getMessage());
                return null;
            }
        };

        BiConsumer<IConnection, String> sendErrorJson = (conn, msg) -> {
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("status", "error");
            errorResponse.addProperty("message", msg);
            try {
                conn.send(ByteBuffer.wrap(errorResponse.toString().getBytes(StandardCharsets.UTF_8)));
            } catch (IOException e) {
                System.err.println("Error sending error response: " + e.getMessage());
            }
        };

        // GET
        callbacks.put(Method.GET, new Pair(
                connection -> {
                    try {
                        JsonObject requestJson = parseByteBufferToJson.apply(connection.receive());
                        if (requestJson == null) {
                            sendErrorJson.accept(connection, "Invalid JSON received for GET request.");
                            return false;
                        }
                        if (!requestJson.has("param1")) {
                            sendErrorJson.accept(connection, "Missing 'param1' in GET request.");
                            return false;
                        }
                        return true;
                    } catch (IOException e) {
                        sendErrorJson.accept(connection, "IO Error during GET validation: " + e.getMessage());
                        return false;
                    }
                },
                connection -> {
                    try {
                        JsonObject requestJson = parseByteBufferToJson.apply(connection.receive());
                        JsonObject response = new JsonObject();
                        response.addProperty("status", "success");
                        response.addProperty("param1Value", requestJson.get("param1").getAsString());
                        connection.send(ByteBuffer.wrap(response.toString().getBytes(StandardCharsets.UTF_8)));
                    } catch (IOException e) {
                        sendErrorJson.accept(connection, "IO Error during GET processing: " + e.getMessage());
                    }
                }
        ));

        // POST
        callbacks.put(Method.POST, new Pair(
                // Validator
                connection -> {
                    try {
                        JsonObject requestJson = parseByteBufferToJson.apply(connection.receive());
                        if (requestJson == null) {
                            sendErrorJson.accept(connection, "Invalid JSON received for POST request.");
                            return false;
                        }
                        if (!requestJson.has("data")) {
                            sendErrorJson.accept(connection, "Missing 'data' in POST request.");
                            return false;
                        }
                        return true;
                    } catch (IOException e) {
                        sendErrorJson.accept(connection, "IO Error during POST validation: " + e.getMessage());
                        return false;
                    }
                },
                // Controller
                connection -> {
                    try {
                        JsonObject requestJson = parseByteBufferToJson.apply(connection.receive());
                        JsonObject response = new JsonObject();
                        response.addProperty("status", "received");
                        response.addProperty("dataValue", requestJson.get("data").getAsString());
                        connection.send(ByteBuffer.wrap(response.toString().getBytes(StandardCharsets.UTF_8)));
                    } catch (IOException e) {
                        sendErrorJson.accept(connection, "IO Error during POST processing: " + e.getMessage());
                    }
                }
        ));

        // PUT
        callbacks.put(Method.PUT, new Pair(
                // Validator
                connection -> {
                    try {
                        JsonObject requestJson = parseByteBufferToJson.apply(connection.receive());
                        if (requestJson == null) {
                            sendErrorJson.accept(connection, "Invalid JSON received for PUT request.");
                            return false;
                        }
                        if (!requestJson.has("update")) {
                            sendErrorJson.accept(connection, "Missing 'update' in PUT request.");
                            return false;
                        }
                        return true;
                    } catch (IOException e) {
                        sendErrorJson.accept(connection, "IO Error during PUT validation: " + e.getMessage());
                        return false;
                    }
                },
                // Controller
                connection -> {
                    try {
                        JsonObject requestJson = parseByteBufferToJson.apply(connection.receive());
                        JsonObject response = new JsonObject();
                        response.addProperty("status", "updated");
                        response.addProperty("updateValue", requestJson.get("update").getAsString());
                        connection.send(ByteBuffer.wrap(response.toString().getBytes(StandardCharsets.UTF_8)));
                    } catch (IOException e) {
                        sendErrorJson.accept(connection, "IO Error during PUT processing: " + e.getMessage());
                    }
                }
        ));

        // DELETE
        callbacks.put(Method.DELETE, new Pair(
                // Validator
                connection -> {
                    try {
                        JsonObject requestJson = parseByteBufferToJson.apply(connection.receive());
                        if (requestJson == null) {
                            sendErrorJson.accept(connection, "Invalid JSON received for DELETE request.");
                            return false;
                        }
                        if (!requestJson.has("id")) {
                            sendErrorJson.accept(connection, "Missing 'id' in DELETE request.");
                            return false;
                        }
                        return true;
                    } catch (IOException e) {
                        sendErrorJson.accept(connection, "IO Error during DELETE validation: " + e.getMessage());
                        return false;
                    }
                },
                // Controller
                connection -> {
                    try {
                        JsonObject requestJson = parseByteBufferToJson.apply(connection.receive());
                        JsonObject response = new JsonObject();
                        response.addProperty("status", "deleted");
                        response.addProperty("deletedId", requestJson.get("id").getAsString());
                        connection.send(ByteBuffer.wrap(response.toString().getBytes(StandardCharsets.UTF_8)));
                    } catch (IOException e) {
                        sendErrorJson.accept(connection, "IO Error during DELETE processing: " + e.getMessage());
                    }
                }
        ));

        server.addRoute(TEST_PATH, callbacks);
        server.start();

        HttpClient client = HttpClient.newHttpClient();

        System.out.println("--- Running Tests ---");

        // GET Tests
        testRequest("GET without 'param1' (expect validation error, status 200)",
                HttpRequest.newBuilder().uri(URI.create(BASE_URL + TEST_PATH))
                        .GET().build(), client);

        testRequest("GET with 'param1' (expect success)",
                HttpRequest.newBuilder().uri(URI.create(BASE_URL + TEST_PATH + "?param1=testValue")).GET().build(), client);

        testRequest("GET to non-existent path (expect 404)",
                HttpRequest.newBuilder().uri(URI.create(BASE_URL + "/nonexist"))
                        .GET().build(), client);

        // POST Tests
        testRequest("POST with valid body (expect success)",
                HttpRequest.newBuilder().uri(URI.create(BASE_URL + TEST_PATH))
                        .POST(HttpRequest.BodyPublishers.ofString("{\"data\": \"some data\"}"))
                        .header("Content-Type", "application/json").build(), client);

        testRequest("POST without 'data' (expect validation error, status 200)",
                HttpRequest.newBuilder().uri(URI.create(BASE_URL + TEST_PATH))
                        .POST(HttpRequest.BodyPublishers.ofString("{\"other\": \"value\"}"))
                        .header("Content-Type", "application/json").build(), client);

        testRequest("POST with invalid JSON body (expect 400 from HttpRequestHandler)",
                HttpRequest.newBuilder().uri(URI.create(BASE_URL + TEST_PATH))
                        .POST(HttpRequest.BodyPublishers.ofString("this is not json"))
                        .header("Content-Type", "application/json").build(), client);

        testRequest("POST with wrong Content-Type (expect 415 from HttpRequestHandler)",
                HttpRequest.newBuilder().uri(URI.create(BASE_URL + TEST_PATH))
                        .POST(HttpRequest.BodyPublishers.ofString("some data"))
                        .header("Content-Type", "text/plain").build(), client);


        // PUT Tests
        testRequest("PUT with valid 'update' (expect success)",
                HttpRequest.newBuilder().uri(URI.create(BASE_URL + TEST_PATH))
                        .PUT(HttpRequest.BodyPublishers.ofString("{\"update\": \"value123\"}"))
                        .header("Content-Type", "application/json").build(), client);

        testRequest("PUT without 'update' (expect validation error, status 200)",
                HttpRequest.newBuilder().uri(URI.create(BASE_URL + TEST_PATH))
                        .PUT(HttpRequest.BodyPublishers.ofString("{\"invalid\": true}"))
                        .header("Content-Type", "application/json").build(), client);

        // DELETE Tests
        testRequest("DELETE with 'id' (expect success)",
                HttpRequest.newBuilder().uri(URI.create(BASE_URL + TEST_PATH + "?id=99"))
                        .method("DELETE", HttpRequest.BodyPublishers.noBody()).build(), client);

        testRequest("DELETE without 'id' (expect validation error, status 200)",
                HttpRequest.newBuilder().uri(URI.create(BASE_URL + TEST_PATH))
                        .method("DELETE", HttpRequest.BodyPublishers.noBody()).build(), client);

        System.out.println("--- Tests Finished ---");
    }

    private static void testRequest(String title, HttpRequest request, HttpClient client) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Test: " + title + " - Status Code: " + response.statusCode() + ", Body: " + response.body());
    }
}