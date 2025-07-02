package gateway;

import gateway.RPS.RPS;
import gateway.connectionService.ConnectionService;
import mediator.Mediator;
import plugAndPlay.PlugAndPlay;

import java.io.IOException;

public class Gateway {
    public Gateway(int port, String ip) throws IOException {
        RPS<String, ?, ?> rps = new RPS<>();
        ConnectionService cs = new ConnectionService(rps);
        cs.registerTCP(port, ip);
        cs.registerHTTP(port + 1, ip);

        Mediator mediator = new Mediator(rps, rps.getMongoDBMS());
        String watchDirectory = "/home/roi-sasson/iot_plugins";
        new PlugAndPlay(mediator, watchDirectory);

        cs.start();
    }
}