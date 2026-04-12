package cz.krokviak.agents.cli.tips;

import cz.krokviak.agents.api.event.AgentEvent;
import cz.krokviak.agents.api.event.EventBus;
import cz.krokviak.agents.cli.render.Renderer;

/**
 * Subscribes to {@link AgentEvent.SpinnerStart} and shows one tip per spinner
 * cycle (least-recently-shown). Gated by a debounce so back-to-back spinner
 * events within a short window only render one tip.
 */
public final class TipScheduler {

    private static final long DEBOUNCE_MS = 2_000;

    private final Renderer renderer;
    private final TipRegistry registry;
    private volatile long lastShownAtMs;

    public TipScheduler(Renderer renderer, TipRegistry registry) {
        this.renderer = renderer;
        this.registry = registry;
    }

    public void install(EventBus bus) {
        if (registry.isEmpty()) return;
        bus.on(AgentEvent.SpinnerStart.class, e -> onSpinnerStart());
    }

    /** Visible for testing — directly trigger the tip flow. */
    public void onSpinnerStart() {
        long now = System.currentTimeMillis();
        if (now - lastShownAtMs < DEBOUNCE_MS) return;
        registry.pickNext().ifPresent(tip -> {
            lastShownAtMs = now;
            renderer.println("\033[2m\uD83D\uDCA1 " + tip.text() + "\033[0m");
        });
    }
}
