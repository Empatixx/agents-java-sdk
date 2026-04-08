package cz.krokviak.agents.cli.command;

import java.util.*;

public final class Commands {
    private final Map<String, Command> registry = new LinkedHashMap<>();

    public void register(Command command) {
        registry.put(command.name(), command);
        for (String alias : command.aliases()) {
            registry.put(alias, command);
        }
    }

    public Command find(String name) {
        return registry.get(name);
    }

    public List<Command> all() {
        return registry.values().stream().distinct().toList();
    }
}
