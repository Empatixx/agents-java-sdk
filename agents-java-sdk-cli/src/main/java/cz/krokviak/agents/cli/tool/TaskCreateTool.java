package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.api.AgentService;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.util.List;
import java.util.Map;

public class TaskCreateTool implements ExecutableTool {
    private final AgentService agent;
    private final ToolDefinition toolDefinition;

    public TaskCreateTool(AgentService agent) {
        this.agent = agent;
        this.toolDefinition = new ToolDefinition("task_create",
            "Create a task to track work. Returns the task ID.",
            Map.of("type", "object", "properties", Map.of(
                "description", Map.of("type", "string", "description", "Description of the task"),
                "status", Map.of("type", "string", "description", "Initial status: pending or running (default pending)")
            ), "required", List.of("description")));
    }

    @Override public String name() { return "task_create"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) {
        String desc = args.get("description", String.class);
        String status = args.getOrDefault("status", String.class, "pending");
        if (desc == null || desc.isBlank()) return ToolOutput.text("Error: description required");
        try {
            String id = agent.createTask(desc, status);
            return ToolOutput.text("Task created: " + id + " — " + desc + " [" + status + "]");
        } catch (IllegalArgumentException e) {
            return ToolOutput.text("Error: " + e.getMessage());
        }
    }
}
