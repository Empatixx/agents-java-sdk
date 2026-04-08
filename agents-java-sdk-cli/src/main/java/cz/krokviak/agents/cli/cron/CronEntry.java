package cz.krokviak.agents.cli.cron;

import java.time.Instant;

public record CronEntry(
    String id,
    String schedule,
    String prompt,
    String agentName,
    Instant createdAt,
    Instant lastRunAt,
    boolean enabled
) {
    public CronEntry withLastRunAt(Instant lastRunAt) {
        return new CronEntry(id, schedule, prompt, agentName, createdAt, lastRunAt, enabled);
    }

    public CronEntry withEnabled(boolean enabled) {
        return new CronEntry(id, schedule, prompt, agentName, createdAt, lastRunAt, enabled);
    }

    @Override
    public String toString() {
        return "CronEntry{id=" + id + ", schedule='" + schedule + "', prompt='" + prompt +
            "', agentName=" + agentName + ", enabled=" + enabled +
            (lastRunAt != null ? ", lastRun=" + lastRunAt : "") + "}";
    }
}
