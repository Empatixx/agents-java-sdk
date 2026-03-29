package cz.krokviak.agents.context;

import cz.krokviak.agents.model.Usage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RunContextTest {
    record TestContext(String userId) {}
    @Test
    void contextWrapsUserObject() {
        var ctx = new RunContext<>(new TestContext("user-1"));
        assertEquals("user-1", ctx.context().userId());
    }
    @Test
    void usageStartsAtZero() {
        var ctx = new RunContext<>(new TestContext("user-1"));
        assertEquals(Usage.zero(), ctx.usage());
    }
    @Test
    void usageAccumulates() {
        var ctx = new RunContext<>(new TestContext("user-1"));
        ctx.addUsage(new Usage(10, 20));
        ctx.addUsage(new Usage(5, 10));
        assertEquals(15, ctx.usage().inputTokens());
        assertEquals(30, ctx.usage().outputTokens());
    }
    @Test
    void toolContextExtendsRunContext() {
        var runCtx = new RunContext<>(new TestContext("user-1"));
        var toolCtx = new ToolContext<>(runCtx, "call-123");
        assertEquals("user-1", toolCtx.context().userId());
        assertEquals("call-123", toolCtx.toolCallId());
    }
}
