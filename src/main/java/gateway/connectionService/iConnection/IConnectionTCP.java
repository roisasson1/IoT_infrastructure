package gateway.connectionService.iConnection;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class IConnectionTCP implements IConnection {
    private final SocketChannel socketChannel;

    public IConnectionTCP(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
    }

    @Override
    public void send(ByteBuffer buffer) throws IOException {
        socketChannel.write(buffer);
    }

    @Override
    public ByteBuffer receive() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        try {
            int readBytes = socketChannel.read(buffer);
            if (readBytes == -1) {
                // client disconnected
                System.out.println("Client disconnected: " + socketChannel.getRemoteAddress());
                socketChannel.close();
                return null;
            }

            // No data read
            if (readBytes == 0) {
                return null;
            }

            buffer.flip();
            return buffer;
        } catch (IOException e) {
            System.err.println("Error receiving data: " + e.getMessage());
            socketChannel.close();
            return null;
        }
    }
}
