package cz.krokviak.agents.cli.event;

import cz.krokviak.agents.cli.test.FakeRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RenderEventListenerTest {

    CliEventBus bus;
    FakeRenderer renderer;

    @BeforeEach
    void setup() {
        bus = new CliEventBus();
        renderer = new FakeRenderer();
        new RenderEventListener(renderer).register(bus);
    }

    @Test
    void toolStartedCallsPrintToolCall() {
        bus.emit(new CliEvent.ToolStarted("bash", Map.of("command", "ls"), "tc-1", false));
        assertTrue(renderer.lines().stream().anyMatch(l -> l.contains("[tool] bash")));
    }

    @Test
    void toolCompletedCallsPrintToolResult() {
        bus.emit(new CliEvent.ToolCompleted("read_file", "file content", 1, 50));
        assertTrue(renderer.lines().stream().anyMatch(l -> l.contains("[result] read_file")));
    }

    @Test
    void toolBlockedCallsPrintPermissionDenied() {
        bus.emit(new CliEvent.ToolBlocked("write_file", "denied"));
        assertTrue(renderer.lines().stream().anyMatch(l -> l.contains("[denied] write_file")));
    }

    @Test
    void responseDeltaCallsPrintTextDelta() {
        bus.emit(new CliEvent.ResponseDelta("hello"));
        assertTrue(renderer.lines().stream().anyMatch(l -> l.contains("[delta] hello")));
    }

    @Test
    void responseDoneAddsNewline() {
        bus.emit(new CliEvent.ResponseDone(100, 50));
        assertTrue(renderer.lines().contains(""));
    }

    @Test
    void spinnerStartStop() {
        bus.emit(new CliEvent.SpinnerStart("thinking"));
        bus.emit(new CliEvent.SpinnerStop());
        // FakeRenderer doesn't track spinner state — just verify no crash
    }

    @Test
    void errorOccurred() {
        bus.emit(new CliEvent.ErrorOccurred("something broke"));
        assertTrue(renderer.errors().contains("something broke"));
    }

    @Test
    void taskNotificationRendered() {
        bus.emit(new CliEvent.TaskNotification("t-1", "agent-1", "COMPLETED", "found stuff"));
        assertTrue(renderer.lines().stream().anyMatch(l -> l.contains("agent-1")));
        assertTrue(renderer.lines().stream().anyMatch(l -> l.contains("found stuff")));
    }

    @Test
    void mailboxMessageRendered() {
        bus.emit(new CliEvent.MailboxMessage("agent-2", "hello from agent"));
        assertTrue(renderer.lines().stream().anyMatch(l -> l.contains("agent-2")));
    }

    @Test
    void compactionRendered() {
        bus.emit(new CliEvent.CompactionTriggered(50, 20));
        assertTrue(renderer.lines().stream().anyMatch(l -> l.contains("50") && l.contains("20")));
    }

    @Test
    void sessionLoadedRendered() {
        bus.emit(new CliEvent.SessionLoaded("abc123", 42));
        assertTrue(renderer.lines().stream().anyMatch(l -> l.contains("42") && l.contains("abc123")));
    }

    @Test
    void userEventsDoNotCrashRenderer() {
        // User events are consumed by business logic, not renderer — should be ignored
        assertDoesNotThrow(() -> {
            bus.emit(new CliEvent.UserPromptSubmitted("hello"));
            bus.emit(new CliEvent.UserSelectionMade("ctx", 0, "Yes"));
            bus.emit(new CliEvent.UserTextInputSubmitted("ctx", "text"));
            bus.emit(new CliEvent.CommandExecuted("help", null));
            bus.emit(new CliEvent.PermissionDecision("bash", "tc-1", true));
        });
    }
}
