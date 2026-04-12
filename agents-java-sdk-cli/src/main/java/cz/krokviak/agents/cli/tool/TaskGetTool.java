package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.api.AgentService;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.ExecutableTool;
import cz.krokviak.agents.tool.ToolArgs;
import cz.krokviak.agents.tool.ToolDefinition;
import cz.krokviak.agents.tool.ToolOutput;

import java.util.List;
import java.util.Map;

public class TaskGetTool implements ExecutableTool {
    private final AgentService agent;
    private final ToolDefinition toolDefinition;

    public TaskGetTool(AgentService agent) {
        this.agent = agent;
        this.toolDefinition = new ToolDefinition("task_get",
            "Get detailed information about a single task.",
            Map.of("type", "object", "properties", Map.of(
                "task_id", Map.of("type", "string", "description", "The task ID")
            ), "required", List.of("task_id")));
    }

    @Override public String name() { return "task_get"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) {
        String taskId = args.get("task_id", String.class);
        if (taskId == null || taskId.isBlank()) return ToolOutput.text("Error: task_id required");

        var info = agent.getTask(taskId);
        if (info == null) return ToolOutput.text("Error: task not found: " + taskId);

        StringBuilder sb = new StringBuilder();
        sb.append("Task: ").append(info.id()).append("\n");
        sb.append("Description: ").append(info.description()).append("\n");
        sb.append("Status: ").append(info.status()).append("\n");
        if (info.summary() != null) sb.append("Summary: ").append(info.summary()).append("\n");
        return ToolOutput.text(sb.toString());
    }
}
