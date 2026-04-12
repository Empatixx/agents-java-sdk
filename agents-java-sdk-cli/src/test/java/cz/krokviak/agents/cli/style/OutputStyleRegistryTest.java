package cz.krokviak.agents.cli.style;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OutputStyleRegistryTest {

    @Test
    void findAndActivateAndClear() {
        var a = new OutputStyle("a", "Style A", "body A", "src");
        var b = new OutputStyle("b", "Style B", "body B", "src");
        var reg = new OutputStyleRegistry(List.of(a, b));

        assertNull(reg.activeName());
        assertTrue(reg.active().isEmpty());

        reg.activate("a");
        assertEquals("a", reg.activeName());
        assertEquals("body A", reg.active().orElseThrow().systemPrompt());

        reg.clear();
        assertNull(reg.activeName());
        assertTrue(reg.find("missing").isEmpty());
    }
}
