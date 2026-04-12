package cz.krokviak.agents.cli.tips;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Holds the loaded {@link Tip}s and a per-id "last-shown" timestamp.
 * {@link #pickNext()} returns the tip that has been idle the longest so
 * users never see the same one twice in a row.
 *
 * <p>History is in-memory only (per session) — a spinner hint doesn't
 * need durable tracking.
 */
public final class TipRegistry {

    private final List<Tip> tips;
    private final ConcurrentHashMap<String, Long> lastShownMs = new ConcurrentHashMap<>();

    public TipRegistry(List<Tip> tips) {
        this.tips = List.copyOf(tips);
    }

    public List<Tip> all() { return tips; }
    public boolean isEmpty() { return tips.isEmpty(); }

    /** Tip that has been shown least recently (never-shown beats everything). */
    public Optional<Tip> pickNext() {
        if (tips.isEmpty()) return Optional.empty();
        // Find the minimum last-shown timestamp; 0 means never shown.
        long now = System.currentTimeMillis();
        long oldest = tips.stream()
            .mapToLong(t -> lastShownMs.getOrDefault(t.id(), 0L))
            .min().orElse(0L);
        List<Tip> candidates = tips.stream()
            .filter(t -> lastShownMs.getOrDefault(t.id(), 0L) == oldest)
            .sorted(Comparator.comparing(Tip::id))
            .toList();
        Tip chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        lastShownMs.put(chosen.id(), now);
        return Optional.of(chosen);
    }

    /** Visible for testing. */
    long lastShownOf(String id) { return lastShownMs.getOrDefault(id, 0L); }
}
