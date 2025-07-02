package gateway.connectionService.iConnection;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface IConnection {
    void send(ByteBuffer buffer) throws IOException;
    ByteBuffer receive() throws IOException;
}