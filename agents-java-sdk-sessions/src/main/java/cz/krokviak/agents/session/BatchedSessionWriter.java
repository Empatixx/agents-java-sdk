package cz.krokviak.agents.session;

import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.runner.RunItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Decorator that batches {@link Session#save} calls onto a single background
 * thread so the agent's turn loop never blocks on disk I/O.
 *
 * <p>Flush conditions:
 * <ul>
 *   <li>After {@code flushIntervalMs} has elapsed since the oldest queued item</li>
 *   <li>When the queue has accumulated {@code flushThreshold} items</li>
 *   <li>On {@link #shutdown()} — blocks until the pending queue drains</li>
 * </ul>
 *
 * <p>Read path ({@link #getHistory}) bypasses the queue and hits the
 * underlying session directly; callers who just wrote to a session may
 * therefore see stale data for up to {@code flushIntervalMs}. Agents that
 * need read-your-own-writes consistency should call {@link #flush()} first.
 */
public final class BatchedSessionWriter implements Session, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BatchedSessionWriter.class);

    private static final long DEFAULT_FLUSH_INTERVAL_MS = 5_000L;
    private static final int DEFAULT_FLUSH_THRESHOLD = 50;

    private record Pending(String sessionId, List<RunItem> items) {}

    private final Session delegate;
    private final long flushIntervalMs;
    private final int flushThreshold;
    private final BlockingQueue<Pending> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final Thread worker;

    public BatchedSessionWriter(Session delegate) {
        this(delegate, DEFAULT_FLUSH_INTERVAL_MS, DEFAULT_FLUSH_THRESHOLD);
    }

    public BatchedSessionWriter(Session delegate, long flushIntervalMs, int flushThreshold) {
        if (delegate == null) throw new IllegalArgumentException("delegate must not be null");
        if (flushIntervalMs <= 0) throw new IllegalArgumentException("flushIntervalMs must be > 0");
        if (flushThreshold <= 0) throw new IllegalArgumentException("flushThreshold must be > 0");
        this.delegate = delegate;
        this.flushIntervalMs = flushIntervalMs;
        this.flushThreshold = flushThreshold;
        this.worker = Thread.ofVirtual()
            .name("batched-session-writer")
            .unstarted(this::runLoop);
        this.worker.start();
    }

    @Override
    public List<InputItem> getHistory(String sessionId) {
        return delegate.getHistory(sessionId);
    }

    @Override
    public void save(String sessionId, List<RunItem> newItems) {
        if (newItems == null || newItems.isEmpty()) return;
        if (shuttingDown.get()) {
            // Synchronous fallback after shutdown so we never lose late writes.
            delegate.save(sessionId, newItems);
            return;
        }
        queue.offer(new Pending(sessionId, List.copyOf(newItems)));
    }

    /** Block until the current queue contents have been flushed to the delegate. */
    public void flush() {
        drainAndWrite(Integer.MAX_VALUE);
    }

    /** Stop the worker and synchronously flush any remaining items. */
    public void shutdown() {
        if (shuttingDown.compareAndSet(false, true)) {
            worker.interrupt();
            try {
                worker.join(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            drainAndWrite(Integer.MAX_VALUE);
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    private void runLoop() {
        while (!shuttingDown.get()) {
            try {
                Pending first = queue.poll(flushIntervalMs, TimeUnit.MILLISECONDS);
                if (first == null) continue;                 // interval elapsed, loop
                writeBatch(first);                            // write the one that woke us
                drainAndWrite(flushThreshold - 1);            // opportunistic batch with anything queued
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return; // shutdown path
            } catch (RuntimeException e) {
                log.warn("BatchedSessionWriter flush failed: {}", e.getMessage());
            }
        }
    }

    private void drainAndWrite(int maxItems) {
        List<Pending> batch = new ArrayList<>();
        queue.drainTo(batch, Math.max(0, maxItems));
        for (Pending p : batch) writeBatch(p);
    }

    private void writeBatch(Pending p) {
        try {
            delegate.save(p.sessionId(), p.items());
        } catch (RuntimeException e) {
            log.warn("Session save failed for {}: {}", p.sessionId(), e.getMessage());
        }
    }

    /** Visible for testing — current queue depth. */
    int queueSize() { return queue.size(); }
}
