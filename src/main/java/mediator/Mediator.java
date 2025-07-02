package mediator;

import gateway.RPS.RPS;
import gateway.RPS.command.Command;
import gateway.connectionService.request.Request;
import dbms.MongoDBMS;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class Mediator {
    private final RPS<String, ?, ?> rps;
    private final MongoDBMS mongoDBMS;

    public Mediator(RPS<String, ?, ?> rps, MongoDBMS mongoDBMS) {
        this.rps = rps;
        this.mongoDBMS = mongoDBMS;
    }

    public void add(List<Class<?>> commandClasses) {
        for (Class<?> commandClass : commandClasses) {
            String commandName = getCommandNameFromClass(commandClass.getSimpleName());
            this.rps.addCommand(commandName, (request) -> {
                try {
                    try {
                        Constructor<?> constructor = commandClass.getConstructor(Request.class, MongoDBMS.class);
                        return (Command) constructor.newInstance(request, mongoDBMS);
                    } catch (NoSuchMethodException e1) {
                        try {
                            Constructor<?> constructor = commandClass.getConstructor(Request.class);
                            return (Command) constructor.newInstance(request);
                        } catch (NoSuchMethodException e2) {
                            try {
                                Constructor<?> constructor = commandClass.getConstructor();
                                return (Command) constructor.newInstance();
                            } catch (NoSuchMethodException e3) {
                                System.err.println("No suitable constructor found for command class: " + commandClass.getName() +
                                        " expecting (Request, MongoDBMS), (Request) or default.");
                                throw new RuntimeException("No suitable constructor found for command class. " + e3);
                            }
                        }
                    }
                } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
                    System.err.println("Error instantiating command " + commandClass.getName() + ": " + e.getMessage());
                    throw new RuntimeException("Failed to instantiate command: " + commandClass.getName(), e);
                }
            });
        }
    }

    private String getCommandNameFromClass(String className) {
        switch (className) {
            case "RegisterCompany":
                return "Register Company";
            case "RegisterProduct":
                return "Register Product";
            case "RegisterIoT":
                return "Register IoT";
            case "UpdateIoT":
                return "Update IoT";
        }
        return className.replace("Command", "").replaceAll("([A-Z])", " $1").trim();
    }
}