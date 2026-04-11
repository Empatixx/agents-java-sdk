package cz.krokviak.agents.cli.command.builtin;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.cli.context.TokenEstimator;

public class CompactCommand implements Command {
    @Override public String name() { return "compact"; }
    @Override public String description() { return "Force context compaction"; }
    @Override public void execute(String args, CliContext ctx) {
        if (ctx.compactor() == null) { ctx.output().printError("Context compaction not available."); return; }

        int beforeTokens = TokenEstimator.estimate(ctx.history());
        int beforeMessages = ctx.history().size();

        ctx.output().startSpinner("Compacting context...");
        try {
            var compacted = ctx.compactionPipeline().reactiveCompact(ctx.history(), ctx.systemPrompt());
            ctx.history().clear();
            ctx.history().addAll(compacted);
        } finally {
            ctx.output().stopSpinner();
        }

        int afterTokens = TokenEstimator.estimate(ctx.history());
        int afterMessages = ctx.history().size();
        int freedTokens = beforeTokens - afterTokens;
        int freedMessages = beforeMessages - afterMessages;

        ctx.output().println(String.format(
            "\033[2m[Compacted: %d messages → %d | ~%dk tokens freed]\033[0m",
            beforeMessages, afterMessages, freedTokens / 1000));
    }
}
