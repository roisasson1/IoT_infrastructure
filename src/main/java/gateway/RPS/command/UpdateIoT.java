package gateway.RPS.command;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dbms.MongoDBMS;
import gateway.RPS.parser.JsonCommandParser;
import gateway.connectionService.request.Request;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Map;

public class UpdateIoT implements Command {
    private static final Logger logger = LoggerFactory.getLogger(UpdateIoT.class);

    private final Request request;
    private final JsonCommandParser jsonCommandParser = new JsonCommandParser();
    private final MongoDBMS mongoDBMS;

    private static final String COMMAND_NAME = "UpdateIoT";

    public UpdateIoT(Request request, MongoDBMS mongoDBMS) {
        this.request = request;
        this.mongoDBMS = mongoDBMS;
    }

    @Override
    public void execute() {
        JsonObject responseJson = new JsonObject();
        Document updateDataDocument = new Document();

        try {
            JsonObject fullRequestPayload = request.getJsonPayload();
            logger.debug("Received payload for {}: {}", COMMAND_NAME, fullRequestPayload);

            JsonObject commandData = jsonCommandParser.parse(fullRequestPayload).getValue();
            logger.debug("Parsed command data: {}", commandData);

            if (!extractMandatoryFields(commandData, updateDataDocument, responseJson)) {
                logger.warn("Mandatory fields extraction failed for {}. Sending error response.", COMMAND_NAME);
                request.sendResponse(responseJson);
                return;
            }

            appendDynamicFields(commandData, updateDataDocument);
            updateDataDocument.append("timestamp", new Date());
            logger.debug("Constructed update document: {}", updateDataDocument.toJson());

            if (updateDataDocument.size() <= 6) {
                logger.warn("No specific update fields (like status, firmwareVersion, or custom data) provided for IoT device {}. Only logging identity and timestamp.",
                        updateDataDocument.getString("iot_id"));
            }

            if (mongoDBMS == null) {
                String errMsg = "MongoDB connection not initialized. Cannot log IoT device update.";
                sendErrorResponse(errMsg, responseJson);
                logger.error(errMsg);
                request.sendResponse(responseJson);
                return;
            }

            logIoTDeviceUpdate(updateDataDocument, responseJson);

        } catch (IllegalArgumentException e) {
            String errMsg = "Invalid request format: " + e.getMessage();
            sendErrorResponse(errMsg, responseJson);
            logger.error("{} parsing error: {}", COMMAND_NAME, e.getMessage(), e);
        } catch (Exception e) {
            String errMsg = "Internal error processing request: " + e.getMessage();
            sendErrorResponse(errMsg, responseJson);
            logger.error("{} execution error: {}", COMMAND_NAME, e.getMessage(), e);
        } finally {
            if (responseJson.keySet().isEmpty()) {
                String errMsg = "An unexpected error occurred and no specific response was generated.";
                sendErrorResponse(errMsg, responseJson);
                logger.error(errMsg);
            }
            request.sendResponse(responseJson);
            logger.info("Sent response for {}: {}", COMMAND_NAME, responseJson);
        }
    }

    private boolean extractMandatoryFields(JsonObject commandData, Document updateDataDocument, JsonObject responseJson) {
        String[] mandatoryFields = {"iot_id", "company_name", "company_id", "product_name", "product_version"};
        String[] errorMessages = {
                "Error: 'iot_id' is missing from IoT update data.",
                "Error: 'company_name' is missing from IoT update data.",
                "Error: 'company_id' is missing from IoT update data. This is now required.",
                "Error: 'product_name' is missing from IoT update data, cannot locate device update collection.",
                "Error: 'product_version' is missing from IoT update data, cannot locate device update collection."
        };

        for (int i = 0; i < mandatoryFields.length; i++) {
            String field = mandatoryFields[i];
            if (commandData.has(field)) {
                String value = commandData.get(field).getAsString().trim();
                if (value.isEmpty()) {
                    String specificErrMsg = "Error: '" + field + "' cannot be empty.";
                    sendErrorResponse(specificErrMsg, responseJson);
                    logger.warn(specificErrMsg);
                    return false;
                }
                updateDataDocument.append(field, value);
            } else {
                sendErrorResponse(errorMessages[i], responseJson);
                logger.warn(errorMessages[i]);
                return false;
            }
        }
        return true;
    }

