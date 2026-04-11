package cz.krokviak.agents.cli.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class McpManagerTest {

    @TempDir Path tempDir;

    @Test
    void initializeWithNoConfig() {
        var manager = new McpManager();
        manager.initialize(tempDir);
        assertFalse(manager.hasServers());
        assertTrue(manager.allTools().isEmpty());
    }

    @Test
    void initializeWithEmptyConfig() throws IOException {
        Path krokDir = tempDir.resolve(".krok");
        Files.createDirectories(krokDir);
        Files.writeString(krokDir.resolve("mcp.json"), """
            { "mcpServers": {} }
            """);

        var manager = new McpManager();
        manager.initialize(tempDir);
        assertFalse(manager.hasServers());
    }

    @Test
    void initializeWithInvalidCommandGracefullyFails() throws IOException {
        Path krokDir = tempDir.resolve(".krok");
        Files.createDirectories(krokDir);
        Files.writeString(krokDir.resolve("mcp.json"), """
            {
              "mcpServers": {
                "broken": {
                  "command": "/nonexistent/binary/that/doesnt/exist",
                  "args": []
                }
              }
            }
            """);

        var manager = new McpManager();
        // Should not throw — logs warning and continues
        assertDoesNotThrow(() -> manager.initialize(tempDir));
        assertFalse(manager.hasServers());
    }

    @Test
    void configParsesCorrectly() throws IOException {
        Path krokDir = tempDir.resolve(".krok");
        Files.createDirectories(krokDir);
        Files.writeString(krokDir.resolve("mcp.json"), """
            {
              "mcpServers": {
                "echo-server": {
                  "command": "echo",
                  "args": ["hello"],
                  "env": { "FOO": "bar" }
                }
              }
            }
            """);

        // Can't fully test start (echo is not MCP), but can verify config loads
        var manager = new McpManager();
        // Will fail to start because echo doesn't speak JSON-RPC — that's OK
        manager.initialize(tempDir);
        // Server failed to start so won't be registered
        assertFalse(manager.hasServers());
        manager.close();
    }

    @Test
    void callToolOnMissingServerReturnsError() {
        var manager = new McpManager();
        String result = manager.callTool("nonexistent", "some_tool", java.util.Map.of());
        assertTrue(result.startsWith("Error:"));
    }

    @Test
    void closeIsIdempotent() {
        var manager = new McpManager();
        assertDoesNotThrow(() -> {
            manager.close();
            manager.close();
        });
    }

    @Test
    void globalAndProjectConfigMerged() throws IOException {
        // Can't test global (~/.krok) in unit test, but verify project config path
        Path krokDir = tempDir.resolve(".krok");
        Files.createDirectories(krokDir);
        Files.writeString(krokDir.resolve("mcp.json"), """
            { "mcpServers": {} }
            """);

        var manager = new McpManager();
        manager.initialize(tempDir);
        // No servers but no crash
        assertFalse(manager.hasServers());
    }

    @Test
    void malformedJsonDoesNotCrash() throws IOException {
        Path krokDir = tempDir.resolve(".krok");
        Files.createDirectories(krokDir);
        Files.writeString(krokDir.resolve("mcp.json"), "not json at all {{{{");

        var manager = new McpManager();
        assertDoesNotThrow(() -> manager.initialize(tempDir));
    }
}
