package gateway.connectionService.server;

import com.google.gson.JsonObject;
import gateway.connectionService.iConnection.IConnection;

public interface Protocol {
    void handle(IConnection connection, JsonObject message);
}