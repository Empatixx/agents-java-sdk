package cz.krokviak.agents.cli.command.builtin;
import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.cli.tool.ToolClassifier;
public class ToolsCommand implements Command {
    @Override public String name() { return "tools"; }
    @Override public String description() { return "List available tools"; }
    @Override public void execute(String args, CliContext ctx) {
        ctx.output().println("Available tools:");
        for (var tool : ctx.agent().availableTools()) {
            String ro = ToolClassifier.isReadOnly(tool.name()) ? " (read-only)" : "";
            ctx.output().println("  " + tool.name() + ro + " — " + tool.description());
        }
    }
}
