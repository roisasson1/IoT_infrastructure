package httpServer;

import gateway.connectionService.iConnection.IConnection;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class Pair {
    Predicate<IConnection> validator;
    Consumer<IConnection> controller;

    public Pair(Predicate<IConnection> validator, Consumer<IConnection> controller) {
        this.controller = controller;
        this.validator = validator;
    }

    public Predicate<IConnection> getValidator() {
        return validator;
    }

    public Consumer<IConnection> getController() {
        return controller;
    }
}