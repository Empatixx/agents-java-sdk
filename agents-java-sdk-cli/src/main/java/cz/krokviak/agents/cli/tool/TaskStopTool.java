package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.task.TaskManager;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.ExecutableTool;
import cz.krokviak.agents.tool.ToolArgs;
import cz.krokviak.agents.tool.ToolDefinition;
import cz.krokviak.agents.tool.ToolOutput;

import java.util.List;
import java.util.Map;

public class TaskStopTool implements ExecutableTool {
    private final TaskManager taskManager;
    private final ToolDefinition toolDefinition;

    public TaskStopTool(TaskManager taskManager) {
        this.taskManager = taskManager;
        this.toolDefinition = new ToolDefinition("task_stop",
            "Stop a running or pending task.",
            Map.of("type", "object", "properties", Map.of(
                "task_id", Map.of("type", "string", "description", "The task ID")
            ), "required", List.of("task_id")));
    }

    @Override public String name() { return "task_stop"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) {
        String taskId = args.get("task_id", String.class);
        if (taskId == null || taskId.isBlank()) return ToolOutput.text("Error: task_id required");

        if (taskManager.get(taskId) == null) return ToolOutput.text("Error: task not found: " + taskId);
        taskManager.killTask(taskId);
        return ToolOutput.text("Task stopped: " + taskId);
    }
}
