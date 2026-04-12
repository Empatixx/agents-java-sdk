package cz.krokviak.agents.cli.tips;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TipRegistryTest {

    @Test
    void emptyRegistryReturnsEmpty() {
        var reg = new TipRegistry(List.of());
        assertTrue(reg.pickNext().isEmpty());
        assertTrue(reg.isEmpty());
    }

    @Test
    void pickNextCyclesThroughAllBeforeRepeating() {
        var tips = List.of(
            new Tip("a", "alpha", "x"),
            new Tip("b", "beta", "x"),
            new Tip("c", "gamma", "x")
        );
        var reg = new TipRegistry(tips);

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            seen.add(reg.pickNext().orElseThrow().id());
        }
        assertEquals(3, seen.size(), "all three tips must appear within the first 3 picks");
    }

    @Test
    void pickNextUpdatesLastShownTimestamp() {
        var tips = List.of(new Tip("only", "only tip", "x"));
        var reg = new TipRegistry(tips);
        assertEquals(0L, reg.lastShownOf("only"));
        reg.pickNext();
        assertTrue(reg.lastShownOf("only") > 0L);
    }
}
