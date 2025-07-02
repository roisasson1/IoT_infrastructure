package gateway.RPS.parser;

import java.util.Map;
import utils.Pair;

public class StringParser implements Parsable<String, Map.Entry<String, String>> {
    @Override
    public Map.Entry<String, String> parse(String input) {
        if (input == null || !input.contains("@")) {
            throw new IllegalArgumentException("invalid input!");
        }

        String[] request = input.split("@", 3);
        return new Pair<>(request[1], request[2]);
    }
}
