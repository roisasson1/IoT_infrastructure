package gateway.RPS.command;

import com.google.gson.JsonObject;
import dbms.MongoDBMS;
import gateway.RPS.parser.JsonCommandParser;
import gateway.connectionService.request.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class RegisterCompany implements Command {

    private static final Logger logger = LoggerFactory.getLogger(RegisterCompany.class);

    private final Request request;
    private final JsonCommandParser jsonCommandParser = new JsonCommandParser();
    private final MongoDBMS mongoDBMS;

    private static final String COMMAND_NAME = "RegisterCompany";

    public RegisterCompany(Request request, MongoDBMS mongoDBMS) {
        this.request = request;
        this.mongoDBMS = mongoDBMS;
    }

    @Override
    public void execute() {
        String companyName;
        String compId;
        JsonObject responseJson = new JsonObject();

        try {
            JsonObject fullRequestPayload = request.getJsonPayload();
            logger.debug("Received payload for {}: {}", COMMAND_NAME, fullRequestPayload);

            JsonObject commandData = jsonCommandParser.parse(fullRequestPayload).getValue();
            logger.debug("Parsed command data: {}", commandData);

            String[] mandatoryFields = {"company_name", "company_id"};
            String[] errorMessages = {
                    "Error: 'company_name' is missing from registration data.",
                    "Error: 'company_id' is missing from registration data."
            };

            Map<String, String> extractedFields = extractAllMandatoryFields(commandData, mandatoryFields, errorMessages, responseJson);
            if (extractedFields == null) {
                logger.warn("Mandatory fields extraction failed for {}. Sending error response.", COMMAND_NAME);
                return;
            }

            companyName = extractedFields.get("company_name");
            compId = extractedFields.get("company_id");
            logger.debug("Extracted fields: companyName='{}', compId='{}'", companyName, compId);

            if (mongoDBMS == null) {
                String errMsg = "MongoDB connection not initialized. Cannot register company DB.";
                sendErrorResponse(errMsg, responseJson);
                logger.error(errMsg);
                System.err.println(errMsg);
                return;
            }

            registerCompanyInMongo(companyName, compId, responseJson);

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
                    String specificErrMsg = "Error: '" + fieldName + "' cannot be empty.";
                    sendErrorResponse(specificErrMsg, responseJson);
                    logger.warn(specificErrMsg);
                    System.err.println(specificErrMsg);
                    return null;
                }
                fields.put(fieldName, value);
            } else {
                sendErrorResponse(errorMessage, responseJson);
                logger.warn(errorMessage);
                System.err.println(errorMessage);
                return null;
            }
        }
        return fields;
    }

    private void registerCompanyInMongo(String companyName, String compId, JsonObject responseJson) {
        String fullDbName = companyName + "_" + compId;
        logger.info("Attempting to register company DB for: {} (ID: {}). Full DB Name: {}", companyName, compId, fullDbName);
        boolean success = mongoDBMS.registerCompanyDB(companyName, compId);


        if (success) {
            logger.info("Company DB '{}' (ID: '{}') registered successfully in MongoDB!", companyName, compId);
            System.out.println(fullDbName + " has registered DB successfully in MongoDB!");
            responseJson.addProperty("status", "success");
            responseJson.addProperty("command", COMMAND_NAME);
            responseJson.addProperty("company_name", companyName);
            responseJson.addProperty("company_id", compId);
            responseJson.addProperty("message", fullDbName + " company registered successfully in MongoDB!");
        } else {
            String errorMsg = "Failed to register company DB in MongoDB for " + fullDbName;
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