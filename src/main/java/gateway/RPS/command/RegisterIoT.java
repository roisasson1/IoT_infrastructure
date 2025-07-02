package gateway.RPS.command;

import com.google.gson.JsonObject;
import dbms.MongoDBMS;
import gateway.RPS.parser.JsonCommandParser;
import gateway.connectionService.request.Request;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class RegisterIoT implements Command {
    private static final Logger logger = LoggerFactory.getLogger(RegisterIoT.class);

    private final Request request;
    private final JsonCommandParser jsonCommandParser = new JsonCommandParser();
    private final MongoDBMS mongoDBMS;

    private static final String COMMAND_NAME = "RegisterIoT";

    public RegisterIoT(Request request, MongoDBMS mongoDBMS) {
        this.request = request;
        this.mongoDBMS = mongoDBMS;
    }

    @Override
    public void execute() {
        String iotId;
        String prodName;
        String version;
        String companyName;
        String compId;
        JsonObject responseJson = new JsonObject();
        JsonObject extraData = new JsonObject();

        try {
            JsonObject fullRequestPayload = request.getJsonPayload();
            logger.debug("Received payload for {}: {}", COMMAND_NAME, fullRequestPayload);

            JsonObject commandData = jsonCommandParser.parse(fullRequestPayload).getValue();
            logger.debug("Parsed command data: {}", commandData);

            String[] mandatoryFields = {"iot_id", "company_id", "company_name", "product_name", "product_version"};
            String[] errorMessages = {
                    "Error: 'iot_id' is missing from IoT registration data.",
                    "Error: 'company_id' is missing from IoT registration data. It's required for database identification.",
                    "Error: 'company_name' is missing from IoT registration data.",
                    "Error: 'product_name' is missing from IoT registration data.",
                    "Error: 'product_version' is missing from IoT registration data."
            };

            Map<String, String> extractedFields = extractAllMandatoryFields(commandData, mandatoryFields, errorMessages, responseJson);
            if (extractedFields == null) {
                logger.warn("Mandatory fields extraction failed for {}", COMMAND_NAME);
                return;
            }

            iotId = extractedFields.get("iot_id");
            compId = extractedFields.get("company_id");
            companyName = extractedFields.get("company_name");
            prodName = extractedFields.get("product_name");
            version = extractedFields.get("product_version");
            logger.debug("Extracted mandatory fields: iotId='{}', compId='{}', companyName='{}', prodName='{}', version='{}'",
                    iotId, compId, companyName, prodName, version);

            populateExtraData(commandData, extraData, mandatoryFields);
            if (!extraData.entrySet().isEmpty()) {
                logger.debug("Extracted extra data: {}", extraData);
            }

            if (mongoDBMS == null) {
                String errMsg = "MongoDB connection not initialized. Cannot register IoT device.";
                sendErrorResponse(errMsg, responseJson);
                logger.error(errMsg);
                System.err.println(errMsg);
                return;
            }

            logIoTDeviceRegistration(iotId, compId, companyName, prodName, version, extraData, responseJson);

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

    private void populateExtraData(JsonObject commandData, JsonObject extraData, String[] mandatoryFields) {
        Map<String, Boolean> mandatoryFieldMap = new HashMap<>();
        for (String fieldName : mandatoryFields) {
            mandatoryFieldMap.put(fieldName, true);
        }
        mandatoryFieldMap.put("command", true);

        for (Map.Entry<String, com.google.gson.JsonElement> entry : commandData.entrySet()) {
            if (!mandatoryFieldMap.containsKey(entry.getKey())) {
                extraData.add(entry.getKey(), entry.getValue());
            }
        }
    }

    private void logIoTDeviceRegistration(String iotId, String compId, String companyName, String prodName, String version, JsonObject extraData, JsonObject responseJson) {
        logger.info("Attempting to register IoT device: {} for company '{}' (ID: '{}'), product '{}' (v'{}') with extra data: {}",
                iotId, companyName, compId, prodName, version, extraData);

        Document registeredDevice = mongoDBMS.registerIoTDevice(companyName, compId, prodName, version, iotId, extraData);

        if (registeredDevice != null) {
            logger.info("IoT device '{}' registered successfully in MongoDB for company '{}' (ID: '{}'), product '{}' (v'{}').",
                    iotId, companyName, compId, prodName, version);
            System.out.println("IoT device " + iotId + " for company " + companyName + " (ID: " + compId + "), product " + prodName + " (v" + version + ") registered successfully in MongoDB!");
            responseJson.addProperty("status", "success");
            responseJson.addProperty("command", COMMAND_NAME);
            responseJson.addProperty("iot_id", iotId);
            responseJson.addProperty("company_id", compId);
            responseJson.addProperty("company_name", companyName);
            responseJson.addProperty("product_name", prodName);
            responseJson.addProperty("product_version", version);
            for (Map.Entry<String, com.google.gson.JsonElement> entry : extraData.entrySet()) {
                responseJson.add(entry.getKey(), entry.getValue());
            }
            responseJson.addProperty("message", iotId + " IoT device registered!");
        } else {
            String errorMsg = "Failed to register IoT device " + iotId + " for company " + companyName +
                    " (ID: " + compId + "), product " + prodName + " (v" + version + ") in MongoDB. It might already exist or a database error occurred.";
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