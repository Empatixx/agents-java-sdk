package cz.krokviak.agents.mcp;

/**
 * Exception thrown when an MCP server returns a JSON-RPC error.
 */
public class MCPException extends Exception {
    private final int code;

    public MCPException(int code, String message) {
        super("MCP error " + code + ": " + message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
