package cz.krokviak.agents.agent.engine;

import cz.krokviak.agents.runner.InputItem;
import java.util.ArrayList;
import java.util.List;

public class MicroCompactor {
    private final int keepRecentToolResults;

    public MicroCompactor(int keepRecentToolResults) { this.keepRecentToolResults = keepRecentToolResults; }
    public MicroCompactor() { this(cz.krokviak.agents.agent.AgentDefaults.MICRO_COMPACTOR_KEEP_RECENT); }

    public List<InputItem> compact(List<InputItem> history) {
        int toolResultCount = 0;
        for (int i = history.size() - 1; i >= 0; i--)
            if (history.get(i) instanceof InputItem.ToolResult) toolResultCount++;
        if (toolResultCount <= keepRecentToolResults) return history;

        boolean[] keep = new boolean[history.size()];
        int seen = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i) instanceof InputItem.ToolResult) { seen++; keep[i] = seen <= keepRecentToolResults; }
            else keep[i] = true;
        }
        List<InputItem> result = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            if (keep[i]) result.add(history.get(i));
            else if (history.get(i) instanceof InputItem.ToolResult tr)
                result.add(new InputItem.ToolResult(tr.toolCallId(), tr.toolName(), "[result truncated]"));
        }
        return result;
    }
}
