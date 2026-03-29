package cz.krokviak.agents.mcp;

import java.util.List;

/**
 * Configuration for MCP server integration.
 *
 * @param toolFilters only expose these tool names (null = all tools allowed)
 * @param errorHandling "raise" to propagate errors, "ignore" to swallow them
 */
public record MCPConfig(
    List<String> toolFilters,
    String errorHandling
) {

    /**
     * Returns a default configuration that allows all tools and raises on errors.
     */
    public static MCPConfig defaults() {
        return new MCPConfig(null, "raise");
    }

    /**
     * Checks whether a tool with the given name is allowed by the filter.
     */
    public boolean isToolAllowed(String toolName) {
        return toolFilters == null || toolFilters.contains(toolName);
    }
}
