package gateway.connectionService;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import gateway.RPS.RPS;
import gateway.connectionService.iConnection.IConnection;
import gateway.connectionService.iConnection.IConnectionHTTP;
import gateway.connectionService.request.Request;
import gateway.connectionService.server.GenericServer;
import gateway.connectionService.server.Handler;
import gateway.connectionService.server.Protocol;
import httpServer.GenericHttpServer;
import httpServer.Method;
import httpServer.Pair;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class ConnectionService {
    private final GenericServer tcpUdpServer;
    private GenericHttpServer httpServer;

    private final Protocol protocol = new ConnectionProtocol();
    private final Handler handler = new ConnectionHandler();
    private final RPS<String, ?, ?> rps;

    private final Map<Method, Pair> callbacks = Map.of(
            Method.GET, new Pair(
                    connection -> {
                        try {
                            ByteBuffer buffer = connection.receive();
                            if (buffer == null) return false;
                            String rawMessage = new String(buffer.array(), 0, buffer.limit(), StandardCharsets.UTF_8);
                            JsonObject jsonPayload;
                            try {
                                jsonPayload = JsonParser.parseString(rawMessage).getAsJsonObject();
                            } catch (JsonSyntaxException | IllegalStateException e) {
                                JsonObject errorResponse = new JsonObject();
                                errorResponse.addProperty("status", "error");
                                errorResponse.addProperty("message", "Invalid JSON format or not an object in GET query: " + e.getMessage());
                                connection.send(ByteBuffer.wrap(errorResponse.toString().getBytes(StandardCharsets.UTF_8)));
                                return false;
                            }
                            if (!jsonPayload.has("command")) {
                                JsonObject errorResponse = new JsonObject();
                                errorResponse.addProperty("status", "error");
                                errorResponse.addProperty("message", "Missing 'command' in GET request.");
                                connection.send(ByteBuffer.wrap(errorResponse.toString().getBytes(StandardCharsets.UTF_8)));
                                return false;
                            }
                            return true;
                        } catch (IOException e) {
                            System.err.println("GET Validator I/O error: " + e.getMessage());
                            try {
                                JsonObject errorResponse = new JsonObject();
                                errorResponse.addProperty("status", "error");
                                errorResponse.addProperty("message", "Internal validator error: " + e.getMessage());
                                connection.send(ByteBuffer.wrap(errorResponse.toString().getBytes(StandardCharsets.UTF_8)));
                            } catch (IOException innerE) {
                                /* ignore */
                            }
                            return false;
                        }
                    },
                    connection -> {
                        try {
                            handler.onReceive(connection);
                        } catch (IOException e) {
                            System.err.println("GET Controller I/O error: " + e.getMessage());
                        }
                    }),

            Method.POST, new Pair(
                    connection -> {
                        try {
                            ByteBuffer buffer = connection.receive();
                            if (buffer == null) return false;
                            String rawMessage = new String(buffer.array(), 0, buffer.limit(), StandardCharsets.UTF_8);
                            JsonObject jsonPayload;
                            try {
                                jsonPayload = JsonParser.parseString(rawMessage).getAsJsonObject();
                            } catch (JsonSyntaxException | IllegalStateException e) {
                                JsonObject errorResponse = new JsonObject();
                                errorResponse.addProperty("status", "error");
                                errorResponse.addProperty("message", "Invalid JSON format or not an object in POST body: " + e.getMessage());
                                connection.send(ByteBuffer.wrap(errorResponse.toString().getBytes(StandardCharsets.UTF_8)));
                                return false;
                            }
                            if (!jsonPayload.has("command") || !jsonPayload.has("data") || !jsonPayload.get("data").isJsonObject()) {
                                JsonObject errorResponse = new JsonObject();
                                errorResponse.addProperty("status", "error");
                                errorResponse.addProperty("message", "Missing 'command' or 'data' (or 'data' not object) in POST request.");
                                connection.send(ByteBuffer.wrap(errorResponse.toString().getBytes(StandardCharsets.UTF_8)));
                                return false;
                            }
                            return true;
                        } catch (IOException e) {
                            System.err.println("POST Validator I/O error: " + e.getMessage());
                            try {
                                JsonObject errorResponse = new JsonObject();
                                errorResponse.addProperty("status", "error");
                                errorResponse.addProperty("message", "Internal validator error: " + e.getMessage());
                                connection.send(ByteBuffer.wrap(errorResponse.toString().getBytes(StandardCharsets.UTF_8)));
                            } catch (IOException innerE) {
                                /* ignore */
                            }
                            return false;
                        }
                    },
                    connection -> {
                        try {
                            handler.onReceive(connection);
                        } catch (IOException e) {
                            System.err.println("POST Controller I/O error: " + e.getMessage());
                        }
                    })
    );

    public ConnectionService(RPS<String, ?, ?> rps) throws IOException {
        tcpUdpServer = new GenericServer(handler);
        this.rps = rps;
    }

    public void registerTCP(int port, String ip) {
        tcpUdpServer.registerTCP(port, ip);
    }

    public void registerUDP(int port, String ip) {
        tcpUdpServer.registerUDP(port, ip);
    }

    public void registerHTTP(int port, String ip) {
        httpServer = new GenericHttpServer(ip, port);
        httpServer.addRoute("/iots", callbacks);
    }

    public void start() {
        new Thread(() -> httpServer.start()).start();
        tcpUdpServer.start();
    }

    private class ConnectionHandler implements Handler {
        @Override
        public JsonObject onReceive(IConnection connection) {
            protocol.handle(connection, null);
            return null;
        }

        @Override
        public boolean onAccept(IConnection connection) {
            String connectionType;
            if (connection instanceof IConnectionHTTP) {
                connectionType = "HTTP Connection";
            } else {
                connectionType = "TCP Connection";
            }

            System.out.println("New connection accepted: " + connectionType);
            return true;
        }

        @Override
        public void send(IConnection connection, JsonObject message) {
            if (message != null) {
                protocol.handle(connection, message);
            }
        }
    }

    private class ConnectionProtocol implements Protocol {
        @Override
        public void handle(IConnection connection, JsonObject message) {
            try {
                if (message == null) {
                    ByteBuffer buffer = connection.receive();
                    if (buffer != null) {
                        String rawMessage = new String(buffer.array(), 0, buffer.limit(), StandardCharsets.UTF_8);
                        JsonObject jsonPayload;
                        try {
                            jsonPayload = JsonParser.parseString(rawMessage).getAsJsonObject();
                        } catch (JsonSyntaxException e) {
                            System.err.println("ConnectionProtocol: Invalid JSON format received: " +
                                    rawMessage + " Error: " + e.getMessage());
                            String errorMsg = "Error: Invalid JSON format. Please send valid JSON.\n";
                            connection.send(ByteBuffer.wrap(errorMsg.getBytes(StandardCharsets.UTF_8)));
                            return;
                        } catch (IllegalStateException e) {
                            System.err.println("ConnectionProtocol: JSON payload is not a JSON object: " +
                                    rawMessage + " Error: " + e.getMessage());
                            String errorMsg = "Error: JSON payload must be a JSON object\n";
                            connection.send(ByteBuffer.wrap(errorMsg.getBytes(StandardCharsets.UTF_8)));
                            return;
                        }

                        try {
                            rps.handle(new Request(connection, handler, jsonPayload));
                        } catch (Exception e) {
                            System.err.println("ConnectionProtocol: Error processing request in RPS: " + e.getMessage());
                            String errorMsg = "Error processing request: " + e.getMessage() + "\n";
                            connection.send(ByteBuffer.wrap(errorMsg.getBytes(StandardCharsets.UTF_8)));
                        }
                    }
                } else {
                    String responseString = message.toString();
                    responseString += "\n";
                    connection.send(ByteBuffer.wrap(responseString.getBytes(StandardCharsets.UTF_8)));
                }
            } catch (IOException e) {
                System.err.println("Connection error: " + e.getMessage());
                try {
                    connection.receive();
                } catch (IOException ignore) {
                    /* ignore */
                }
            }
        }
    }
}
