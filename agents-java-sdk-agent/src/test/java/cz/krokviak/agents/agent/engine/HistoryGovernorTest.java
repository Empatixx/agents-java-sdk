package cz.krokviak.agents.agent.engine;

import cz.krokviak.agents.agent.AgentDefaults;
import cz.krokviak.agents.agent.event.DefaultEventBus;
import cz.krokviak.agents.api.event.AgentEvent;
import cz.krokviak.agents.runner.InputItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class HistoryGovernorTest {

    private static List<InputItem> filled(int n) {
        List<InputItem> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(new InputItem.UserMessage("msg-" + i));
        return out;
    }

    @Test
    void underCapDoesNothing() {
        var gov = new HistoryGovernor();
        var hist = filled(10);
        assertEquals(0, gov.enforce(hist, null));
        assertEquals(10, hist.size());
    }

    @Test
    void overCapTrimsToExactCap() {
        var gov = new HistoryGovernor();
        var hist = filled(AgentDefaults.HISTORY_HARD_CAP + 50);
        int dropped = gov.enforce(hist, null);
        assertEquals(50, dropped);
        assertEquals(AgentDefaults.HISTORY_HARD_CAP, hist.size());
    }

    @Test
    void preservesFirstItemWhenTrimming() {
        var gov = new HistoryGovernor();
        var hist = new ArrayList<InputItem>();
        hist.add(new InputItem.SystemMessage("SYSTEM_ANCHOR"));
        for (int i = 0; i < AgentDefaults.HISTORY_HARD_CAP + 10; i++) {
            hist.add(new InputItem.UserMessage("x"));
        }
        gov.enforce(hist, null);
        assertEquals("SYSTEM_ANCHOR",
            ((InputItem.SystemMessage) hist.get(0)).content());
    }

    @Test
    void warningIsOneShot() {
        var gov = new HistoryGovernor();
        var bus = new DefaultEventBus();
        var seen = new CopyOnWriteArrayList<AgentEvent>();
        bus.subscribe(seen::add);

        // First call above warn threshold — emits warning.
        gov.enforce(filled(AgentDefaults.HISTORY_WARN_THRESHOLD + 1), bus);
        long warnCount1 = seen.stream().filter(e -> e instanceof AgentEvent.ErrorOccurred).count();
        assertEquals(1, warnCount1);

        // Second call — should not emit another warning.
        gov.enforce(filled(AgentDefaults.HISTORY_WARN_THRESHOLD + 1), bus);
        long warnCount2 = seen.stream().filter(e -> e instanceof AgentEvent.ErrorOccurred).count();
        assertEquals(1, warnCount2, "warning is one-shot per governor instance");
    }

    @Test
    void trimEmitsCompactionEvent() {
        var gov = new HistoryGovernor();
        var bus = new DefaultEventBus();
        var seen = new CopyOnWriteArrayList<AgentEvent>();
        bus.subscribe(seen::add);
        gov.enforce(filled(AgentDefaults.HISTORY_HARD_CAP + 5), bus);
        assertTrue(seen.stream().anyMatch(e -> e instanceof AgentEvent.CompactionTriggered));
    }

    @Test
    void nullHistoryIsNoop() {
        var gov = new HistoryGovernor();
        assertEquals(0, gov.enforce(null, null));
    }
}
