package cz.krokviak.agents.runner;

/**
 * Thrown by {@link AbortSignal#throwIfAborted()} when a caller (typically
 * {@code AgentService.cancelTurn()}) has signalled that the current run
 * should stop. Unchecked — engine code catches it at the turn boundary and
 * reports the run as interrupted.
 */
public final class AbortException extends RuntimeException {
    public AbortException() { super("aborted"); }
    public AbortException(String message) { super(message); }
}
