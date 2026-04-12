package cz.krokviak.agents.runner;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative cancellation flag propagated through a run. Mirrors the
 * {@code AbortSignal} concept from the Web Fetch API / Node.js — callers
 * flip it via {@link #abort()}, engine check-points call
 * {@link #throwIfAborted()} at safe points to exit cleanly.
 *
 * <p>Deliberately not named {@code CancellationToken} — "token" collides
 * with LLM-token terminology and confuses the domain.
 */
public final class AbortSignal {

    private final AtomicBoolean aborted = new AtomicBoolean(false);

    public boolean isAborted() { return aborted.get(); }

    /** Request abort. Idempotent; safe to call from any thread. */
    public void abort() { aborted.set(true); }

    /** Throws {@link AbortException} when {@link #isAborted()} is true. */
    public void throwIfAborted() {
        if (aborted.get()) throw new AbortException();
    }

    /** Reset the signal so the owning context can be reused for the next turn. */
    public void reset() { aborted.set(false); }
}
