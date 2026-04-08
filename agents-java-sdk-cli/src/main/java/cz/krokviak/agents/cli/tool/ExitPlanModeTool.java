package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.util.List;
import java.util.Map;

public class ExitPlanModeTool implements ExecutableTool {
    private final CliContext ctx;
    private final ToolDefinition toolDefinition;

    public ExitPlanModeTool(CliContext ctx) {
        this.ctx = ctx;
        this.toolDefinition = new ToolDefinition("exit_plan_mode",
            "Exit plan mode and return to normal mode where all tools are available.",
            Map.of("type", "object", "properties", Map.of(), "required", List.of()));
    }

    @Override public String name() { return "exit_plan_mode"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> toolCtx) {
        ctx.setPlanMode(false);
        ctx.output().println("\033[32m✓ Exited plan mode — all tools available\033[0m");
        return ToolOutput.text("Plan mode deactivated. All tools are now available.");
    }
}
