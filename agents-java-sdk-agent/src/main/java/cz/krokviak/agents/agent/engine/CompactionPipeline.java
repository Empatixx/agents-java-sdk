package cz.krokviak.agents.agent.engine;

import cz.krokviak.agents.agent.context.ContextCompactor;
import cz.krokviak.agents.agent.context.TokenEstimator;
import cz.krokviak.agents.runner.InputItem;
import java.util.List;

public class CompactionPipeline {
    private final SnipCompactor snipCompactor;
    private final MicroCompactor microCompactor;
    private final ContextCompactor autoCompactor;
    private final int layer1Threshold;
    private final int layer2Threshold;
    private final int layer3Threshold;
    private volatile TokenEstimator calibratedEstimator;

    public CompactionPipeline(ContextCompactor autoCompactor) {
        this(new SnipCompactor(), new MicroCompactor(), autoCompactor,
            cz.krokviak.agents.agent.AgentDefaults.COMPACTION_LAYER1_THRESHOLD,
            cz.krokviak.agents.agent.AgentDefaults.COMPACTION_LAYER2_THRESHOLD,
            cz.krokviak.agents.agent.AgentDefaults.COMPACTION_LAYER3_THRESHOLD);
    }

    public CompactionPipeline(SnipCompactor snip, MicroCompactor micro, ContextCompactor auto,
                              int l1, int l2, int l3) {
        this.snipCompactor = snip; this.microCompactor = micro; this.autoCompactor = auto;
        this.layer1Threshold = l1; this.layer2Threshold = l2; this.layer3Threshold = l3;
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

    public List<InputItem> compact(List<InputItem> history, String systemPrompt) {
        int tokens = estimateTokens(history, systemPrompt);
        if (tokens > layer1Threshold) { history = snipCompactor.snipIfNeeded(history);
            tokens = estimateTokens(history, systemPrompt); }
        if (tokens > layer2Threshold) { history = microCompactor.compact(history);
            tokens = estimateTokens(history, systemPrompt); }
        if (tokens > layer3Threshold && autoCompactor != null) history = autoCompactor.compactIfNeeded(history, systemPrompt);
        return history;
    }

    public List<InputItem> reactiveCompact(List<InputItem> history, String systemPrompt) {
        history = snipCompactor.snipIfNeeded(history);
        history = microCompactor.compact(history);
        if (autoCompactor != null) history = autoCompactor.forceCompact(history);
        return history;
    }
}
