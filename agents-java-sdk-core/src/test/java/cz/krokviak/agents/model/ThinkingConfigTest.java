package cz.krokviak.agents.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThinkingConfigTest {

    @Test
    void offIsDisabled() {
        var c = ThinkingConfig.off();
        assertFalse(c.enabled());
        assertEquals(0, c.budgetTokens());
    }

    @Test
    void onHasDefaultBudget() {
        var c = ThinkingConfig.on();
        assertTrue(c.enabled());
        assertEquals(ThinkingConfig.DEFAULT_BUDGET, c.budgetTokens());
    }

    @Test
    void onAcceptsCustomBudget() {
        var c = ThinkingConfig.on(12345);
        assertTrue(c.enabled());
        assertEquals(12345, c.budgetTokens());
    }

    @Test
    void builderAttachesThinking() {
        var settings = ModelSettings.builder().thinking(ThinkingConfig.on(8000)).build();
        assertNotNull(settings.thinking());
        assertTrue(settings.thinking().enabled());
        assertEquals(8000, settings.thinking().budgetTokens());
    }
}
