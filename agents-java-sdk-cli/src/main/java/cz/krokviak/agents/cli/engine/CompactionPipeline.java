package cz.krokviak.agents.cli.engine;

import cz.krokviak.agents.cli.context.ContextCompactor;
import cz.krokviak.agents.cli.context.TokenEstimator;
import cz.krokviak.agents.runner.InputItem;
import java.util.List;

public class CompactionPipeline {
    private final SnipCompactor snipCompactor;
    private final MicroCompactor microCompactor;
    private final ContextCompactor autoCompactor;
    private final int layer1Threshold;
    private final int layer2Threshold;
    private final int layer3Threshold;

    public CompactionPipeline(ContextCompactor autoCompactor) {
        this(new SnipCompactor(), new MicroCompactor(), autoCompactor, 40_000, 60_000, 80_000);
    }

    public CompactionPipeline(SnipCompactor snip, MicroCompactor micro, ContextCompactor auto,
                              int l1, int l2, int l3) {
        this.snipCompactor = snip; this.microCompactor = micro; this.autoCompactor = auto;
        this.layer1Threshold = l1; this.layer2Threshold = l2; this.layer3Threshold = l3;
    }

    public List<InputItem> compact(List<InputItem> history, String systemPrompt) {
        int tokens = TokenEstimator.estimate(history) + TokenEstimator.estimate(systemPrompt);
        if (tokens > layer1Threshold) { history = snipCompactor.snipIfNeeded(history);
            tokens = TokenEstimator.estimate(history) + TokenEstimator.estimate(systemPrompt); }
        if (tokens > layer2Threshold) { history = microCompactor.compact(history);
            tokens = TokenEstimator.estimate(history) + TokenEstimator.estimate(systemPrompt); }
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
