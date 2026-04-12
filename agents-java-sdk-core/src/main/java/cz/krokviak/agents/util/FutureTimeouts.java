package cz.krokviak.agents.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Helpers for awaiting {@link CompletableFuture}s with sane defaults.
 * Avoids the deadlock trap of bare {@code future.get()} in UI-interaction
 * paths (permission prompts, ask-user dialogs) where an unresponsive
 * frontend could hang the whole turn indefinitely.
 */
public final class FutureTimeouts {

    /** Default for blocking user prompts — long enough to tolerate AFK, short enough to recover. */
    public static final long USER_PROMPT_TIMEOUT_SECONDS = 300L; // 5 min

    private FutureTimeouts() {}

    /**
     * Await a future representing a blocking user interaction with a default
     * {@value #USER_PROMPT_TIMEOUT_SECONDS}s timeout.
     *
     * <p>On timeout, interruption, or execution failure, returns the supplied
     * fallback. The caller decides what the "safe default" is (typically
     * DENY for permissions, -1 for index selections, empty string for text).
     *
     * @param future   the future to await
     * @param fallback value returned on any failure (timeout, interrupt, error)
     */
    public static <T> T awaitUserPrompt(CompletableFuture<T> future, Supplier<T> fallback) {
        return await(future, USER_PROMPT_TIMEOUT_SECONDS, TimeUnit.SECONDS, fallback);
    }

    /**
     * Await a future with an explicit timeout. On any failure, returns {@code fallback.get()}.
     */
    public static <T> T await(CompletableFuture<T> future, long timeout, TimeUnit unit, Supplier<T> fallback) {
        try {
            return future.get(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback.get();
        } catch (TimeoutException | ExecutionException e) {
            return fallback.get();
        }
    }
}
