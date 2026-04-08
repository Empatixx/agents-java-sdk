package cz.krokviak.agents.cli.command.builtin;
import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.cli.engine.ToolDispatcher;
import cz.krokviak.agents.cli.tool.ToolClassifier;
public class ToolsCommand implements Command {
    private final ToolDispatcher toolDispatcher;
    public ToolsCommand(ToolDispatcher toolDispatcher) { this.toolDispatcher = toolDispatcher; }
    @Override public String name() { return "tools"; }
    @Override public String description() { return "List available tools"; }
    @Override public void execute(String args, CliContext ctx) {
        ctx.output().println("Available tools:");
        for (var tool : toolDispatcher.all()) {
            String ro = ToolClassifier.isReadOnly(tool.name()) ? " (read-only)" : "";
            ctx.output().println("  " + tool.name() + ro + " — " + tool.description());
        }
    }
}
