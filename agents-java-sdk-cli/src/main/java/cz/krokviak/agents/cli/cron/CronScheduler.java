package cz.krokviak.agents.cli.cron;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class CronScheduler {
    private static final Logger log = LoggerFactory.getLogger(CronScheduler.class);
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private final Map<String, CronEntry> entries = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger(0);
    private final Consumer<CronEntry> onFire;

    public CronScheduler(Consumer<CronEntry> onFire) {
        this.onFire = onFire;
    }

    /**
     * Parses interval strings like "every 5m", "every 1h", "every 30s", "every 2h30m".
     * Also accepts bare durations like "5m", "1h", "30s".
     */
    public static Duration parseInterval(String schedule) {
        if (schedule == null || schedule.isBlank()) {
            throw new IllegalArgumentException("Schedule cannot be blank");
        }
        String s = schedule.trim().toLowerCase();
        if (s.startsWith("every ")) {
            s = s.substring(6).trim();
        }
        // Parse compound like "2h30m", "1h", "30m", "45s"
        long totalSeconds = 0;
        int i = 0;
        boolean parsed = false;
        while (i < s.length()) {
            int start = i;
            while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            if (i == start) break; // no digit found
            long value = Long.parseLong(s.substring(start, i));
            if (i >= s.length()) break;
            char unit = s.charAt(i);
            i++;
            totalSeconds += switch (unit) {
                case 'h' -> value * 3600;
                case 'm' -> value * 60;
                case 's' -> value;
                default -> throw new IllegalArgumentException("Unknown time unit '" + unit + "' in: " + schedule);
            };
            parsed = true;
        }
        if (!parsed || totalSeconds <= 0) {
            throw new IllegalArgumentException("Cannot parse schedule: " + schedule);
        }
        return Duration.ofSeconds(totalSeconds);
    }

    public CronEntry schedule(String schedule, String prompt, String agentName) {
        Duration interval = parseInterval(schedule);
        String id = "cron-" + counter.incrementAndGet();
        CronEntry entry = new CronEntry(id, schedule, prompt, agentName, Instant.now(), null, true);
        entries.put(id, entry);

        ScheduledFuture<?> future = executor.scheduleAtFixedRate(() -> {
            CronEntry current = entries.get(id);
            if (current == null || !current.enabled()) return;
            CronEntry updated = current.withLastRunAt(Instant.now());
            entries.put(id, updated);
            if (onFire != null) {
                try {
                    onFire.accept(updated);
                } catch (Exception e) {
                    log.warn( "Cron job " + id + " failed", e);
                }
            }
        }, interval.toSeconds(), interval.toSeconds(), TimeUnit.SECONDS);

        futures.put(id, future);
        return entry;
    }

    public boolean cancel(String id) {
        CronEntry entry = entries.remove(id);
        if (entry == null) return false;
        ScheduledFuture<?> future = futures.remove(id);
        if (future != null) future.cancel(false);
        return true;
    }

    public List<CronEntry> list() {
        return entries.values().stream()
            .sorted(Comparator.comparing(CronEntry::id))
            .toList();
    }

    public CronEntry get(String id) {
        return entries.get(id);
    }

    public void shutdown() {
        executor.shutdown();
    }
}
