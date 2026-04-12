package cz.krokviak.agents.agent.plan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Persistent plan file storage. Plans live at ~/.krok/plans/{slug}.md
 */
public final class PlanStore {

    private static final String[] ADJECTIVES = {
        "happy", "swift", "calm", "bold", "warm", "cool", "keen", "fair",
        "wise", "neat", "soft", "bright", "fresh", "clear", "quick"
    };
    private static final String[] VERBS = {
        "seeking", "running", "flying", "dancing", "glowing", "rising",
        "flowing", "spinning", "jumping", "soaring", "rolling", "drifting"
    };
    private static final String[] NOUNS = {
        "sun", "moon", "star", "wave", "leaf", "wind", "rain", "stone",
        "fire", "snow", "dawn", "dusk", "cloud", "river", "hill"
    };

    private final Path plansDir;
    private final Random rng = new Random();
    private String currentSlug;

    public PlanStore() {
        this.plansDir = Path.of(System.getProperty("user.home"), ".krok", "plans");
    }

    /** Create a new plan with a generated slug. Returns the slug. */
    public String createPlan() throws IOException {
        Files.createDirectories(plansDir);
        String slug;
        do {
            slug = ADJECTIVES[rng.nextInt(ADJECTIVES.length)] + "-"
                + VERBS[rng.nextInt(VERBS.length)] + "-"
                + NOUNS[rng.nextInt(NOUNS.length)];
        } while (Files.exists(plansDir.resolve(slug + ".md")));

        Files.writeString(planPath(slug), "# Plan\n\n_Write your plan here._\n");
        currentSlug = slug;
        return slug;
    }

    public void savePlan(String slug, String content) throws IOException {
        Files.createDirectories(plansDir);
        Files.writeString(planPath(slug), content);
    }

    public String loadPlan(String slug) throws IOException {
        Path p = planPath(slug);
        return Files.exists(p) ? Files.readString(p) : null;
    }

    public Path planPath(String slug) {
        return plansDir.resolve(slug + ".md");
    }

    public String currentSlug() { return currentSlug; }
    public void setCurrentSlug(String slug) { this.currentSlug = slug; }

    public String currentPlanPath() {
        return currentSlug != null ? planPath(currentSlug).toString() : null;
    }
}
