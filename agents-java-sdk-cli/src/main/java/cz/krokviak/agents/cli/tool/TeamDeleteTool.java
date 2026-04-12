package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.agent.spawn.TeamManager;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.util.List;
import java.util.Map;

public class TeamDeleteTool implements ExecutableTool {
    private final TeamManager teamManager;
    private final ToolDefinition toolDefinition;

    public TeamDeleteTool(TeamManager teamManager) {
        this.teamManager = teamManager;
        this.toolDefinition = new ToolDefinition("team_delete",
            "Delete a named team and release its resources.",
            Map.of("type", "object", "properties", Map.of(
                "name", Map.of("type", "string", "description", "Team name to delete")
            ), "required", List.of("name")));
    }

    @Override public String name() { return "team_delete"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) {
        String name = args.get("name", String.class);
        if (name == null || name.isBlank()) return ToolOutput.text("Error: name required");
        boolean deleted = teamManager.deleteTeam(name);
        return deleted
            ? ToolOutput.text("Team deleted: " + name)
            : ToolOutput.text("Error: team not found: " + name);
    }
}
