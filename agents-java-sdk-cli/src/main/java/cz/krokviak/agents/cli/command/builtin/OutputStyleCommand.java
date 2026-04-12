package cz.krokviak.agents.cli.command.builtin;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.cli.style.OutputStyle;
import cz.krokviak.agents.cli.style.OutputStyleRegistry;

/**
 * {@code /output-style} — list available styles, activate one, or clear.
 *
 * <ul>
 *   <li>{@code /output-style} — list loaded styles + current active</li>
 *   <li>{@code /output-style <name>} — activate style, inject its prompt</li>
 *   <li>{@code /output-style default} (or {@code none}/{@code off}) — clear</li>
 * </ul>
 */
public class OutputStyleCommand implements Command {
    @Override public String name() { return "output-style"; }
    @Override public String description() { return "List or switch output styles (alternate system prompts from ~/.claude/output-styles/)"; }

    @Override
    public void execute(String args, CliContext ctx) {
        OutputStyleRegistry reg = ctx.outputStyles();
        if (reg == null) {
            ctx.output().printError("Output styles not initialised.");
            return;
        }

        if (args == null || args.isBlank()) {
            listStyles(ctx, reg);
            return;
        }

        String arg = args.trim();
        if (arg.equalsIgnoreCase("default") || arg.equalsIgnoreCase("none") || arg.equalsIgnoreCase("off")) {
            reg.clear();
            ctx.setSystemPromptSuffix(null);
            ctx.output().println("Output style: default (cleared).");
            return;
        }

        OutputStyle style = reg.find(arg).orElse(null);
        if (style == null) {
            ctx.output().printError("No output style named '" + arg + "'. Try /output-style for the list.");
            return;
        }

        reg.activate(style.name());
        ctx.setSystemPromptSuffix(style.systemPrompt());
        String desc = style.description() == null || style.description().isBlank()
            ? "" : " — " + style.description();
        ctx.output().println("Output style: " + style.name() + desc);
    }

    private void listStyles(CliContext ctx, OutputStyleRegistry reg) {
        var all = reg.all();
        if (all.isEmpty()) {
            ctx.output().println("No output styles installed.");
            ctx.output().println("  Add Markdown files to ~/.claude/output-styles/<name>.md");
            ctx.output().println("  or <project>/.krok/output-styles/<name>.md");
            return;
        }
        String active = reg.activeName();
        ctx.output().println("Output styles:");
        for (OutputStyle s : all) {
            String marker = s.name().equals(active) ? "  \033[32m● " : "    ";
            String desc = s.description() == null || s.description().isBlank()
                ? "" : " — " + s.description();
            ctx.output().println(marker + s.name() + desc + "\033[0m");
        }
        if (active == null) {
            ctx.output().println("  (use /output-style <name> to activate)");
        } else {
            ctx.output().println("  Active: " + active + "  (use /output-style default to clear)");
        }
    }
}
