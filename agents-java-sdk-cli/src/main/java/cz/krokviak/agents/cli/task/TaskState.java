package cz.krokviak.agents.cli.task;

import java.time.Duration;
import java.time.Instant;

public class TaskState {
    public enum Status { PENDING, RUNNING, COMPLETED, FAILED, KILLED }

    private final String id;
    private final String description;
    private final Instant startTime;
    private volatile Status status;
    private volatile String result;
    private volatile String error;
    private volatile Instant endTime;
    private volatile Thread thread;
    private volatile int toolUseCount;
    private volatile int tokenCount;

    public TaskState(String id, String description) {
        this(id, description, Status.RUNNING);
    }

    public TaskState(String id, String description, Status initialStatus) {
        this.id = id;
        this.description = description;
        this.startTime = Instant.now();
        this.status = initialStatus;
        if (initialStatus == Status.COMPLETED || initialStatus == Status.FAILED || initialStatus == Status.KILLED) {
            this.endTime = this.startTime;
        }
    }

    public String id() { return id; }
    public String description() { return description; }
    public Instant startTime() { return startTime; }
    public Status status() { return status; }
    public String result() { return result; }
    public String error() { return error; }
    public int toolUseCount() { return toolUseCount; }

    public void setThread(Thread thread) { this.thread = thread; }
    public void start() { this.status = Status.RUNNING; this.endTime = null; }
    public void setPending() { this.status = Status.PENDING; this.endTime = null; }
    public void setResult(String result) { this.result = result; }
    public void setError(String error) { this.error = error; }
    public void complete(String result) { this.result = result; this.status = Status.COMPLETED; this.endTime = Instant.now(); }
    public void fail(String error) { this.error = error; this.status = Status.FAILED; this.endTime = Instant.now(); }
    public void kill() { this.status = Status.KILLED; this.endTime = Instant.now(); if (thread != null) thread.interrupt(); }

    public String formatDuration() {
        var end = endTime != null ? endTime : Instant.now();
        long secs = Duration.between(startTime, end).toSeconds();
        return secs < 60 ? secs + "s" : (secs / 60) + "m " + (secs % 60) + "s";
    }
}
