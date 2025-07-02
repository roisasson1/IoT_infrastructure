package gateway.RPS;

import com.google.gson.JsonObject;
import dbms.MongoDBMS;
import gateway.RPS.command.RegisterCompany;
import gateway.RPS.command.RegisterIoT;
import gateway.RPS.command.RegisterProduct;
import gateway.RPS.command.UpdateIoT;
import gateway.connectionService.iConnection.IConnection;
import gateway.connectionService.request.Request;
import gateway.connectionService.server.Handler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

class RPSTest {
    RPS<String, ?, ?> rps;
    private MongoDBMS mongoDBMS;

    private static class MockConnection implements IConnection {
        private final StringBuilder receivedData = new StringBuilder();
        @Override
        public void send(ByteBuffer buffer) {
            receivedData.append(StandardCharsets.UTF_8.decode(buffer));
            System.out.println("MockConnection received: " + receivedData.toString().trim());
        }
        @Override
        public ByteBuffer receive() {
            return null;
        }
    }

    private static class MockHandler implements Handler {
        @Override
        public JsonObject onReceive(IConnection connection) {
            return null;
        }

        @Override
        public boolean onAccept(IConnection connection) {
            return true;
        }

        @Override
        public void send(IConnection connection, JsonObject message) throws IOException {
            connection.send(ByteBuffer.wrap(message.toString().getBytes(StandardCharsets.UTF_8)));
        }
    }


    @BeforeEach
    void setUp() {
        rps = new RPS<>();
        try {
            this.mongoDBMS = new MongoDBMS(System.getenv("MONGO_URI"));
            System.out.println("MongoDBMS initialized in RPS.");
        } catch (Exception e) {
            System.err.println("Failed to initialize MongoDBMS: " + e.getMessage());
        }

        rps.addCommand("Register Company", request -> new RegisterCompany(request, mongoDBMS));
        rps.addCommand("Register Product", request -> new RegisterProduct(request, mongoDBMS));
        rps.addCommand("Register IoT", request -> new RegisterIoT(request, mongoDBMS));
        rps.addCommand("Update IoT", request -> new UpdateIoT(request, mongoDBMS));
    }

    @Test
    void handleJsonRequests() throws InterruptedException {
        MockConnection mockConnection1 = new MockConnection();
        MockHandler mockHandler = new MockHandler();

        // --- Register Company Request ---
        JsonObject registerCompanyPayload = new JsonObject();
        registerCompanyPayload.addProperty("command", "RegisterCompany");
        JsonObject registerCompanyData = new JsonObject();
        registerCompanyData.addProperty("companyName", "Elta");
        registerCompanyData.addProperty("someParam", "value1");
        registerCompanyPayload.add("data", registerCompanyData);
        rps.handle(new Request(mockConnection1, mockHandler, registerCompanyPayload));


        // --- Register Product Request ---
        JsonObject registerProductPayload = new JsonObject();
        registerProductPayload.addProperty("command", "RegisterProduct");
        JsonObject registerProductData = new JsonObject();
        registerProductData.addProperty("productName", "SensorX");
        registerProductData.addProperty("version", "1.0");
        registerProductPayload.add("data", registerProductData);
        rps.handle(new Request(new MockConnection(), mockHandler, registerProductPayload));


        // --- Register IoT Request ---
        JsonObject registerIoTPayload = new JsonObject();
        registerIoTPayload.addProperty("command", "RegisterIoT");
        JsonObject registerIoTData = new JsonObject();
        registerIoTData.addProperty("deviceId", "iot-device-001");
        registerIoTData.addProperty("location", "WarehouseA");
        registerIoTPayload.add("data", registerIoTData);
        rps.handle(new Request(new MockConnection(), mockHandler, registerIoTPayload));


        // --- Update IoT Request ---
        JsonObject updateIoTPayload = new JsonObject();
        updateIoTPayload.addProperty("command", "UpdateIoT");
        JsonObject updateIoTData = new JsonObject();
        updateIoTData.addProperty("deviceId", "iot-device-001");
        updateIoTData.addProperty("status", "online");
        updateIoTData.addProperty("firmwareVersion", "2.1");
        updateIoTPayload.add("data", updateIoTData);
        rps.handle(new Request(new MockConnection(), mockHandler, updateIoTPayload));

        // --- Unknown Command Request ---
        JsonObject unknownCommandPayload = new JsonObject();
        unknownCommandPayload.addProperty("command", "NonExistentCommand");
        unknownCommandPayload.add("data", new JsonObject());
        rps.handle(new Request(new MockConnection(), mockHandler, unknownCommandPayload));

        Thread.sleep(1000);
    }
}