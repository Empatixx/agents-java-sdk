package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.task.TaskManager;
import cz.krokviak.agents.cli.task.TaskState;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.util.List;
import java.util.Map;

public class TaskOutputTool implements ExecutableTool {
    private final TaskManager taskManager;
    private final ToolDefinition toolDefinition;

    public TaskOutputTool(TaskManager taskManager) {
        this.taskManager = taskManager;
        this.toolDefinition = new ToolDefinition("task_output",
            "Get the output, result, and status of a task by its ID.",
            Map.of("type", "object", "properties", Map.of(
                "taskId", Map.of("type", "string", "description", "The task ID to retrieve output for")
            ), "required", List.of("taskId")));
    }

    @Override public String name() { return "task_output"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) {
        String taskId = args.get("taskId", String.class);
        if (taskId == null || taskId.isBlank()) return ToolOutput.text("Error: taskId required");

        TaskState task = taskManager.get(taskId);
        if (task == null) return ToolOutput.text("Error: task not found: " + taskId);

        StringBuilder sb = new StringBuilder();
        sb.append("Task: ").append(task.id()).append("\n");
        sb.append("Description: ").append(task.description()).append("\n");
        sb.append("Status: ").append(task.status()).append("\n");
        sb.append("Duration: ").append(task.formatDuration()).append("\n");

        if (task.result() != null) {
            sb.append("Result:\n").append(task.result()).append("\n");
        }
        if (task.error() != null) {
            sb.append("Error:\n").append(task.error()).append("\n");
        }
        if (task.result() == null && task.error() == null) {
            sb.append("Output: (none yet)\n");
        }
        return ToolOutput.text(sb.toString().trim());
    }
}
