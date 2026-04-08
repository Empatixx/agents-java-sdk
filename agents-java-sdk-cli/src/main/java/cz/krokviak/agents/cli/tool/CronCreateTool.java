package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.cron.CronEntry;
import cz.krokviak.agents.cli.cron.CronScheduler;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.util.List;
import java.util.Map;

public class CronCreateTool implements ExecutableTool {
    private final CronScheduler cronScheduler;
    private final ToolDefinition toolDefinition;

    public CronCreateTool(CronScheduler cronScheduler) {
        this.cronScheduler = cronScheduler;
        this.toolDefinition = new ToolDefinition("cron_create",
            "Create a scheduled cron job that runs a prompt on an interval. Schedule format: 'every 5m', 'every 1h', 'every 30s'.",
            Map.of("type", "object", "properties", Map.of(
                "schedule", Map.of("type", "string", "description", "Interval schedule, e.g. 'every 5m', 'every 1h'"),
                "prompt", Map.of("type", "string", "description", "The prompt to run on each trigger"),
                "agentName", Map.of("type", "string", "description", "Optional agent name to run the prompt with")
            ), "required", List.of("schedule", "prompt")));
    }

    @Override public String name() { return "cron_create"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) {
        String schedule = args.get("schedule", String.class);
        String prompt = args.get("prompt", String.class);
        String agentName = args.getOrDefault("agentName", String.class, null);

        if (schedule == null || schedule.isBlank()) return ToolOutput.text("Error: schedule required");
        if (prompt == null || prompt.isBlank()) return ToolOutput.text("Error: prompt required");

        try {
            CronEntry entry = cronScheduler.schedule(schedule, prompt, agentName);
            return ToolOutput.text("Cron job created: " + entry.id() + " — schedule: " + schedule +
                (agentName != null ? ", agent: " + agentName : ""));
        } catch (IllegalArgumentException e) {
            return ToolOutput.text("Error: " + e.getMessage());
        }
    }
}
