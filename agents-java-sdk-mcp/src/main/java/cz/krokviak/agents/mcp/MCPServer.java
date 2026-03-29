package cz.krokviak.agents.mcp;

import cz.krokviak.agents.tool.ToolDefinition;
import cz.krokviak.agents.tool.ToolOutput;

import java.util.List;
import java.util.Map;

/**
 * Represents a connection to an MCP (Model Context Protocol) server.
 * Implementations handle different transports (stdio, HTTP, etc.).
 */
public interface MCPServer extends AutoCloseable {

    /**
     * Establish a connection to the MCP server and perform the initialize handshake.
     */
    void connect() throws Exception;

    /**
     * List all tools available on this MCP server.
     */
    List<ToolDefinition> listTools() throws Exception;

    /**
     * Call a tool on the MCP server with the given arguments.
     */
    ToolOutput callTool(String toolName, Map<String, Object> arguments) throws Exception;

    /**
     * Close the connection and release resources.
     */
    @Override
    void close();
}
