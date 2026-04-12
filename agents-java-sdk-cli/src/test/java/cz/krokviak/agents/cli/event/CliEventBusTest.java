package cz.krokviak.agents.cli.event;

import cz.krokviak.agents.api.event.AgentEvent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CliEventBusTest {

    @Test
    void emitDeliversToAllListeners() {
        var bus = new CliEventBus();
        List<AgentEvent> received = new ArrayList<>();
        bus.subscribe(received::add);
        bus.subscribe(received::add);

        bus.emit(new AgentEvent.SpinnerStart("loading"));
        assertEquals(2, received.size());
    }

    @Test
    void typedSubscriptionFilters() {
        var bus = new CliEventBus();
        List<AgentEvent.ToolStarted> tools = new ArrayList<>();
        List<AgentEvent.ErrorOccurred> errors = new ArrayList<>();

        bus.on(AgentEvent.ToolStarted.class, tools::add);
        bus.on(AgentEvent.ErrorOccurred.class, errors::add);

        bus.emit(new AgentEvent.ToolStarted("bash", Map.of("command", "ls"), "tc-1", false));
        bus.emit(new AgentEvent.ErrorOccurred("broke"));
        bus.emit(new AgentEvent.SpinnerStart("thinking"));

        assertEquals(1, tools.size());
        assertEquals("bash", tools.getFirst().name());
        assertEquals(1, errors.size());
    }

    @Test
    void listenerExceptionDoesNotBreakOthers() {
        var bus = new CliEventBus();
        List<AgentEvent> received = new ArrayList<>();

        bus.subscribe(e -> { throw new RuntimeException("boom"); });
        bus.subscribe(received::add);

        bus.emit(new AgentEvent.SpinnerStop());
        assertEquals(1, received.size());
    }

    @Test
    void emitWithNoListenersIsNoop() {
        assertDoesNotThrow(() -> new CliEventBus().emit(new AgentEvent.ErrorOccurred("orphan")));
    }

    @Test
    void toolCompletedDataPreserved() {
        var bus = new CliEventBus();
        List<AgentEvent.ToolCompleted> events = new ArrayList<>();
        bus.on(AgentEvent.ToolCompleted.class, events::add);

        bus.emit(new AgentEvent.ToolCompleted("read_file", "content", 5, 120));

        var e = events.getFirst();
        assertEquals("read_file", e.name());
        assertEquals("content", e.result());
        assertEquals(5, e.resultLines());
        assertEquals(120, e.ms());
    }

    @Test
    void agentLifecycle() {
        var bus = new CliEventBus();
        List<String> log = new ArrayList<>();

        bus.on(AgentEvent.AgentStarted.class, e -> log.add("start:" + e.agentId()));
        bus.on(AgentEvent.AgentProgress.class, e -> log.add("progress:" + e.detail()));
        bus.on(AgentEvent.AgentCompleted.class, e -> log.add("done:" + e.agentId()));

        bus.emit(new AgentEvent.AgentStarted("a1", "research"));
        bus.emit(new AgentEvent.AgentProgress("a1", "turn 2"));
        bus.emit(new AgentEvent.AgentCompleted("a1", "result"));

        assertEquals(List.of("start:a1", "progress:turn 2", "done:a1"), log);
    }

    @Test
    void responseStreaming() {
        var bus = new CliEventBus();
        StringBuilder text = new StringBuilder();

        bus.on(AgentEvent.ResponseDelta.class, e -> text.append(e.text()));
        bus.on(AgentEvent.ResponseDone.class, _ -> text.append("[DONE]"));

        bus.emit(new AgentEvent.ResponseDelta("Hello "));
        bus.emit(new AgentEvent.ResponseDelta("world"));
        bus.emit(new AgentEvent.ResponseDone(100, 50));

        assertEquals("Hello world[DONE]", text.toString());
    }

    @Test
    void toolBlocked() {
        var bus = new CliEventBus();
        List<String> blocked = new ArrayList<>();
        bus.on(AgentEvent.ToolBlocked.class, e -> blocked.add(e.name()));

        bus.emit(new AgentEvent.ToolBlocked("bash", "denied"));
        assertEquals("bash", blocked.getFirst());
    }

    @Test
    void budgetExceeded() {
        var bus = new CliEventBus();
        List<AgentEvent.BudgetExceeded> events = new ArrayList<>();
        bus.on(AgentEvent.BudgetExceeded.class, events::add);

        bus.emit(new AgentEvent.BudgetExceeded(200_000, 200_000));
        assertEquals(200_000, events.getFirst().used());
    }

    @Test
    void compactionEvent() {
        var bus = new CliEventBus();
        List<AgentEvent.CompactionTriggered> events = new ArrayList<>();
        bus.on(AgentEvent.CompactionTriggered.class, events::add);

        bus.emit(new AgentEvent.CompactionTriggered(50, 20));
        assertEquals(50, events.getFirst().messagesBefore());
    }

    // ========================= Bidirectional: TUI → Business Logic =========================

    @Test
    void userPromptSubmitted() {
        var bus = new CliEventBus();
        List<String> prompts = new ArrayList<>();
        bus.on(AgentEvent.UserPromptSubmitted.class, e -> prompts.add(e.text()));

        bus.emit(new AgentEvent.UserPromptSubmitted("fix the bug"));
        assertEquals("fix the bug", prompts.getFirst());
    }

    @Test
    void userSelectionMade() {
        var bus = new CliEventBus();
        List<String> selections = new ArrayList<>();
        bus.on(AgentEvent.UserSelectionMade.class, e ->
            selections.add(e.context() + ":" + e.selectedIndex()));

        bus.emit(new AgentEvent.UserSelectionMade("budget", 0, "Yes"));
        assertEquals("budget:0", selections.getFirst());
    }

    @Test
    void userTextInput() {
        var bus = new CliEventBus();
        List<String> inputs = new ArrayList<>();
        bus.on(AgentEvent.UserTextInputSubmitted.class, e -> inputs.add(e.value()));

        bus.emit(new AgentEvent.UserTextInputSubmitted("mkt-add", "owner/repo"));
        assertEquals("owner/repo", inputs.getFirst());
    }

    @Test
    void commandExecuted() {
        var bus = new CliEventBus();
        List<String> cmds = new ArrayList<>();
        bus.on(AgentEvent.CommandExecuted.class, e -> cmds.add(e.name()));

        bus.emit(new AgentEvent.CommandExecuted("plugin", "install x"));
        assertEquals("plugin", cmds.getFirst());
    }

    @Test
    void permissionDecision() {
        var bus = new CliEventBus();
        List<Boolean> decisions = new ArrayList<>();
        bus.on(AgentEvent.PermissionResolved.class, e -> decisions.add(e.allowed()));

        bus.emit(new AgentEvent.PermissionResolved("bash", "tc-1", true));
        bus.emit(new AgentEvent.PermissionResolved("write_file", "tc-2", false));

        assertEquals(List.of(true, false), decisions);
    }

    // ========================= Full round-trip flows =========================

    @Test
    void roundTrip_budgetExceeded_userApproves() {
        var bus = new CliEventBus();
        var budgetExtended = new ArrayList<Boolean>();

        // Business logic: listen for user response
        bus.on(AgentEvent.UserSelectionMade.class, e -> {
            if ("budget".equals(e.context())) budgetExtended.add(e.selectedIndex() == 0);
        });

        // Engine emits budget exceeded
        bus.emit(new AgentEvent.BudgetExceeded(200_000, 200_000));
        // TUI shows prompt → user picks Yes → TUI emits selection
        bus.emit(new AgentEvent.UserSelectionMade("budget", 0, "Yes"));

        assertTrue(budgetExtended.getFirst());
    }

    @Test
    void roundTrip_agentCompletes_bothUIandLogicReact() {
        var bus = new CliEventBus();
        List<String> uiLog = new ArrayList<>();
        List<String> bizLog = new ArrayList<>();

        // TUI listener
        bus.on(AgentEvent.AgentCompleted.class, e -> uiLog.add("render:" + e.agentId()));
        // Business logic listener
        bus.on(AgentEvent.AgentCompleted.class, e -> bizLog.add("cleanup:" + e.agentId()));

        bus.emit(new AgentEvent.AgentCompleted("a1", "done"));

        assertEquals("render:a1", uiLog.getFirst());
        assertEquals("cleanup:a1", bizLog.getFirst());
    }

    @Test
    void roundTrip_toolFlow_startToComplete() {
        var bus = new CliEventBus();
        List<String> timeline = new ArrayList<>();

        bus.on(AgentEvent.ToolStarted.class, e -> timeline.add("start:" + e.name()));
        bus.on(AgentEvent.ToolCompleted.class, e -> timeline.add("done:" + e.name() + ":" + e.ms() + "ms"));

        bus.emit(new AgentEvent.ToolStarted("grep", Map.of("pattern", "TODO"), "tc-1", false));
        bus.emit(new AgentEvent.ToolCompleted("grep", "found 3 matches", 3, 45));

        assertEquals(List.of("start:grep", "done:grep:45ms"), timeline);
    }

    @Test
    void multipleListenersSameEvent() {
        var bus = new CliEventBus();
        List<String> log = new ArrayList<>();

        bus.on(AgentEvent.ToolStarted.class, e -> log.add("L1"));
        bus.on(AgentEvent.ToolStarted.class, e -> log.add("L2"));
        bus.on(AgentEvent.ToolStarted.class, e -> log.add("L3"));

        bus.emit(new AgentEvent.ToolStarted("x", Map.of(), "tc", false));
        assertEquals(List.of("L1", "L2", "L3"), log);
    }
}
