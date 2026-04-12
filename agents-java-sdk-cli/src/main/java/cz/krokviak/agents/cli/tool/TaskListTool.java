package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.api.AgentService;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.util.List;
import java.util.Map;

public class TaskListTool implements ExecutableTool {
    private final AgentService agent;
    private final ToolDefinition toolDefinition;

    public TaskListTool(AgentService agent) {
        this.agent = agent;
        this.toolDefinition = new ToolDefinition("task_list",
            "List all tasks with their status.",
            Map.of("type", "object", "properties", Map.of(
                "status", Map.of("type", "string", "description", "Optional status filter")
            ), "required", List.of()));
    }

    @Override public String name() { return "task_list"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) {
        String statusFilter = args.get("status", String.class);
        var tasks = agent.listTasks();
        if (statusFilter != null && !statusFilter.isBlank()) {
            tasks = tasks.stream()
                .filter(t -> t.status().equalsIgnoreCase(statusFilter))
                .toList();
        }
        if (tasks.isEmpty()) return ToolOutput.text("No tasks.");
        StringBuilder sb = new StringBuilder("Tasks:\n");
        for (var t : tasks) {
            sb.append("  ").append(t.id()).append(" [").append(t.status()).append("] ")
                .append(t.description()).append("\n");
            if (t.summary() != null) sb.append("    ").append(truncate(t.summary(), 200)).append("\n");
        }
        return ToolOutput.text(sb.toString());
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
