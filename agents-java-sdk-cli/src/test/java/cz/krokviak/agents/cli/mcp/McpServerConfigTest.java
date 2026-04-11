package cz.krokviak.agents.cli.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class McpServerConfigTest {

    @Test
    void validConfig() {
        var config = new McpServerConfig("github", "npx",
            List.of("-y", "@modelcontextprotocol/server-github"),
            Map.of("GITHUB_TOKEN", "ghp_xxx"));

        assertEquals("github", config.name());
        assertEquals("npx", config.command());
        assertEquals(2, config.args().size());
        assertEquals("ghp_xxx", config.env().get("GITHUB_TOKEN"));
    }

    @Test
    void nullArgsDefaultsToEmpty() {
        var config = new McpServerConfig("test", "echo", null, null);
        assertTrue(config.args().isEmpty());
        assertTrue(config.env().isEmpty());
    }

    @Test
    void blankCommandThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new McpServerConfig("test", "", List.of(), Map.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new McpServerConfig("test", null, List.of(), Map.of()));
    }
}
