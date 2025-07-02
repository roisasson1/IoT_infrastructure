package gateway.RPS.parser;

import com.google.gson.JsonObject;
import utils.Pair;

public class JsonCommandParser implements Parsable<JsonObject, Pair<String, JsonObject>> {
    @Override
    public Pair<String, JsonObject> parse(JsonObject jsonInput) {
        if (jsonInput == null) {
            throw new IllegalArgumentException("JsonCommandParser: Input cannot be null");
        }

        if (!jsonInput.has("command") || !jsonInput.has("data")) {
            throw new IllegalArgumentException("JsonCommandParser: JSON must have 'command' and 'data'");
        }

        String commandName;
        JsonObject commandData;
        try {
            commandName = jsonInput.get("command").getAsString();
            commandData = jsonInput.getAsJsonObject("data");
        } catch (Exception e) {
            throw new IllegalArgumentException("JsonCommandParser: Failed to extract 'command' or 'data': " + e.getMessage(), e);
        }

        return new Pair<>(commandName, commandData);
    }
}