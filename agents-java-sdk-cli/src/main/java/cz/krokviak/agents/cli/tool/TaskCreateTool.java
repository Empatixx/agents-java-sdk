package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.task.TaskManager;
import cz.krokviak.agents.cli.task.TaskState;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.util.List;
import java.util.Map;

public class TaskCreateTool implements ExecutableTool {
    private final TaskManager taskManager;
    private final ToolDefinition toolDefinition;

    public TaskCreateTool(TaskManager taskManager) {
        this.taskManager = taskManager;
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
        String id = taskManager.nextId();
        TaskState.Status initialStatus = switch (status.toLowerCase()) {
            case "running", "in_progress" -> TaskState.Status.RUNNING;
            case "pending" -> TaskState.Status.PENDING;
            default -> null;
        };
        if (initialStatus == null) {
            return ToolOutput.text("Error: unsupported status: " + status + ". Use pending or running.");
        }
        TaskState task = new TaskState(id, desc, initialStatus);
        taskManager.register(task);
        return ToolOutput.text("Task created: " + id + " — " + desc + " [" + initialStatus + "]");
    }
}
