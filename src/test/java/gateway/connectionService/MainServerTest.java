package gateway.connectionService;

import gateway.RPS.RPS;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class MainServerTest {
    @Test
    void testServerStartupAndTCPRegistration() throws IOException {
        RPS<String, ?, ?> rps = new RPS<>();
        ConnectionService cs = new ConnectionService(rps);
        assertDoesNotThrow(() -> {
            cs.registerTCP(5005, "localhost");
            cs.registerUDP(5006, "localhost");
            cs.start();
        });
    }
}
