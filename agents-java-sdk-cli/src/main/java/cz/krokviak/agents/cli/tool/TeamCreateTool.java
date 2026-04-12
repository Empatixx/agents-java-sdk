package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.agent.spawn.Team;
import cz.krokviak.agents.agent.spawn.TeamManager;
import cz.krokviak.agents.agent.task.TaskManager;
import cz.krokviak.agents.agent.task.TaskState;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.util.List;
import java.util.Map;

public class TeamCreateTool implements ExecutableTool {
    private final TeamManager teamManager;
    private final ToolDefinition toolDefinition;

    public TeamCreateTool(TeamManager teamManager) {
        this.teamManager = teamManager;
        this.toolDefinition = new ToolDefinition("team_create",
            "Create a named team with an optional task list for coordinating multiple agents.",
            Map.of("type", "object", "properties", Map.of(
                "name", Map.of("type", "string", "description", "Unique team name"),
                "tasks", Map.of("type", "array", "items", Map.of("type", "string"),
                    "description", "Optional list of task descriptions to pre-populate the team task manager")
            ), "required", List.of("name")));
    }

    @Override public String name() { return "team_create"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    @SuppressWarnings("unchecked")
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) {
        String name = args.get("name", String.class);
        if (name == null || name.isBlank()) return ToolOutput.text("Error: name required");

        if (teamManager.getTeam(name) != null) {
            return ToolOutput.text("Error: team already exists: " + name);
        }

        Team team = teamManager.createTeam(name);

        // Optionally pre-populate task list
        List<Object> tasks = args.getOrDefault("tasks", List.class, null);
        StringBuilder sb = new StringBuilder("Team created: " + name);
        if (tasks != null && !tasks.isEmpty()) {
            TaskManager tm = team.sharedTaskManager();
            sb.append("\nTasks:");
            for (Object taskObj : tasks) {
                String desc = taskObj.toString();
                String id = tm.nextId();
                tm.register(new TaskState(id, desc, TaskState.Status.PENDING));
                sb.append("\n  ").append(id).append(": ").append(desc);
            }
        }
        return ToolOutput.text(sb.toString());
    }
}
