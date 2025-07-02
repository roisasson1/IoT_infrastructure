import gateway.Gateway;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MainTest {
    @Test
    public void testGatewayCreation() throws IOException {
        int port = 5005;
        String host = "localhost";
        Gateway gateway = new Gateway(port, host);
        assertNotNull(gateway, "Gateway object should not be null after creation.");
    }
}