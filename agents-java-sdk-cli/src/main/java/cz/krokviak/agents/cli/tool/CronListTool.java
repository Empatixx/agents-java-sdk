package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.cron.CronEntry;
import cz.krokviak.agents.cli.cron.CronScheduler;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.util.List;
import java.util.Map;

public class CronListTool implements ExecutableTool {
    private final CronScheduler cronScheduler;
    private final ToolDefinition toolDefinition;

    public CronListTool(CronScheduler cronScheduler) {
        this.cronScheduler = cronScheduler;
        this.toolDefinition = new ToolDefinition("cron_list",
            "List all scheduled cron jobs.",
            Map.of("type", "object", "properties", Map.of()));
    }

    @Override public String name() { return "cron_list"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) {
        List<CronEntry> entries = cronScheduler.list();
        if (entries.isEmpty()) {
            return ToolOutput.text("No cron jobs scheduled.");
        }
        StringBuilder sb = new StringBuilder("Cron jobs (" + entries.size() + "):\n");
        for (CronEntry e : entries) {
            sb.append("  ").append(e.id())
                .append(" [").append(e.enabled() ? "enabled" : "disabled").append("]")
                .append(" schedule=").append(e.schedule())
                .append(" prompt=").append(e.prompt());
            if (e.agentName() != null) sb.append(" agent=").append(e.agentName());
            if (e.lastRunAt() != null) sb.append(" lastRun=").append(e.lastRunAt());
            sb.append("\n");
        }
        return ToolOutput.text(sb.toString().trim());
    }
}
