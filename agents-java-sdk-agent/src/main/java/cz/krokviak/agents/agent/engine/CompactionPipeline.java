package cz.krokviak.agents.agent.engine;

import cz.krokviak.agents.agent.context.ContextCompactor;
import cz.krokviak.agents.agent.context.TokenEstimator;
import cz.krokviak.agents.runner.InputItem;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Three-layer compaction applied per turn.
 *
 * <p><b>Layer 1 — snip:</b> drop old tool-call args / results that have
 * been superseded. Local, cheap, O(history.size()).
 *
 * <p><b>Layer 2 — micro:</b> coalesce tiny messages into compact
 * summaries with no LLM call. Local, cheap.
 *
 * <p><b>Layer 3 — auto:</b> model-driven summary of older history. Makes
 * a blocking {@code model.call()} that can take 5–30 s. <b>Never runs
 * inside the per-turn {@link #compact} path</b>; only the explicit
 * {@link #reactiveCompact} (fired after a {@code ContextTooLongException})
 * may block on layer 3. Callers that want proactive layer 3 kick it off
 * asynchronously via {@link #compactL3Async}.
 */
public class CompactionPipeline {
    private final SnipCompactor snipCompactor;
    private final MicroCompactor microCompactor;
    private final ContextCompactor autoCompactor;
    private final int layer1Threshold;
    private final int layer2Threshold;
    private final int layer3Threshold;
    private volatile TokenEstimator calibratedEstimator;
    /** Single-flight guard so we never run two layer-3 compactions concurrently. */
    private final AtomicBoolean l3InFlight = new AtomicBoolean(false);

    public CompactionPipeline(ContextCompactor autoCompactor) {
        this(new SnipCompactor(), new MicroCompactor(), autoCompactor,
            cz.krokviak.agents.agent.AgentDefaults.COMPACTION_LAYER1_THRESHOLD,
            cz.krokviak.agents.agent.AgentDefaults.COMPACTION_LAYER2_THRESHOLD,
            cz.krokviak.agents.agent.AgentDefaults.COMPACTION_LAYER3_THRESHOLD);
    }

    public CompactionPipeline(SnipCompactor snip, MicroCompactor micro, ContextCompactor auto,
                              int l1, int l2, int l3) {
        this.snipCompactor = snip;
        this.microCompactor = micro;
        this.autoCompactor = auto;
        this.layer1Threshold = l1;
        this.layer2Threshold = l2;
        this.layer3Threshold = l3;
    }

    public void setCalibratedEstimator(TokenEstimator estimator) {
        this.calibratedEstimator = estimator;
    }

    private int estimateTokens(List<InputItem> history, String systemPrompt) {
        if (calibratedEstimator != null) {
            return calibratedEstimator.estimateCalibrated(history)
                + calibratedEstimator.estimateCalibrated(systemPrompt);
        }
        return TokenEstimator.estimate(history) + TokenEstimator.estimate(systemPrompt);
    }

    /**
     * Fast per-turn compaction. Runs layers 1 and 2 only; never blocks on
     * a model call. Layer 3, when needed, is kicked off via
     * {@link #compactL3Async} so the current turn does not pay its latency.
     */
    public List<InputItem> compact(List<InputItem> history, String systemPrompt) {
        int tokens = estimateTokens(history, systemPrompt);
        if (tokens > layer1Threshold) {
            history = snipCompactor.snipIfNeeded(history);
            tokens = estimateTokens(history, systemPrompt);
        }
        if (tokens > layer2Threshold) {
            history = microCompactor.compact(history);
            tokens = estimateTokens(history, systemPrompt);
        }
        // Layer 3 is now async-only — see compactL3Async().
        return history;
    }

    /**
     * Last-resort compaction after {@code ContextTooLongException}. Runs
     * all three layers synchronously so the next turn's prompt actually
     * fits. Callers should expect up to 30 s of blocking.
     */
    public List<InputItem> reactiveCompact(List<InputItem> history, String systemPrompt) {
        history = snipCompactor.snipIfNeeded(history);
        history = microCompactor.compact(history);
        if (autoCompactor != null) history = autoCompactor.forceCompact(history);
        return history;
    }

    /**
     * Kick off a layer-3 auto-compaction on a virtual thread when the
     * history is over the layer-3 threshold and no other compaction is in
     * flight. Returns immediately; callers pick up the reduced history on
     * the next turn when the background job has finished.
     *
     * @param historyRef mutable list updated in-place when compaction completes
     */
    public void compactL3Async(List<InputItem> historyRef, String systemPrompt) {
        if (autoCompactor == null) return;
        int tokens = estimateTokens(historyRef, systemPrompt);
        if (tokens <= layer3Threshold) return;
        if (!l3InFlight.compareAndSet(false, true)) return;
        Thread.ofVirtual().name("layer3-compact").start(() -> {
            try {
                var snapshot = List.copyOf(historyRef);
                var compacted = autoCompactor.compactIfNeeded(snapshot, systemPrompt);
                if (compacted != snapshot && compacted.size() < historyRef.size()) {
                    synchronized (historyRef) {
                        historyRef.clear();
                        historyRef.addAll(compacted);
                    }
                }
            } catch (Exception ignored) {
                // Never crash the background compaction; next trigger retries.
            } finally {
                l3InFlight.set(false);
            }
        });
    }

    /** Visible for testing — true while a background L3 job is running. */
    boolean isL3InFlight() { return l3InFlight.get(); }
}
