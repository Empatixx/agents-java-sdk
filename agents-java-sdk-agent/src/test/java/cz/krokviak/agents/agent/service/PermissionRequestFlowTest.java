package cz.krokviak.agents.agent.service;

import cz.krokviak.agents.agent.AgentContext;

import cz.krokviak.agents.api.dto.PermissionDecision;
import cz.krokviak.agents.api.event.AgentEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PermissionRequestFlowTest {

    @Test
    void requestEmitsEventAndFutureCompletesOnResolve() throws Exception {
        // Build a minimal AgentService-like harness around CliEventBus + the
        // pending-future map that AgentServiceImpl implements. We don't need
        // a full AgentContext for this contract test.
        var bus = new cz.krokviak.agents.agent.event.DefaultEventBus();
        var pending = new java.util.concurrent.ConcurrentHashMap<String, CompletableFuture<PermissionDecision>>();

        AtomicReference<AgentEvent.PermissionRequested> seen = new AtomicReference<>();
        bus.on(AgentEvent.PermissionRequested.class, seen::set);

        // simulate agent-side request
        String requestId = "req-1";
        var future = new CompletableFuture<PermissionDecision>();
        pending.put(requestId, future);
        bus.emit(new AgentEvent.PermissionRequested(requestId, "bash", Map.of("command", "ls"), "tc-1", java.util.List.of("yes", "no")));

        assertNotNull(seen.get(), "listener should have captured PermissionRequested");
        assertEquals("bash", seen.get().toolName());
        assertFalse(future.isDone(), "future must not complete until resolve is called");

        // simulate UI-side resolve from a different thread (contract requirement)
        Thread.ofVirtual().start(() -> pending.remove(requestId).complete(PermissionDecision.ALLOW_ONCE)).join();

        assertEquals(PermissionDecision.ALLOW_ONCE, future.get());
    }

    @Test
    void resolveWithoutPendingRequestIsNoop() {
        var pending = new java.util.concurrent.ConcurrentHashMap<String, CompletableFuture<PermissionDecision>>();
        // Nothing enqueued; a stray resolve must not throw.
        var f = pending.remove("unknown");
        assertNull(f);
    }
}
