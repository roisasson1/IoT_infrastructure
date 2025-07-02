package gateway.connectionService.iConnection;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

public class IConnectionUDP implements IConnection {
    private final DatagramChannel datagramChannel;
    private InetSocketAddress clientAddress;

    public IConnectionUDP(DatagramChannel datagramChannel) {
        this.datagramChannel = datagramChannel;
    }

    @Override
    public void send(ByteBuffer buffer) throws IOException {
        if (clientAddress != null) {
            datagramChannel.send(buffer, clientAddress);
        } else {
            System.err.println("IConnectionUDP: Cannot send response - client address unknown");
        }
    }

    @Override
    public ByteBuffer receive() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        buffer.clear();
        clientAddress = (InetSocketAddress)datagramChannel.receive(buffer);
        if (clientAddress == null) {
            return null;
        }

        buffer.flip();
        String message = new String(buffer.array(), 0, buffer.limit());
        System.out.println("IConnectionUDP: Received from client: " + message);

        return buffer;
    }
}