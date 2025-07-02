package gateway.connectionService.server;

import com.google.gson.JsonObject;
import gateway.connectionService.iConnection.IConnection;

import java.io.IOException;

public interface Handler {
    JsonObject onReceive(IConnection connection) throws IOException;
    boolean onAccept(IConnection connection);
    void send(IConnection connection, JsonObject message) throws IOException;
}
