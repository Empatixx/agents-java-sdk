package cz.krokviak.agents.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelSettingsTest {

    @Test
    void builderCreatesSettings() {
        var settings = ModelSettings.builder()
            .temperature(0.7)
            .topP(0.9)
            .maxTokens(1000)
            .build();

        assertEquals(0.7, settings.temperature());
        assertEquals(0.9, settings.topP());
        assertEquals(1000, settings.maxTokens());
    }

    @Test
    void nullFieldsAllowed() {
        var settings = ModelSettings.builder().build();
        assertNull(settings.temperature());
        assertNull(settings.maxTokens());
    }
}
