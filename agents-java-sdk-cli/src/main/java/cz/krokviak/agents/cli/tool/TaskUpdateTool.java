package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.task.TaskManager;
import cz.krokviak.agents.cli.task.TaskState;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.util.List;
import java.util.Map;

public class TaskUpdateTool implements ExecutableTool {
    private final TaskManager taskManager;
    private final ToolDefinition toolDefinition;

    public TaskUpdateTool(TaskManager taskManager) {
        this.taskManager = taskManager;
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

        TaskState task = taskManager.get(taskId);
        if (task == null) return ToolOutput.text("Error: task not found: " + taskId);

        switch (status.toLowerCase()) {
            case "pending" -> {
                task.setPending();
                if (summary != null && !summary.isBlank()) task.setResult(summary);
            }
            case "running", "in_progress" -> {
                task.start();
                if (summary != null && !summary.isBlank()) task.setResult(summary);
            }
            case "completed" -> task.complete(summary != null && !summary.isBlank() ? summary : "Completed");
            case "failed" -> task.fail(summary != null && !summary.isBlank() ? summary : "Marked as failed");
            case "killed", "stopped" -> task.kill();
            default -> {
                return ToolOutput.text("Error: unsupported status: " + status);
            }
        }
        return ToolOutput.text("Task " + taskId + " updated to " + status);
    }
}
