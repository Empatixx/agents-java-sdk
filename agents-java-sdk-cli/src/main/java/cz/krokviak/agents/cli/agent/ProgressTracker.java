package cz.krokviak.agents.cli.agent;

import java.util.concurrent.atomic.AtomicInteger;

public class ProgressTracker {
    private final AtomicInteger toolUseCount = new AtomicInteger(0);
    private final AtomicInteger tokenCount = new AtomicInteger(0);
    private volatile String currentActivity = "";

    public void updateActivity(String description) {
        this.currentActivity = description;
    }

    public void addToolUse() { toolUseCount.incrementAndGet(); }

    public void addTokens(int count) { tokenCount.addAndGet(count); }

    public int getToolUseCount() { return toolUseCount.get(); }

    public int getTokenCount() { return tokenCount.get(); }

    public String getCurrentActivity() { return currentActivity; }

    public String getProgressLine() {
        int tools = toolUseCount.get();
        int tokens = tokenCount.get();
        String toolPart = tools + " tool call" + (tools != 1 ? "s" : "");
        String tokenPart;
        if (tokens >= 1000) {
            tokenPart = String.format("%.1fK tokens", tokens / 1000.0);
        } else {
            tokenPart = tokens + " tokens";
        }
        return toolPart + ", " + tokenPart;
    }
}
