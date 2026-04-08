package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.cron.CronScheduler;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.util.List;
import java.util.Map;

public class CronDeleteTool implements ExecutableTool {
    private final CronScheduler cronScheduler;
    private final ToolDefinition toolDefinition;

    public CronDeleteTool(CronScheduler cronScheduler) {
        this.cronScheduler = cronScheduler;
        this.toolDefinition = new ToolDefinition("cron_delete",
            "Delete/cancel a scheduled cron job by its ID.",
            Map.of("type", "object", "properties", Map.of(
                "id", Map.of("type", "string", "description", "The cron job ID to delete")
            ), "required", List.of("id")));
    }

    @Override public String name() { return "cron_delete"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) {
        String id = args.get("id", String.class);
        if (id == null || id.isBlank()) return ToolOutput.text("Error: id required");

        boolean cancelled = cronScheduler.cancel(id);
        if (cancelled) {
            return ToolOutput.text("Cron job deleted: " + id);
        } else {
            return ToolOutput.text("Error: cron job not found: " + id);
        }
    }
}
