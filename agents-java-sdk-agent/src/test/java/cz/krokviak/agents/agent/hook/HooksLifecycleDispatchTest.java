package cz.krokviak.agents.agent.hook;

import cz.krokviak.agents.api.hook.Hook;
import cz.krokviak.agents.api.hook.HookPhase;
import cz.krokviak.agents.api.hook.HookResult;
import cz.krokviak.agents.api.hook.events.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HooksLifecycleDispatchTest {

    @Test
    void dispatchTypedCallsMatchingPhaseOnly() {
        var hooks = new Hooks();
        AtomicReference<PreTurnEvent> seen = new AtomicReference<>();
        hooks.register(HookPhase.PRE_TURN, new Hook<PreTurnEvent>() {
            @Override public HookPhase phase() { return HookPhase.PRE_TURN; }
            @Override public HookResult execute(PreTurnEvent event) {
                seen.set(event);
                return new HookResult.Proceed();
            }
        });

        var evt = new PreTurnEvent("hello", new ArrayList<>());
        var r = hooks.dispatchTyped(HookPhase.PRE_TURN, evt);
        assertInstanceOf(HookResult.Proceed.class, r);
        assertSame(evt, seen.get());
    }

    @Test
    void preTurnHookCanAppendAdditionalContext() {
        var hooks = new Hooks();
        hooks.register(HookPhase.PRE_TURN, new Hook<PreTurnEvent>() {
            @Override public HookPhase phase() { return HookPhase.PRE_TURN; }
            @Override public HookResult execute(PreTurnEvent e) {
                e.additionalContext().add("from hook");
                return new HookResult.Proceed();
            }
        });

        List<String> ctx = new ArrayList<>();
        hooks.dispatchTyped(HookPhase.PRE_TURN, new PreTurnEvent("q", ctx));
        assertEquals(List.of("from hook"), ctx);
    }

    @Test
    void blockShortCircuitsLaterHooks() {
        var hooks = new Hooks();
        AtomicReference<String> secondCalled = new AtomicReference<>("no");
        hooks.register(HookPhase.POST_TURN, new Hook<PostTurnEvent>() {
            @Override public HookPhase phase() { return HookPhase.POST_TURN; }
            @Override public HookResult execute(PostTurnEvent e) {
                return new HookResult.Block("stop");
            }
        });
        hooks.register(HookPhase.POST_TURN, new Hook<PostTurnEvent>() {
            @Override public HookPhase phase() { return HookPhase.POST_TURN; }
            @Override public HookResult execute(PostTurnEvent e) {
                secondCalled.set("yes");
                return new HookResult.Proceed();
            }
        });

        var r = hooks.dispatchTyped(HookPhase.POST_TURN,
            new PostTurnEvent("done", List.of(), 1));
        assertInstanceOf(HookResult.Block.class, r);
        assertEquals("no", secondCalled.get());
    }

    @Test
    void compactEventDispatches() {
        var hooks = new Hooks();
        AtomicReference<CompactEvent> pre = new AtomicReference<>();
        AtomicReference<CompactEvent> post = new AtomicReference<>();
        hooks.register(HookPhase.PRE_COMPACT, new Hook<CompactEvent>() {
            @Override public HookPhase phase() { return HookPhase.PRE_COMPACT; }
            @Override public HookResult execute(CompactEvent e) { pre.set(e); return new HookResult.Proceed(); }
        });
        hooks.register(HookPhase.POST_COMPACT, new Hook<CompactEvent>() {
            @Override public HookPhase phase() { return HookPhase.POST_COMPACT; }
            @Override public HookResult execute(CompactEvent e) { post.set(e); return new HookResult.Proceed(); }
        });

        hooks.dispatchTyped(HookPhase.PRE_COMPACT, new CompactEvent(100, -1, "test"));
        hooks.dispatchTyped(HookPhase.POST_COMPACT, new CompactEvent(100, 40, "test"));

        assertEquals(-1, pre.get().sizeAfter());
        assertEquals(40, post.get().sizeAfter());
        assertEquals("test", post.get().trigger());
    }

    @Test
    void subagentEventDispatches() {
        var hooks = new Hooks();
        AtomicReference<SubagentEvent> captured = new AtomicReference<>();
        hooks.register(HookPhase.SUBAGENT_START, new Hook<SubagentEvent>() {
            @Override public HookPhase phase() { return HookPhase.SUBAGENT_START; }
            @Override public HookResult execute(SubagentEvent e) { captured.set(e); return new HookResult.Proceed(); }
        });

        hooks.dispatchTyped(HookPhase.SUBAGENT_START,
            new SubagentEvent("a1", "workerA", "STARTING", null, true));
        assertEquals("a1", captured.get().agentId());
        assertTrue(captured.get().background());
    }

    @Test
    void sessionEventDispatches() {
        var hooks = new Hooks();
        AtomicReference<SessionEvent> captured = new AtomicReference<>();
        hooks.register(HookPhase.SESSION_START, new Hook<SessionEvent>() {
            @Override public HookPhase phase() { return HookPhase.SESSION_START; }
            @Override public HookResult execute(SessionEvent e) { captured.set(e); return new HookResult.Proceed(); }
        });
        hooks.dispatchTyped(HookPhase.SESSION_START, new SessionEvent("sess-42", 17));
        assertEquals("sess-42", captured.get().sessionId());
        assertEquals(17, captured.get().messageCount());
    }

    @Test
    void noHookRegisteredReturnsProceed() {
        var hooks = new Hooks();
        var r = hooks.dispatchTyped(HookPhase.PRE_TURN, new PreTurnEvent("q", new ArrayList<>()));
        assertInstanceOf(HookResult.Proceed.class, r);
    }
}
