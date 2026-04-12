package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.api.AgentService;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.ExecutableTool;
import cz.krokviak.agents.tool.ToolArgs;
import cz.krokviak.agents.tool.ToolDefinition;
import cz.krokviak.agents.tool.ToolOutput;

import java.util.List;
import java.util.Map;

public class TaskStopTool implements ExecutableTool {
    private final AgentService agent;
    private final ToolDefinition toolDefinition;

    public TaskStopTool(AgentService agent) {
        this.agent = agent;
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
        if (agent.getTask(taskId) == null) return ToolOutput.text("Error: task not found: " + taskId);
        agent.stopTask(taskId);
        return ToolOutput.text("Task stopped: " + taskId);
    }
}
