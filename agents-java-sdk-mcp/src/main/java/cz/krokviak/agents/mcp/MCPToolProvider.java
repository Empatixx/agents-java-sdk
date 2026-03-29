package cz.krokviak.agents.mcp;

import cz.krokviak.agents.tool.Tool;
import cz.krokviak.agents.tool.ToolDefinition;
import cz.krokviak.agents.tool.ToolProvider;

import java.util.ArrayList;
import java.util.List;

public class MCPToolProvider implements ToolProvider {
    private final List<MCPServer> servers;
    private final MCPConfig config;

    public MCPToolProvider(List<MCPServer> servers, MCPConfig config) {
        this.servers = List.copyOf(servers);
        this.config = config != null ? config : MCPConfig.defaults();
    }

    public MCPToolProvider(MCPServer... servers) {
        this(List.of(servers), MCPConfig.defaults());
    }

    @Override
    public List<Tool> provideTools() throws Exception {
        List<Tool> tools = new ArrayList<>();
        for (MCPServer server : servers) {
            try {
                server.connect();
                for (ToolDefinition def : server.listTools()) {
                    if (config.isToolAllowed(def.name())) {
                        tools.add(new MCPTool(def, server));
                    }
                }
            } catch (Exception e) {
                if ("raise".equals(config.errorHandling())) throw e;
            }
        }
        return tools;
    }

    @Override
    public void close() {
        for (MCPServer s : servers) {
            try { s.close(); } catch (Exception ignored) {}
        }
    }
}
