package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.agent.AgentRegistry;
import cz.krokviak.agents.cli.agent.AgentSpawner;
import cz.krokviak.agents.cli.agent.ProgressTracker;
import cz.krokviak.agents.cli.agent.RunningAgent;
import cz.krokviak.agents.cli.task.TaskManager;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.model.*;
import cz.krokviak.agents.tool.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AgentTool implements ExecutableTool {
    private final CliContext ctx;
    private final List<ExecutableTool> tools;
    private final TaskManager taskManager;
    private final AgentRegistry agentRegistry;
    private final AgentSpawner spawner;
    private final ToolDefinition toolDefinition;
    private final AtomicInteger nameCounter = new AtomicInteger(0);

    public AgentTool(CliContext ctx, List<ExecutableTool> tools, TaskManager taskManager,
                     AgentRegistry agentRegistry) {
        this.ctx = ctx;
        this.tools = tools;
        this.taskManager = taskManager;
        this.agentRegistry = agentRegistry;
        this.spawner = new AgentSpawner(ctx, agentRegistry, taskManager);
        this.toolDefinition = new ToolDefinition("agent",
            "Launch a sub-agent to handle a task. By default runs in FOREGROUND (blocking) — " +
                "the agent executes and its result is returned directly to you. " +
                "Use this when you need the agent's output to continue your work. " +
                "Only set run_in_background=true for truly independent tasks where you don't need the result immediately.",
            Map.of("type", "object", "properties", Map.of(
                "prompt", Map.of("type", "string", "description", "The task for the sub-agent"),
                "description", Map.of("type", "string", "description", "Short 3-5 word description"),
                "run_in_background", Map.of("type", "boolean", "description",
                    "If true, run async and get notified when done. Default false (blocking — result returned directly)."),
                "name", Map.of("type", "string", "description", "Optional agent name for addressing later"),
                "model", Map.of("type", "string", "description", "Model override"),
                "max_turns", Map.of("type", "integer", "description", "Max conversation turns (default 15, max 100)")
            ), "required", List.of("prompt", "description")));
    }

    @Override public String name() { return "agent"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx2) throws Exception {
        String prompt = args.get("prompt", String.class);
        String desc = args.getOrDefault("description", String.class, "sub-agent");
        Boolean bg = args.getOrDefault("run_in_background", Boolean.class, false);
        String nameParam = args.getOrDefault("name", String.class, null);
        String modelOverride = args.get("model", String.class);
        Integer maxTurnsParam = args.getOrDefault("max_turns", Integer.class, null);
        int maxTurns = Math.min(maxTurnsParam != null ? maxTurnsParam : 15, 100);
        if (prompt == null || prompt.isBlank()) return ToolOutput.text("Error: prompt required");

        // Resolve agent name
        String agentName = (nameParam != null && !nameParam.isBlank())
            ? nameParam
            : "agent-" + nameCounter.incrementAndGet();

        Model model = ctx.model();
        if (modelOverride != null && !modelOverride.isBlank()) {
            model = new cz.krokviak.agents.model.AnthropicModel(ctx.apiKey(), ctx.baseUrl(), modelOverride);
        }

        ProgressTracker progress = new ProgressTracker();

        if (Boolean.TRUE.equals(bg)) {
            RunningAgent agent = spawner.spawnBackground(agentName, prompt, tools, model, progress, maxTurns);
            return ToolOutput.text("Background agent started: " + agentName + " (" + desc + ")\nUse /tasks to check status.");
        } else {
            String output = spawner.spawnForeground(agentName, prompt, tools, model, progress, maxTurns);
            return ToolOutput.text(output);
        }
    }
}
