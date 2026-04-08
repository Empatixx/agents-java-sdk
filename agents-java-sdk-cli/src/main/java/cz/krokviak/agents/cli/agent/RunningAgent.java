package cz.krokviak.agents.cli.agent;

import cz.krokviak.agents.cli.render.AgentStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class RunningAgent {
    private final String name;
    private final String description;
    private final Instant startTime;
    private volatile AgentStatus status;
    private volatile Thread threadRef;
    private final AtomicInteger toolUseCount = new AtomicInteger(0);
    private final AtomicInteger tokenCount = new AtomicInteger(0);
    private final StringBuilder outputCollector = new StringBuilder();
    private final boolean background;

    public RunningAgent(String name, String description, boolean background) {
        this.name = name;
        this.description = description;
        this.background = background;
        this.startTime = Instant.now();
        this.status = AgentStatus.STARTING;
    }

    public String name() { return name; }
    public String description() { return description; }
    public AgentStatus status() { return status; }
    public Instant startTime() { return startTime; }
    public Thread threadRef() { return threadRef; }
    public int toolUseCount() { return toolUseCount.get(); }
    public int tokenCount() { return tokenCount.get(); }

    public void setThreadRef(Thread thread) { this.threadRef = thread; }
    public void setStatus(AgentStatus status) { this.status = status; }

    public boolean isBackground() { return background; }

    public String elapsed() {
        long secs = Duration.between(startTime, Instant.now()).toSeconds();
        return secs < 60 ? secs + "s" : (secs / 60) + "m " + (secs % 60) + "s";
    }

    public void addToolUse() { toolUseCount.incrementAndGet(); }

    public void addTokens(int count) { tokenCount.addAndGet(count); }

    public synchronized void appendOutput(String text) {
        outputCollector.append(text);
    }

    public synchronized String getOutput() {
        return outputCollector.toString();
    }
}
