package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.task.TaskState;
import cz.krokviak.agents.cli.task.TaskManager;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.ExecutableTool;
import cz.krokviak.agents.tool.ToolArgs;
import cz.krokviak.agents.tool.ToolDefinition;
import cz.krokviak.agents.tool.ToolOutput;

import java.util.List;
import java.util.Map;

public class TaskGetTool implements ExecutableTool {
    private final TaskManager taskManager;
    private final ToolDefinition toolDefinition;

    public TaskGetTool(TaskManager taskManager) {
        this.taskManager = taskManager;
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

        TaskState task = taskManager.get(taskId);
        if (task == null) return ToolOutput.text("Error: task not found: " + taskId);

        StringBuilder sb = new StringBuilder();
        sb.append("Task: ").append(task.id()).append("\n");
        sb.append("Description: ").append(task.description()).append("\n");
        sb.append("Status: ").append(task.status()).append("\n");
        sb.append("Duration: ").append(task.formatDuration()).append("\n");
        if (task.result() != null) sb.append("Result: ").append(task.result()).append("\n");
        if (task.error() != null) sb.append("Error: ").append(task.error()).append("\n");
        return ToolOutput.text(sb.toString());
    }
}
