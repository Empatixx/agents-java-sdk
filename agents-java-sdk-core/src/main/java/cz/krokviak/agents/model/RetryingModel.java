package cz.krokviak.agents.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Decorator that retries transient model failures with exponential backoff and jitter.
 *
 * <p>Wraps any {@link Model} implementation. On retryable exceptions (the caller's
 * {@link RetryPolicy} decides), sleeps {@code base × 2^attempt + jitter} and retries
 * until {@code maxAttempts} is exhausted. After that the last exception propagates.
 *
 * <p><b>Scope:</b> only {@link #call} is retried. {@link #callStreamed} returns a
 * stream whose elements may already be committed to the caller — it's not safe to
 * silently retry once bytes have shipped. Streaming callers that want retry must
 * wrap individual calls themselves.
 */
public final class RetryingModel implements Model {

    private static final Logger log = LoggerFactory.getLogger(RetryingModel.class);

    /** Classifies a throwable as transient (retryable) or terminal. */
    @FunctionalInterface
    public interface RetryPolicy {
        boolean isRetryable(Throwable error);

        /** Default policy: retry on any RuntimeException that mentions 429 or 5xx. */
        RetryPolicy DEFAULT = error -> {
            if (error == null) return false;
            String msg = error.getMessage() == null ? "" : error.getMessage().toLowerCase();
            if (msg.contains("429") || msg.contains("rate_limit") || msg.contains("rate limit")) return true;
            if (msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504")) return true;
            if (msg.contains("timeout") || msg.contains("timed out")) return true;
            if (msg.contains("connection reset") || msg.contains("connection refused")) return true;
            return false;
        };
    }

    public record Config(int maxAttempts, long baseDelayMs, long maxDelayMs, RetryPolicy policy) {
        public static Config defaults() {
            return new Config(3, 250L, 10_000L, RetryPolicy.DEFAULT);
        }
    }

    private final Model delegate;
    private final Config config;

    public RetryingModel(Model delegate) {
        this(delegate, Config.defaults());
    }

    public RetryingModel(Model delegate, Config config) {
        this.delegate = delegate;
        this.config = config;
    }

    @Override
    public ModelResponse call(LlmContext ctx, ModelSettings settings) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < config.maxAttempts; attempt++) {
            try {
                return delegate.call(ctx, settings);
            } catch (RuntimeException e) {
                last = e;
                if (!config.policy.isRetryable(e) || attempt == config.maxAttempts - 1) {
                    throw e;
                }
                long sleep = backoffMs(attempt);
                log.debug("Model call failed (attempt {}/{}): {}. Retrying in {}ms.",
                    attempt + 1, config.maxAttempts, e.getMessage(), sleep);
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry backoff", ie);
                }
            }
        }
        throw last;
    }

    @Override
    public ModelResponseStream callStreamed(LlmContext ctx, ModelSettings settings) {
        // Streams are non-retryable — already committed bytes make idempotency unsafe.
        return delegate.callStreamed(ctx, settings);
    }

    long backoffMs(int attempt) {
        long exp = Math.min(config.maxDelayMs, config.baseDelayMs * (1L << attempt));
        long jitter = ThreadLocalRandom.current().nextLong(exp / 2 + 1);
        return exp + jitter;
    }
}
