package cz.krokviak.agents.cli.service;

import cz.krokviak.agents.api.dto.PermissionDecision;
import cz.krokviak.agents.api.event.AgentEvent;
import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.agent.context.ContextCompactor;
import cz.krokviak.agents.agent.mailbox.MailboxManager;
import cz.krokviak.agents.agent.permission.PermissionManager;
import cz.krokviak.agents.agent.task.TaskManager;
import cz.krokviak.agents.cli.test.FakeRenderer;
import cz.krokviak.agents.runner.InputItem;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AgentServiceImplTest {

    private CliContext newCtx() {
        var permissions = new PermissionManager(PermissionManager.PermissionMode.DEFAULT);
        return new CliContext(
            /*model*/ null, "claude-test", "", "",
            new FakeRenderer(), permissions,
            new ContextCompactor(null), Path.of("/tmp"),
            "system", null, "session-1",
            new TaskManager(), new MailboxManager()
        );
    }

    @Test
    void historyAndClearRoundTrip() {
        var ctx = newCtx();
        var svc = new AgentServiceImpl(ctx);
        ctx.setAgent(svc);
        ctx.history().add(new InputItem.UserMessage("hi"));
        assertEquals(1, svc.history().size());
        svc.clearHistory();
        assertEquals(0, svc.history().size());
    }

    @Test
    void planModeFlips() {
        var ctx = newCtx();
        var svc = new AgentServiceImpl(ctx);
        assertFalse(svc.isPlanMode());
        svc.setPlanMode(true);
        assertTrue(svc.isPlanMode());
    }

    @Test
    void requestPermissionEmitsEventAndResolves() throws Exception {
        var ctx = newCtx();
        var svc = new AgentServiceImpl(ctx);
        AtomicReference<AgentEvent.PermissionRequested> seen = new AtomicReference<>();
        ctx.eventBus().on(AgentEvent.PermissionRequested.class, seen::set);

        var future = svc.requestPermission("bash", Map.of("command", "ls"), "tc-1", List.of("y", "n"));
        assertNotNull(seen.get());
        assertFalse(future.isDone());

        // resolve from another thread (agent-side .get() would be blocking)
        Thread.ofVirtual().start(() -> svc.resolvePermission(seen.get().requestId(), PermissionDecision.ALLOW_ONCE)).join();

        assertEquals(PermissionDecision.ALLOW_ONCE, future.get());
    }

    @Test
    void questionFlowResolvesByIndex() throws Exception {
        var ctx = newCtx();
        var svc = new AgentServiceImpl(ctx);
        AtomicReference<AgentEvent.QuestionRequested> seen = new AtomicReference<>();
        ctx.eventBus().on(AgentEvent.QuestionRequested.class, seen::set);

        var future = svc.requestQuestion("pick one", List.of("a", "b", "c"));
        Thread.ofVirtual().start(() -> svc.resolveQuestion(seen.get().requestId(), 2)).join();
        assertEquals(2, future.get());
    }
}
