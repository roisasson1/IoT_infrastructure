package httpServer;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

public class GenericHttpServer {
    private final HttpServer httpServer;
    private final InetSocketAddress address;

    public GenericHttpServer(String ip, int port) {
        try {
            address = new InetSocketAddress(ip, port);
            httpServer = HttpServer.create(new InetSocketAddress(ip, port), 0);
        } catch (IOException e) {
            throw new RuntimeException("HTTP Server create failed");
        }
    }

    public void addRoute(String url, Map<Method, Pair> callbacks) {
        httpServer.createContext(url, new HttpRequestHandler(callbacks));
        System.out.println("Route added: " + url + " with methods: " + callbacks.keySet());
    }

    public void start() {
        httpServer.start();
        System.out.println("HTTP Server started at http://" +
                address.getHostName() + ":" + address.getPort());
    }
}
