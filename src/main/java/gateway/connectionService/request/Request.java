package gateway.connectionService.request;

import com.google.gson.JsonObject;
import gateway.connectionService.iConnection.IConnection;
import gateway.connectionService.server.Handler;

import java.io.IOException;

public class Request {
    private final IConnection connection;
    private final Handler handler;
    private final JsonObject message;

    public Request(IConnection connection, Handler handler, JsonObject message) {
        this.connection = connection;
        this.handler = handler;
        this.message = message;
    }

    public JsonObject getJsonPayload() {
        return message;
    }

    public void sendResponse(JsonObject message) {
        try {
            handler.send(connection, message);
        } catch (IOException e) {
            System.err.println("Request: send response failed");
        }
    }
}
