package cz.krokviak.agents.cli.command;

import cz.krokviak.agents.cli.CliContext;
import java.util.List;

public interface Command {
    String name();
    default List<String> aliases() { return List.of(); }
    String description();
    void execute(String args, CliContext ctx);
}
