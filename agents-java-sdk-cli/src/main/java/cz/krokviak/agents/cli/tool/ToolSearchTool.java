package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.engine.DeferredTool;
import cz.krokviak.agents.agent.engine.ToolDispatcher;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;
import java.util.*;

public class ToolSearchTool implements ExecutableTool {
    private final ToolDispatcher toolDispatcher;
    private final ToolDefinition toolDefinition;

    public ToolSearchTool(ToolDispatcher toolDispatcher) {
        this.toolDispatcher = toolDispatcher;
        this.toolDefinition = new ToolDefinition("tool_search",
            "Search for available tools by keyword. Returns matching tool names, descriptions, and parameter schemas.",
            Map.of("type", "object",
                "properties", Map.of(
                    "query", Map.of("type", "string", "description", "Keywords to search for"),
                    "max_results", Map.of("type", "integer", "description", "Max results (default 5)")
                ), "required", List.of("query")));
    }

    @Override public String name() { return "tool_search"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) {
        String query = args.get("query", String.class);
        int maxResults = 5;
        Object maxObj = args.raw().get("max_results");
        if (maxObj instanceof Number n) maxResults = n.intValue();
        if (query == null || query.isBlank()) return ToolOutput.text("Error: query is required");

        String[] keywords = query.toLowerCase().split("\\s+");
        record Match(ExecutableTool tool, int score) {}
        List<Match> matches = new ArrayList<>();

        for (ExecutableTool tool : toolDispatcher.all()) {
            int score = 0;
            String nameLC = tool.name().toLowerCase(), descLC = tool.description().toLowerCase();
            String hint = (tool instanceof DeferredTool dt) ? dt.searchHint().toLowerCase() : "";
            for (String kw : keywords) {
                if (nameLC.contains(kw)) score += 3;
                if (descLC.contains(kw)) score += 1;
                if (hint.contains(kw)) score += 2;
            }
            if (score > 0) {
                if (tool instanceof DeferredTool dt && !dt.isLoaded()) dt.load();
                matches.add(new Match(tool, score));
            }
        }
        matches.sort(Comparator.comparingInt(Match::score).reversed());

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(Math.min(matches.size(), maxResults)).append(" tools:\n\n");
        int count = 0;
        for (Match m : matches) {
            if (count++ >= maxResults) break;
            sb.append("## ").append(m.tool().name()).append("\n");
            sb.append(m.tool().description()).append("\n");
            sb.append("Parameters: ").append(m.tool().definition().parametersSchema()).append("\n\n");
        }
        if (matches.isEmpty()) {
            sb.append("No tools matched '").append(query).append("'.\nAvailable: ");
            toolDispatcher.all().forEach(t -> sb.append(t.name()).append(", "));
        }
        return ToolOutput.text(sb.toString());
    }
}
