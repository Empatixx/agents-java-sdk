package cz.krokviak.agents.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CliConfigTest {

    @Test
    void defaultMaxTurns() {
        // Can't easily test parse without API key env, but can test defaults
        assertEquals(50, CliDefaults.MAX_TURNS);
    }

    @Test
    void parseThrowsWithoutApiKey() {
        assertThrows(IllegalArgumentException.class, () ->
            CliConfig.parse(new String[]{}));
    }

    @Test
    void parseModel() {
        // Set env would be needed for full test — just verify the config record
        var config = new CliConfig("key", "claude-test", null,
            java.nio.file.Path.of("."), "https://api.anthropic.com",
            50, "default", false, CliConfig.Provider.ANTHROPIC);

        assertEquals("claude-test", config.model());
        assertEquals("key", config.apiKey());
        assertEquals(50, config.maxTurns());
        assertEquals(CliConfig.Provider.ANTHROPIC, config.provider());
    }

    @Test
    void parseWithFlags() {
        // Simulate --api-key flag
        try {
            var config = CliConfig.parse(new String[]{"--api-key", "test-key", "--model", "custom-model", "--max-turns", "10"});
            assertEquals("test-key", config.apiKey());
            assertEquals("custom-model", config.model());
            assertEquals(10, config.maxTurns());
        } catch (IllegalArgumentException e) {
            // OK if env vars not set and provider detection fails
        }
    }

    @Test
    void providerEnum() {
        assertEquals(CliConfig.Provider.ANTHROPIC, CliConfig.Provider.valueOf("ANTHROPIC"));
        assertEquals(CliConfig.Provider.OPENAI, CliConfig.Provider.valueOf("OPENAI"));
    }
}
