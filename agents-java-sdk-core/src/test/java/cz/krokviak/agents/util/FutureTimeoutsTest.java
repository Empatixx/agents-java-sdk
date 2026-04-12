package cz.krokviak.agents.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class FutureTimeoutsTest {

    @Test
    void completedFutureReturnsValue() {
        var f = CompletableFuture.completedFuture("ok");
        assertEquals("ok", FutureTimeouts.awaitUserPrompt(f, () -> "fallback"));
    }

    @Test
    void timeoutReturnsFallback() {
        var f = new CompletableFuture<String>(); // never completes
        String result = FutureTimeouts.await(f, 50, TimeUnit.MILLISECONDS, () -> "timed-out");
        assertEquals("timed-out", result);
    }

    @Test
    void failedFutureReturnsFallback() {
        var f = CompletableFuture.<String>failedFuture(new RuntimeException("boom"));
        assertEquals("fallback", FutureTimeouts.awaitUserPrompt(f, () -> "fallback"));
    }

    @Test
    void fallbackSupplierCalledLazilyOnSuccess() {
        var f = CompletableFuture.completedFuture(42);
        int[] fallbackCalls = {0};
        assertEquals(42, FutureTimeouts.awaitUserPrompt(f, () -> { fallbackCalls[0]++; return -1; }));
        assertEquals(0, fallbackCalls[0]);
    }

    @Test
    void userPromptDefaultIsFiveMinutes() {
        assertEquals(300L, FutureTimeouts.USER_PROMPT_TIMEOUT_SECONDS);
    }
}
