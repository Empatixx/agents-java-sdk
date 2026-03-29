package cz.krokviak.agents.mcp;

import java.util.Map;

/**
 * JSON-RPC message types for the MCP protocol.
 */
public sealed interface MCPMessage {

    /**
     * A JSON-RPC request message.
     */
    record Request(String id, String method, Map<String, Object> params) implements MCPMessage {}

    /**
     * A JSON-RPC response message.
     */
    record Response(String id, Object result, MCPError error) implements MCPMessage {}

    /**
     * A JSON-RPC notification (no id, no response expected).
     */
    record Notification(String method, Map<String, Object> params) implements MCPMessage {}

    /**
     * An error payload within a JSON-RPC response.
     */
    record MCPError(int code, String message) {}
}
