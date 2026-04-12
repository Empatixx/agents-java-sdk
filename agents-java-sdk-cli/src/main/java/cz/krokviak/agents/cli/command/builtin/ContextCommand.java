package cz.krokviak.agents.cli.command.builtin;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.cli.context.TokenEstimator;

public class ContextCommand implements Command {
    @Override public String name() { return "context"; }
    @Override public String description() { return "Show context window usage"; }
    @Override public void execute(String args, CliContext ctx) {
        var history = ctx.agent().history();
        int historyTokens = TokenEstimator.estimate(history.items());
        int promptTokens = TokenEstimator.estimate(ctx.systemPrompt());
        int total = historyTokens + promptTokens;
        int messageCount = history.size();

        ctx.output().println("Context usage:");
        ctx.output().println("  System prompt: ~" + String.format("%,d", promptTokens) + " tokens");
        ctx.output().println("  History: ~" + String.format("%,d", historyTokens) + " tokens (" + messageCount + " messages)");
        ctx.output().println("  Total: ~" + String.format("%,d", total) + " tokens");
        ctx.output().println("  Plan mode: " + (ctx.agent().isPlanMode() ? "ON" : "OFF"));

        if (total > 60_000) {
            ctx.output().println("  \033[33m⚠ Context is large — compaction may trigger soon\033[0m");
        }
    }
}
