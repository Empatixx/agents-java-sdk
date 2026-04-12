package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.api.AgentService;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.util.List;
import java.util.Map;

public class TaskUpdateTool implements ExecutableTool {
    private final AgentService agent;
    private final ToolDefinition toolDefinition;

    public TaskUpdateTool(AgentService agent) {
        this.agent = agent;
        this.toolDefinition = new ToolDefinition("task_update",
            "Update a task's status or summary.",
            Map.of("type", "object", "properties", Map.of(
                "task_id", Map.of("type", "string", "description", "The task ID"),
                "status", Map.of("type", "string", "description", "New status: pending, running, completed, failed, killed"),
                "summary", Map.of("type", "string", "description", "Optional result or error summary")
            ), "required", List.of("task_id", "status")));
    }

    @Override public String name() { return "task_update"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) {
        String taskId = args.get("task_id", String.class);
        String status = args.get("status", String.class);
        String summary = args.get("summary", String.class);
        if (taskId == null) return ToolOutput.text("Error: task_id required");
        if (status == null) return ToolOutput.text("Error: status required");
        try {
            agent.updateTask(taskId, status, summary);
            return ToolOutput.text("Task " + taskId + " updated to " + status);
        } catch (IllegalArgumentException e) {
            return ToolOutput.text("Error: " + e.getMessage());
        }
    }
}
