package cz.krokviak.agents.cli.command.builtin;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.agent.context.TokenEstimator;

public class CompactCommand implements Command {
    @Override public String name() { return "compact"; }
    @Override public String description() { return "Force context compaction"; }
    @Override public void execute(String args, CliContext ctx) {
        int beforeTokens = TokenEstimator.estimate(ctx.agent().history().items());
        int beforeMessages = ctx.agent().history().size();

        ctx.output().startSpinner("Compacting context...");
        try {
            ctx.agent().compactHistory().join();
        } finally {
            ctx.output().stopSpinner();
        }

        int afterTokens = TokenEstimator.estimate(ctx.agent().history().items());
        int afterMessages = ctx.agent().history().size();
        int freedTokens = beforeTokens - afterTokens;

        ctx.output().println(String.format(
            "\033[2m[Compacted: %d messages → %d | ~%dk tokens freed]\033[0m",
            beforeMessages, afterMessages, freedTokens / 1000));
    }
}
