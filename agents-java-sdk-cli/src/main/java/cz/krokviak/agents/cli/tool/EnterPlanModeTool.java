package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.util.List;
import java.util.Map;

/**
 * Allows the model to enter plan mode — read-only exploration with no edits allowed.
 * The model uses this when it needs to think through an approach before implementing.
 */
public class EnterPlanModeTool implements ExecutableTool {
    private final CliContext ctx;
    private final ToolDefinition toolDefinition;

    public EnterPlanModeTool(CliContext ctx) {
        this.ctx = ctx;
        this.toolDefinition = new ToolDefinition("enter_plan_mode",
            "Enter plan mode. In plan mode, only read-only tools are available. " +
                "Use this when you need to explore and think before making changes. " +
                "Call exit_plan_mode when ready to implement.",
            Map.of("type", "object", "properties", Map.of(
                "reason", Map.of("type", "string", "description", "Why you're entering plan mode")
            ), "required", List.of()));
    }

    @Override public String name() { return "enter_plan_mode"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> toolCtx) {
        String reason = args.get("reason", String.class);
        ctx.setPlanMode(true);
        ctx.output().println("\033[33m📋 Entered plan mode" +
            (reason != null ? ": " + reason : "") + "\033[0m");
        return ToolOutput.text("Plan mode active. Only read-only tools available. Call exit_plan_mode when ready to implement.");
    }
}
