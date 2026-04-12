package cz.krokviak.agents.agent.engine;

import cz.krokviak.agents.agent.context.TokenEstimator;
import cz.krokviak.agents.runner.InputItem;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SnipCompactor {
    private final int snipThreshold;

    public SnipCompactor(int snipThreshold) { this.snipThreshold = snipThreshold; }
    public SnipCompactor() { this(cz.krokviak.agents.agent.AgentDefaults.SNIP_COMPACTOR_THRESHOLD); }

    public List<InputItem> snipIfNeeded(List<InputItem> history) {
        int totalTokens = TokenEstimator.estimate(history);
        if (totalTokens <= snipThreshold || history.size() <= 4) return history;

        int tokensToRemove = totalTokens - (snipThreshold / 2);
        int tokensRemoved = 0, snipIndex = 0;
        for (int i = 0; i < history.size() - 4; i++) {
            tokensRemoved += TokenEstimator.estimate(List.of(history.get(i)));
            snipIndex = i + 1;
            if (tokensRemoved >= tokensToRemove) break;
        }
        if (snipIndex > 0) {
            List<InputItem> result = new ArrayList<>();
            result.add(new InputItem.CompactionMarker(
                "Earlier conversation (" + snipIndex + " messages) removed to save context space",
                Instant.now(), snipIndex));
            result.addAll(history.subList(snipIndex, history.size()));
            return result;
        }
        return history;
    }
}
