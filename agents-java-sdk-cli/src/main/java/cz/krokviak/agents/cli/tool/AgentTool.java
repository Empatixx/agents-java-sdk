package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.api.AgentService;
import cz.krokviak.agents.api.dto.SpawnRequest;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Launch a sub-agent via {@link AgentService#spawnAgent(SpawnRequest)}.
 * All direct access to {@code AgentSpawner} / {@code Model} has moved
 * behind the facade — this tool no longer knows how sub-agents run.
 */
public class AgentTool implements ExecutableTool {
    private final AgentService agent;
    private final ToolDefinition toolDefinition;
    private final AtomicInteger nameCounter = new AtomicInteger(0);

    public AgentTool(AgentService agent) {
        this.agent = agent;
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
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) throws Exception {
        String prompt = args.get("prompt", String.class);
        String desc = args.getOrDefault("description", String.class, "sub-agent");
        Boolean bg = args.getOrDefault("run_in_background", Boolean.class, false);
        String nameParam = args.getOrDefault("name", String.class, null);
        String modelOverride = args.get("model", String.class);
        Integer maxTurnsParam = args.getOrDefault("max_turns", Integer.class, null);
        if (prompt == null || prompt.isBlank()) return ToolOutput.text("Error: prompt required");

        String agentName = (nameParam != null && !nameParam.isBlank())
            ? nameParam
            : "agent-" + nameCounter.incrementAndGet();

        boolean background = Boolean.TRUE.equals(bg);
        var req = new SpawnRequest(agentName, prompt, desc, background,
            modelOverride, maxTurnsParam, List.of(), /*isolatedWorktree*/ false);

        if (background) {
            String id = agent.spawnAgent(req).get();
            return ToolOutput.text("Background agent started: " + id + " (" + desc + ")\nUse /tasks to check status.");
        }
        String output = agent.spawnAgent(req).get();
        return ToolOutput.text(output);
    }
}
