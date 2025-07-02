package gateway.RPS;

import com.google.gson.JsonObject;
import dbms.MongoDBMS;
import gateway.RPS.command.Command;
import gateway.RPS.factory.Factory;
import gateway.RPS.parser.JsonCommandParser;
import gateway.RPS.threadPool.ThreadPool;
import gateway.connectionService.request.Request;
import utils.Pair;

import gateway.RPS.command.RegisterCompany;
import gateway.RPS.command.RegisterProduct;
import gateway.RPS.command.RegisterIoT;
import gateway.RPS.command.UpdateIoT;

import java.util.function.Function;

public class RPS<K, D, T> {
    private final ThreadPool pool;
    private final JsonCommandParser jsonCommandParser = new JsonCommandParser();
    private final Factory<String, Request, Command> factory = new Factory<>();
    private MongoDBMS mongoDBMS;

    private static final int DEFAULT_NUM_THREADS = 4;

    public RPS() {
        this(DEFAULT_NUM_THREADS);
    }

    public RPS(int numOfThreads) {
        pool = new ThreadPool(numOfThreads);
        // initializeFactoryCommands();
        try {
            this.mongoDBMS = new MongoDBMS(System.getenv("MONGO_URI"));
            System.out.println("MongoDBMS initialized in RPS.");
        } catch (Exception e) {
            System.err.println("Failed to initialize MongoDBMS: " + e.getMessage());
        }
    }

    public void handle(Request request) {
        pool.execute(() -> {
            JsonObject requestPayload = request.getJsonPayload();

            try {
                Pair<String, JsonObject> parsedCommand = jsonCommandParser.parse(requestPayload);

                Command command = factory.create(parsedCommand.getKey(), request);

                if (command != null) {
                    pool.execute(command::execute);
                } else {
                    String errorMsg = "Unknown command: '" + parsedCommand.getKey() + "'";
                    sendErrorResponse(request, errorMsg);
                }
            } catch (IllegalArgumentException e) {
                System.err.println("RPS illegal argument: " + e.getMessage());
                sendErrorResponse(request, "Invalid request: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("RPS internal error: " + e.getMessage());
                String errorMsg = "Internal server error: " + e.getMessage();
                sendErrorResponse(request, errorMsg);
            }
        });
    }

    private void sendErrorResponse(Request request, String errorMsg) {
        try {
            JsonObject errorResponseJson = new JsonObject();
            errorResponseJson.addProperty("status", "error");
            errorResponseJson.addProperty("message", errorMsg);

            request.sendResponse(errorResponseJson);
        } catch (Exception e) {
            System.err.println("Failed to send error response: " + e.getMessage());
        }
    }

    public void initializeFactoryCommands() {
        factory.add("Register Company", request -> new RegisterCompany(request, mongoDBMS));
        factory.add("Register Product", request -> new RegisterProduct(request, mongoDBMS));
        factory.add("Register IoT", request -> new RegisterIoT(request, mongoDBMS));
        factory.add("Update IoT", request -> new UpdateIoT(request, mongoDBMS));
    }

    public void addCommand(String key, Function<Request, ? extends Command> value) {
        this.factory.add(key, value);
    }

    public MongoDBMS getMongoDBMS() {
        return mongoDBMS;
    }
}