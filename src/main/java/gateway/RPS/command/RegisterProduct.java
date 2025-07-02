package gateway.RPS.command;

import com.google.gson.JsonObject;
import dbms.MongoDBMS;
import gateway.RPS.parser.JsonCommandParser;
import gateway.connectionService.request.Request;
import org.bson.Document;
import com.mongodb.client.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class RegisterProduct implements Command {

    private static final Logger logger = LoggerFactory.getLogger(RegisterProduct.class);

    private final Request request;
    private final JsonCommandParser jsonCommandParser = new JsonCommandParser();
    private final MongoDBMS mongoDBMS;

    private static final String COMMAND_NAME = "RegisterProduct";

    public RegisterProduct(Request request, MongoDBMS mongoDBMS) {
        this.request = request;
        this.mongoDBMS = mongoDBMS;
    }

    @Override
    public void execute() {
        String companyName;
        String companyId;
        String prodName;
        String version;
        JsonObject responseJson = new JsonObject();

        try {
            JsonObject fullRequestPayload = request.getJsonPayload();
            logger.debug("Received payload for {}: {}", COMMAND_NAME, fullRequestPayload);

            JsonObject commandData = jsonCommandParser.parse(fullRequestPayload).getValue();
            logger.debug("Parsed command data: {}", commandData);

            String[] mandatoryFields = {"company_name", "company_id", "product_name", "product_version"};
            String[] errorMessages = {
                    "Error: 'company_name' is missing from registration data.",
                    "Error: 'company_id' is missing from registration data. This is now required.",
                    "Error: 'product_name' is missing or empty.",
                    "Error: 'product_version' is missing or empty."
            };

            Map<String, String> extractedFields = extractAllMandatoryFields(commandData, mandatoryFields, errorMessages, responseJson);
            if (extractedFields == null) {
                logger.warn("Mandatory fields extraction failed for {}", COMMAND_NAME);
                return;
            }

            companyName = extractedFields.get("company_name");
            companyId = extractedFields.get("company_id");
            prodName = extractedFields.get("product_name");
            version = extractedFields.get("product_version");
            logger.debug("Extracted fields: companyName='{}', companyId='{}', prodName='{}', version='{}'",
                    companyName, companyId, prodName, version);

            if (mongoDBMS == null) {
                String errMsg = "MongoDB connection not initialized. Cannot register product collection.";
                sendErrorResponse(errMsg, responseJson);
                logger.error(errMsg);
                System.err.println(errMsg);
                return;
            }

            logProductRegistration(companyName, companyId, prodName, version, responseJson);

        } catch (IllegalArgumentException e) {
            String errMsg = "Invalid request format: " + e.getMessage();
            sendErrorResponse(errMsg, responseJson);
            logger.error("{} parsing error: {}", COMMAND_NAME, e.getMessage(), e);
            System.err.println(COMMAND_NAME + " parsing error: " + e.getMessage());
        } catch (Exception e) {
            String errMsg = "Internal error processing request: " + e.getMessage();
            sendErrorResponse(errMsg, responseJson);
            logger.error("{} execution error: {}", COMMAND_NAME, e.getMessage(), e);
            System.err.println(COMMAND_NAME + " execution error: " + e.getMessage());
        } finally {
            request.sendResponse(responseJson);
            logger.info("Sent response for {}: {}", COMMAND_NAME, responseJson);
        }
    }

    private Map<String, String> extractAllMandatoryFields(JsonObject commandData, String[] mandatoryFields, String[] errorMessages, JsonObject responseJson) {
        Map<String, String> fields = new HashMap<>();
        for (int i = 0; i < mandatoryFields.length; i++) {
            String fieldName = mandatoryFields[i];
            String errorMessage = errorMessages[i];

            if (commandData.has(fieldName)) {
                String value = commandData.get(fieldName).getAsString().trim();
                if (value.isEmpty()) {
                    sendErrorResponse(errorMessage, responseJson);
                    logger.warn("Mandatory field '{}' is empty. {}", fieldName, errorMessage);
                    System.err.println(errorMessage);
                    return null;
                }
                fields.put(fieldName, value);
            } else {
                sendErrorResponse(errorMessage, responseJson);
                logger.warn("Mandatory field '{}' is missing. {}", fieldName, errorMessage);
                System.err.println(errorMessage);
                return null;
            }
        }
        return fields;
    }

    private void logProductRegistration(String companyName, String companyId, String prodName, String version, JsonObject responseJson) {
        logger.info("Attempting to register product collection: {} (v{}) for company: {} (ID: {}) in MongoDB.",
                prodName, version, companyName, companyId);
        MongoCollection<Document> productCollection = mongoDBMS.registerProductCollection(companyName, companyId, prodName, version);

        if (productCollection != null) {
            logger.info("Product collection '{}' (v'{}') for company '{}' (ID: '{}') registered successfully in MongoDB!",
                    prodName, version, companyName, companyId);
            System.out.println("Product collection " + prodName + " (v" + version + ") for company " + companyName + " (ID: " + companyId + ") registered successfully in MongoDB!");
            responseJson.addProperty("status", "success");
            responseJson.addProperty("command", COMMAND_NAME);
            responseJson.addProperty("company_id", companyId);
            responseJson.addProperty("company_name", companyName);
            responseJson.addProperty("product_name", prodName);
            responseJson.addProperty("product_version", version);
            responseJson.addProperty("message", "Product collection " + prodName + " registered!");
        } else {
            String errorMsg = "Failed to register product collection for " + prodName + " (v" + version + ") for company " + companyName + " (ID: " + companyId + ") in MongoDB.";
            sendErrorResponse(errorMsg, responseJson);
            logger.error(errorMsg);
            System.err.println(errorMsg);
        }
    }

    private void sendErrorResponse(String message, JsonObject responseJson) {
        responseJson.addProperty("status", "error");
        responseJson.addProperty("command", COMMAND_NAME);
        responseJson.addProperty("message", message);
        System.err.println(message);
    }
}