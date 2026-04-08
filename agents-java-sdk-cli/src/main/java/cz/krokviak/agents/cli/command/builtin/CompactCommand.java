package cz.krokviak.agents.cli.command.builtin;
import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
public class CompactCommand implements Command {
    @Override public String name() { return "compact"; }
    @Override public String description() { return "Force context compaction"; }
    @Override public void execute(String args, CliContext ctx) {
        if (ctx.compactor() == null) { ctx.output().printError("Context compaction not available."); return; }
        var compacted = ctx.compactor().forceCompact(ctx.history());
        ctx.history().clear();
        ctx.history().addAll(compacted);
        ctx.output().println("Context compacted.");
    }
}