    private void appendDynamicFields(JsonObject commandData, Document updateDataDocument) {
        String[] excludedFields = {"command", "iot_id", "company_name", "product_name", "product_version", "company_id"};

        for (Map.Entry<String, JsonElement> entry : commandData.entrySet()) {
            String key = entry.getKey();
            boolean isExcluded = false;
            for (String excludedField : excludedFields) {
                if (key.equals(excludedField)) {
                    isExcluded = true;
                    break;
                }
            }

            if (!isExcluded) {
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive()) {
                    handleJsonPrimitive(key, value.getAsJsonPrimitive(), updateDataDocument);
                } else if (value.isJsonNull()) {
                    updateDataDocument.append(key, null);
                    logger.debug("Appended null value for key: {}", key);
                } else if (value.isJsonArray()) {
                    logger.warn("Skipping JSON Array for key: {} in {} command. Arrays are not supported for direct appending.", key, COMMAND_NAME);
                } else if (value.isJsonObject()) {
                    logger.warn("Skipping nested JSON Object for key: {} in {} command. Nested objects are not supported for direct appending.", key, COMMAND_NAME);
                }
            }
        }
    }

    private void handleJsonPrimitive(String key, JsonPrimitive primitive, Document updateDataDocument) {
        if (primitive.isBoolean()) {
            updateDataDocument.append(key, primitive.getAsBoolean());
            logger.trace("Appended boolean '{}' for key: {}", primitive.getAsBoolean(), key);
        } else if (primitive.isNumber()) {
            try {
                updateDataDocument.append(key, primitive.getAsInt());
                logger.trace("Appended integer '{}' for key: {}", primitive.getAsInt(), key);
            } catch (NumberFormatException e1) {
                try {
                    updateDataDocument.append(key, primitive.getAsLong());
                    logger.trace("Appended long '{}' for key: {}", primitive.getAsLong(), key);
                } catch (NumberFormatException e2) {
                    try {
                        updateDataDocument.append(key, primitive.getAsDouble());
                        logger.trace("Appended double '{}' for key: {}", primitive.getAsDouble(), key);
                    } catch (NumberFormatException e3) {
                        updateDataDocument.append(key, primitive.getAsString());
                        logger.warn("Could not parse number '{}' for key '{}' as int, long, or double in {}. Stored as String.", primitive.getAsString(), key, COMMAND_NAME);
                    }
                }
            }
        } else if (primitive.isString()) {
            updateDataDocument.append(key, primitive.getAsString());
            logger.trace("Appended string '{}' for key: {}", primitive.getAsString(), key);
        } else {
            updateDataDocument.append(key, primitive.getAsString());
            logger.debug("Appended primitive '{}' for key: {} as String (default).", primitive.getAsString(), key);
        }
    }

    private void logIoTDeviceUpdate(Document updateDataDocument, JsonObject responseJson) {
        String iotId = updateDataDocument.getString("iot_id");
        String companyName = updateDataDocument.getString("company_name");
        String compId = updateDataDocument.getString("company_id");
        String prodName = updateDataDocument.getString("product_name");
        String version = updateDataDocument.getString("product_version");

        logger.info("Attempting to log IoT device update for '{}' for company '{}' (ID: '{}'), product '{}' (v'{}').",
                iotId, companyName, compId, prodName, version);

        boolean logged = mongoDBMS.updateIoTDevice(companyName, compId, prodName, version, iotId, updateDataDocument);

        if (logged) {
            logger.info("IoT device '{}' update logged successfully for company '{}' (ID: '{}'), product '{}' (v'{}') in MongoDB!",
                    iotId, companyName, compId, prodName, version);
            responseJson.addProperty("status", "success");
            responseJson.addProperty("command", COMMAND_NAME);

            // add all fields from the document to the JSON response
            for (String key : updateDataDocument.keySet()) {
                if (!key.equals("command")) {
                    Object value = updateDataDocument.get(key);
                    if (value instanceof String) {
                        responseJson.addProperty(key, (String) value);
                    } else if (value instanceof Number) {
                        responseJson.addProperty(key, (Number) value);
                    } else if (value instanceof Boolean) {
                        responseJson.addProperty(key, (Boolean) value);
                    } else if (value instanceof Date) {
                        responseJson.addProperty(key, ((Date) value).toInstant().toString());
                    } else if (value == null) {
                        responseJson.addProperty(key, (String) null);
                    } else {
                        responseJson.addProperty(key, value.toString());
                        logger.warn("Converting non-primitive/non-date type for key '{}' to string in response: {}", key, value.getClass().getName());
                    }
                }
            }
            responseJson.addProperty("message", "Update logged for IoT device " + iotId + "!");
        } else {
            String errorMsg = "Failed to log update for IoT device " + iotId + " for company " + companyName + " (ID: " + compId + "), product " + prodName + " (v" + version + ") in MongoDB. Ensure device and its update collection exist.";
            sendErrorResponse(errorMsg, responseJson);
            logger.error(errorMsg);
        }
    }

    private void sendErrorResponse(String message, JsonObject responseJson) {
        responseJson.addProperty("status", "error");
        responseJson.addProperty("command", COMMAND_NAME);
        responseJson.addProperty("message", message);
        if (!logger.isWarnEnabled()) {
            logger.error(message);
        }
    }
}