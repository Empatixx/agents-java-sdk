package cz.krokviak.agents.cli.command.builtin;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.model.ModelSettings;
import cz.krokviak.agents.model.ThinkingConfig;

/**
 * {@code /thinking} — toggle Anthropic extended thinking for subsequent turns.
 *
 * <ul>
 *   <li>{@code /thinking} — show current state</li>
 *   <li>{@code /thinking on [budget]} — enable (default budget 4096 tokens)</li>
 *   <li>{@code /thinking off} — disable</li>
 * </ul>
 *
 * Requires a model that supports the {@code thinking} API block
 * (claude-opus-4, claude-sonnet-4+); other models will reject the request.
 */
public class ThinkingCommand implements Command {
    @Override public String name() { return "thinking"; }
    @Override public String description() { return "Toggle extended thinking mode (Claude 4+ only)"; }

    @Override
    public void execute(String args, CliContext ctx) {
        ModelSettings s = ctx.modelSettings();
        ThinkingConfig current = s == null ? null : s.thinking();

        if (args == null || args.isBlank()) {
            if (current == null || !current.enabled()) {
                ctx.output().println("Thinking: \033[2mdisabled\033[0m");
            } else {
                ctx.output().println("Thinking: \033[32menabled\033[0m (budget " + current.budgetTokens() + " tokens)");
            }
            return;
        }

        String[] parts = args.trim().split("\\s+");
        String mode = parts[0].toLowerCase();

        ThinkingConfig next;
        switch (mode) {
            case "on", "enable", "enabled" -> {
                int budget = ThinkingConfig.DEFAULT_BUDGET;
                if (parts.length > 1) {
                    try { budget = Integer.parseInt(parts[1]); }
                    catch (NumberFormatException e) {
                        ctx.output().printError("Budget must be a number of tokens.");
                        return;
                    }
                }
                next = ThinkingConfig.on(budget);
                ctx.output().println("Thinking: \033[32menabled\033[0m (budget " + budget + " tokens)");
            }
            case "off", "disable", "disabled" -> {
                next = ThinkingConfig.off();
                ctx.output().println("Thinking: \033[2mdisabled\033[0m");
            }
            default -> {
                ctx.output().printError("Usage: /thinking [on [budget] | off]");
                return;
            }
        }

        ModelSettings updated = ModelSettings.builder()
            .temperature(orDefault(s == null ? null : s.temperature(), 0.0))
            .topP(orDefault(s == null ? null : s.topP(), 0.0))
            .maxTokens(s == null || s.maxTokens() == null ? 16_384 : s.maxTokens())
            .thinking(next)
            .build();
        ctx.setModelSettings(updated);
    }

    private double orDefault(Double v, double fallback) { return v == null ? fallback : v; }
}
