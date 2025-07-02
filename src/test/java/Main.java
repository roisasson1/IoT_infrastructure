import gateway.Gateway;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        int port = 5005;
        String host = "localhost";
        new Gateway(port, host);
    }
}
