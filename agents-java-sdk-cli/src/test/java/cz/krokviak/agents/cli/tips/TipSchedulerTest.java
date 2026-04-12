package cz.krokviak.agents.cli.tips;

import cz.krokviak.agents.cli.test.FakeRenderer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TipSchedulerTest {

    @Test
    void onSpinnerStartRendersATip() {
        var renderer = new FakeRenderer();
        var registry = new TipRegistry(List.of(new Tip("one", "tip one", "w")));
        var sched = new TipScheduler(renderer, registry);
        sched.onSpinnerStart();
        assertTrue(renderer.lines().stream().anyMatch(l -> l.contains("tip one")),
            "scheduler must print the tip");
    }

    @Test
    void debounceSuppressesRapidRepeats() {
        var renderer = new FakeRenderer();
        var registry = new TipRegistry(List.of(
            new Tip("a", "alpha", "x"), new Tip("b", "beta", "x")));
        var sched = new TipScheduler(renderer, registry);
        sched.onSpinnerStart();
        sched.onSpinnerStart(); // within debounce window
        long tipLines = renderer.lines().stream().filter(l -> l.contains("\uD83D\uDCA1")).count();
        assertEquals(1, tipLines, "second rapid trigger must be debounced");
    }

    @Test
    void emptyRegistryDoesNothing() {
        var renderer = new FakeRenderer();
        var sched = new TipScheduler(renderer, new TipRegistry(List.of()));
        sched.onSpinnerStart();
        assertTrue(renderer.lines().stream().noneMatch(l -> l.contains("\uD83D\uDCA1")));
    }
}
