package gateway.connectionService.server;

import com.google.gson.JsonObject;
import gateway.connectionService.iConnection.IConnection;
import gateway.connectionService.iConnection.IConnectionTCP;
import gateway.connectionService.iConnection.IConnectionUDP;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

public class GenericServer {
    private final Handler handler;
    private final Selector selector;
    private final Map<Integer, String> tcpPortsToIps;
    private final Map<Integer, String> udpPortsToIps;

    public GenericServer(Handler handler) throws IOException {
        this.handler = handler;
        selector = Selector.open();
        tcpPortsToIps = new HashMap<>();
        udpPortsToIps = new HashMap<>();
    }

    public void registerTCP(int port, String ip) {
        tcpPortsToIps.put(port, ip);
        System.out.println("TCP port " + port + " registered");
    }

    public void registerUDP(int port, String ip) {
        udpPortsToIps.put(port, ip);
        System.out.println("UDP port " + port + " registered");
    }

    @SuppressWarnings("unchecked")
    public void start() {
        startTcpListeners();
        startUdpListeners();
        System.out.println("Server started, waiting for connections...");

        while (true) {
            try {
                if (selector.select() > 0) {
                    Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                    while (iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        iterator.remove();
                        if (key.isValid() && key.attachment() != null) {
                            ((Consumer<SelectionKey>) key.attachment()).accept(key);
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Error in server loop: " + e.getMessage());
            }
        }
    }

    private void startTcpListeners() {
        for (Map.Entry<Integer, String> entry : tcpPortsToIps.entrySet()) {
            int port = entry.getKey();
            String ip = entry.getValue();

            try {
                ServerSocketChannel serverChannel = ServerSocketChannel.open();
                serverChannel.bind(new InetSocketAddress(ip, port));
                serverChannel.configureBlocking(false);
                SelectionKey key = serverChannel.register(selector, SelectionKey.OP_ACCEPT);
                key.attach(new AcceptHandler());
                System.out.println("TCP server started on " + ip + ":" + port);
            } catch (IOException e) {
                System.err.println("Failed to start TCP server on " + ip + ":" + port + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
        }
    }

    private void startUdpListeners() {
        for (Map.Entry<Integer, String> entry : udpPortsToIps.entrySet()) {
            int port = entry.getKey();
            String ip = entry.getValue();

            try {
                DatagramChannel datagramChannel = DatagramChannel.open();
                datagramChannel.bind(new InetSocketAddress(ip, port));
                datagramChannel.configureBlocking(false);
                SelectionKey key = datagramChannel.register(selector, SelectionKey.OP_READ);
                key.attach(new ReadHandler(new IConnectionUDP(datagramChannel)));
                System.out.println("UDP server running on port " + port + "...");
            } catch (IOException e) {
                System.err.println("Failed to start UDP listener on port " + port + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
        }
    }

    private class AcceptHandler implements Consumer<SelectionKey> {
        @Override
        public void accept(SelectionKey key) {
            try {
                ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
                SocketChannel socketChannel = serverChannel.accept();
                if (socketChannel != null) {
                    socketChannel.configureBlocking(false);
                    IConnection connection = new IConnectionTCP(socketChannel);
                    SelectionKey clientKey = socketChannel.register(selector, SelectionKey.OP_READ);
                    clientKey.attach(new ReadHandler(connection));

                    if (handler.onAccept(connection)) {
                        System.out.println("Connection accepted: " + socketChannel.getRemoteAddress());
                    } else {
                        System.out.println("Connection rejected: " + socketChannel.getRemoteAddress());
                        socketChannel.close();
                    }
                }
            } catch (IOException e) {
                System.err.println("Error handling accept: " + e.getMessage());
                key.cancel(); // remove key from selector
                try {
                    key.channel().close(); // close socket channel
                } catch (IOException closeErr) {
                    System.err.println("Error closing channel: " + closeErr.getMessage());
                }
            }
        }
    }

    private class ReadHandler implements Consumer<SelectionKey> {
        private final IConnection connection;

        public ReadHandler(IConnection connection) {
            this.connection = connection;
        }

        @Override
        public void accept(SelectionKey key) {
            try {
                JsonObject msg = handler.onReceive(connection);
                if (msg != null) {
                    handler.send(connection, msg);
                }
            } catch (IOException e) {
                System.err.println("Error handling read: " + e.getMessage());
                key.cancel(); // remove key from selector
                try {
                    key.channel().close(); // close channel
                } catch (IOException closeErr) {
                    System.err.println("Error closing channel: " + closeErr.getMessage());
                }
            }
        }
    }
}