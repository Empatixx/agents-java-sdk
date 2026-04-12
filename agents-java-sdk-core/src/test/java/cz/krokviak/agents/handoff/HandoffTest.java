package cz.krokviak.agents.handoff;

import cz.krokviak.agents.def.Agent;
import cz.krokviak.agents.context.RunContext;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class HandoffTest {

    @Test
    void simpleHandoff() {
        Agent<Void> target = Agent.<Void>builder("Target").instructions("target").build();
        Handoff<Void> handoff = Handoff.<Void>to(target).build();

        assertEquals("Target", handoff.agent().name());
        assertEquals("transfer_to_Target", handoff.toolName());
        assertTrue(handoff.isEnabled(new RunContext<>(null)));
    }

    @Test
    void handoffWithDescription() {
        Agent<Void> target = Agent.<Void>builder("Specialist").instructions("s").build();
        Handoff<Void> handoff = Handoff.<Void>to(target)
            .description("Transfer for complex issues")
            .build();

        assertEquals("Transfer for complex issues", handoff.description());
    }

    @Test
    void handoffWithOnHandoffCallback() {
        AtomicBoolean called = new AtomicBoolean(false);
        Agent<Void> target = Agent.<Void>builder("Target").instructions("t").build();
        Handoff<Void> handoff = Handoff.<Void>to(target)
            .onHandoff(ctx -> called.set(true))
            .build();

        handoff.onHandoff().accept(new RunContext<>(null));
        assertTrue(called.get());
    }

    @Test
    void handoffWithEnabledPredicate() {
        Agent<String> target = Agent.<String>builder("Target").instructions("t").build();
        Handoff<String> handoff = Handoff.<String>to(target)
            .enabled((ctx, agent) -> "admin".equals(ctx.context()))
            .build();

        assertTrue(handoff.isEnabled(new RunContext<>("admin")));
        assertFalse(handoff.isEnabled(new RunContext<>("guest")));
    }
}
