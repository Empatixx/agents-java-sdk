package cz.krokviak.agents.tool;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ToolArgsTest {

    @Test
    void getReturnsTypedValue() {
        var args = new ToolArgs(Map.of("city", "Prague", "count", 5));
        assertEquals("Prague", args.get("city", String.class));
        assertEquals(5, args.get("count", Integer.class));
    }

    @Test
    void getReturnsNullForMissingKey() {
        var args = new ToolArgs(Map.of());
        assertNull(args.get("missing", String.class));
    }

    @Test
    void getOrDefaultReturnsDefault() {
        var args = new ToolArgs(Map.of());
        assertEquals("default", args.getOrDefault("missing", String.class, "default"));
    }

    @Test
    void rawMapAccessible() {
        var raw = Map.<String, Object>of("key", "val");
        var args = new ToolArgs(raw);
        assertEquals(raw, args.raw());
    }
}
