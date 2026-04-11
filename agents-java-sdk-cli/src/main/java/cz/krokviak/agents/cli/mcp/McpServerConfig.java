package cz.krokviak.agents.cli.mcp;

import java.util.List;
import java.util.Map;

/**
 * Configuration for a single MCP server from .krok/mcp.json.
 *
 * <pre>
 * {
 *   "mcpServers": {
 *     "github": {
 *       "command": "npx",
 *       "args": ["-y", "@modelcontextprotocol/server-github"],
 *       "env": { "GITHUB_TOKEN": "..." }
 *     }
 *   }
 * }
 * </pre>
 */
public record McpServerConfig(
    String name,
    String command,
    List<String> args,
    Map<String, String> env
) {
    public McpServerConfig {
        if (command == null || command.isBlank()) throw new IllegalArgumentException("MCP server command required");
        if (args == null) args = List.of();
        if (env == null) env = Map.of();
    }
}
