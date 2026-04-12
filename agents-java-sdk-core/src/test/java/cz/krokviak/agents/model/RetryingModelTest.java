package cz.krokviak.agents.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RetryingModelTest {

    private static ModelResponse fakeResponse(String text) {
        return new ModelResponse("id",
            List.of(new ModelResponse.OutputItem.Message(text)),
            null);
    }

    private static Model flakyFor(int failCount, String failMessage, ModelResponse success) {
        return new Model() {
            final AtomicInteger calls = new AtomicInteger(0);
            @Override public ModelResponse call(LlmContext ctx, ModelSettings s) {
                int n = calls.incrementAndGet();
                if (n <= failCount) throw new RuntimeException(failMessage);
                return success;
            }
            @Override public ModelResponseStream callStreamed(LlmContext ctx, ModelSettings s) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void successOnFirstTryDoesNotRetry() {
        var inner = flakyFor(0, null, fakeResponse("ok"));
        var retrying = new RetryingModel(inner, new RetryingModel.Config(3, 10L, 50L, RetryingModel.RetryPolicy.DEFAULT));
        assertEquals("ok", ((ModelResponse.OutputItem.Message) retrying.call(null, null).output().get(0)).content());
    }

    @Test
    void retriesThenSucceeds() {
        var inner = flakyFor(2, "429 rate_limit", fakeResponse("won"));
        var retrying = new RetryingModel(inner, new RetryingModel.Config(5, 1L, 10L, RetryingModel.RetryPolicy.DEFAULT));
        assertEquals("won", ((ModelResponse.OutputItem.Message) retrying.call(null, null).output().get(0)).content());
    }

    @Test
    void nonRetryableFailurePropagates() {
        var inner = flakyFor(1, "400 bad request", fakeResponse("never"));
        var retrying = new RetryingModel(inner, new RetryingModel.Config(5, 1L, 10L, RetryingModel.RetryPolicy.DEFAULT));
        var ex = assertThrows(RuntimeException.class, () -> retrying.call(null, null));
        assertTrue(ex.getMessage().contains("400"));
    }

    @Test
    void maxAttemptsExhausted() {
        var inner = flakyFor(10, "503 unavailable", fakeResponse("never"));
        var retrying = new RetryingModel(inner, new RetryingModel.Config(3, 1L, 10L, RetryingModel.RetryPolicy.DEFAULT));
        assertThrows(RuntimeException.class, () -> retrying.call(null, null));
    }

    @Test
    void backoffIsExponentialBoundedByMax() {
        var retrying = new RetryingModel(
            new Model() {
                @Override public ModelResponse call(LlmContext ctx, ModelSettings s) { return null; }
                @Override public ModelResponseStream callStreamed(LlmContext ctx, ModelSettings s) { return null; }
            },
            new RetryingModel.Config(5, 100L, 500L, RetryingModel.RetryPolicy.DEFAULT));
        long b0 = retrying.backoffMs(0);
        long b5 = retrying.backoffMs(5);
        assertTrue(b0 >= 100 && b0 <= 200, "attempt-0 backoff: " + b0);
        assertTrue(b5 <= 750, "attempt-5 capped: " + b5);  // 500 base + max 250 jitter
    }

    @Test
    void defaultPolicyClassifiesCommonTransientErrors() {
        var p = RetryingModel.RetryPolicy.DEFAULT;
        assertTrue(p.isRetryable(new RuntimeException("HTTP 429 Too Many Requests")));
        assertTrue(p.isRetryable(new RuntimeException("503 Service Unavailable")));
        assertTrue(p.isRetryable(new RuntimeException("Connection reset by peer")));
        assertTrue(p.isRetryable(new RuntimeException("Read timed out")));
        assertFalse(p.isRetryable(new RuntimeException("400 Bad Request")));
        assertFalse(p.isRetryable(new RuntimeException("401 Unauthorized")));
        assertFalse(p.isRetryable(null));
    }

    @Test
    void streamedCallBypassesRetry() {
        Model inner = new Model() {
            int calls = 0;
            @Override public ModelResponse call(LlmContext ctx, ModelSettings s) { return null; }
            @Override public ModelResponseStream callStreamed(LlmContext ctx, ModelSettings s) {
                calls++;
                throw new RuntimeException("429");
            }
        };
        var retrying = new RetryingModel(inner);
        assertThrows(RuntimeException.class, () -> retrying.callStreamed(null, null));
    }
}
