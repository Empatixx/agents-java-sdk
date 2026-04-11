package cz.krokviak.agents.cli.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Manages MCP server lifecycle: loads config, starts servers, provides tool proxies.
 *
 * Config locations (merged):
 * - ~/.krok/mcp.json (global)
 * - .krok/mcp.json (project)
 */
public class McpManager implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(McpManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, McpServer> servers = new LinkedHashMap<>();

    /**
     * Load config and start all MCP servers.
     */
    @SuppressWarnings("unchecked")
    public void initialize(Path cwd) {
        List<McpServerConfig> configs = new ArrayList<>();

        // Global config
        configs.addAll(loadConfig(Path.of(System.getProperty("user.home"), ".krok", "mcp.json")));
        // Project config
        configs.addAll(loadConfig(cwd.resolve(".krok").resolve("mcp.json")));

        for (var config : configs) {
            try {
                var server = new McpServer(config);
                server.start();
                servers.put(config.name(), server);
                log.info("MCP server '{}' started with {} tools", config.name(), server.tools().size());
            } catch (Exception e) {
                log.warn("Failed to start MCP server '{}': {}", config.name(), e.getMessage());
            }
        }
    }

    /**
     * Get all tools from all running MCP servers.
     */
    public List<McpTool> allTools() {
        List<McpTool> tools = new ArrayList<>();
        for (var server : servers.values()) {
            tools.addAll(server.tools());
        }
        return tools;
    }

    /**
     * Call a tool by qualified name (mcp__server__tool) or by server+tool name.
     */
    public String callTool(String serverName, String toolName, Map<String, Object> args) {
        var server = servers.get(serverName);
        if (server == null) return "Error: MCP server not found: " + serverName;
        return server.callTool(toolName, args);
    }

    /**
     * Get a specific server.
     */
    public McpServer server(String name) { return servers.get(name); }

    public Collection<McpServer> servers() { return servers.values(); }
    public boolean hasServers() { return !servers.isEmpty(); }

    @SuppressWarnings("unchecked")
    private List<McpServerConfig> loadConfig(Path configPath) {
        if (!Files.isRegularFile(configPath)) return List.of();

        try {
            var raw = MAPPER.readValue(configPath.toFile(), Map.class);
            var mcpServers = raw.get("mcpServers");
            if (!(mcpServers instanceof Map<?, ?> serversMap)) return List.of();

            List<McpServerConfig> configs = new ArrayList<>();
            for (var entry : ((Map<String, Object>) serversMap).entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> serverDef) {
                    var m = (Map<String, Object>) serverDef;
                    String command = (String) m.get("command");
                    List<String> args = m.get("args") instanceof List<?> list
                        ? list.stream().map(Object::toString).toList() : List.of();
                    Map<String, String> env = new HashMap<>();
                    if (m.get("env") instanceof Map<?, ?> envMap) {
                        envMap.forEach((k, v) -> env.put(k.toString(), v.toString()));
                    }
                    configs.add(new McpServerConfig(entry.getKey(), command, args, env));
                }
            }
            return configs;
        } catch (IOException e) {
            log.warn("Failed to load MCP config from {}: {}", configPath, e.getMessage());
            return List.of();
        }
    }

    @Override
    public void close() {
        for (var server : servers.values()) {
            try { server.close(); } catch (Exception ignored) {}
        }
        servers.clear();
    }
}
