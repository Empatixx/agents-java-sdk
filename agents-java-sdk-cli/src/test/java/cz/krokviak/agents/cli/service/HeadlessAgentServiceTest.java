package cz.krokviak.agents.cli.service;

import cz.krokviak.agents.api.AgentService;
import cz.krokviak.agents.api.dto.PermissionDecision;
import cz.krokviak.agents.api.event.AgentEvent;
import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.agent.context.ContextCompactor;
import cz.krokviak.agents.agent.mailbox.MailboxManager;
import cz.krokviak.agents.agent.permission.PermissionManager;
import cz.krokviak.agents.agent.task.TaskManager;
import cz.krokviak.agents.cli.test.FakeRenderer;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the {@link AgentService} contract can be driven without any TUI
 * renderer, dialog, or {@code CliApp} — which is the prerequisite for a
 * GraphQL (or any other) frontend to own only the event subscription +
 * mutation layer.
 */
class HeadlessAgentServiceTest {

    @Test
    void eventSubscriptionAndPromptResolveWorkWithoutRenderer() throws Exception {
        var ctx = new CliContext(
            null, "claude-test", "", "",
            new FakeRenderer(), new PermissionManager(PermissionManager.PermissionMode.DEFAULT),
            new ContextCompactor(null), Path.of("/tmp"), "", null, "s1",
            new TaskManager(), new MailboxManager()
        );
        AgentService svc = new AgentServiceImpl(ctx);
        ctx.setAgent(svc);

        // Subscriber collects events — simulating a GraphQL subscription sink.
        List<AgentEvent> events = new CopyOnWriteArrayList<>();
        svc.events().subscribe(events::add);

        // Emit a few agent-side events and verify they surface.
        svc.events().emit(new AgentEvent.AgentStarted("agent-1", "running test"));
        svc.events().emit(new AgentEvent.ResponseDelta("hello"));
        svc.events().emit(new AgentEvent.AgentCompleted("agent-1", "done"));
        assertEquals(3, events.size());

        // Exercise the async permission flow end-to-end headless.
        var future = svc.requestPermission("bash", Map.of("command", "ls"), "tc-1", List.of("y", "n"));
        var req = events.stream()
            .filter(e -> e instanceof AgentEvent.PermissionRequested)
            .map(e -> (AgentEvent.PermissionRequested) e)
            .findFirst().orElseThrow();
        Thread.ofVirtual().start(() -> svc.resolvePermission(req.requestId(), PermissionDecision.DENY)).join();
        assertEquals(PermissionDecision.DENY, future.get());
    }
}
