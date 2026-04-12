package cz.krokviak.agents.cli.tips;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads tips from {@code ~/.claude/tips/*.md} (user) and a builtin
 * seed list bundled with the CLI. User tips override builtin by {@code id}.
 *
 * <p>Each markdown file is one tip: YAML frontmatter (id, category) +
 * body (the displayed text). Same frontmatter shape as {@code OutputStyleLoader}.
 */
public final class TipLoader {

    private TipLoader() {}

    /** Builtin CLI tips shipped with the binary. */
    public static List<Tip> builtin() {
        return List.of(
            new Tip("plan-mode", "Use /plan to turn the agent into a coordinator that delegates.", "workflow"),
            new Tip("output-style", "Try /output-style to switch the agent's persona mid-session.", "workflow"),
            new Tip("thinking", "Enable /thinking on hard problems for visible reasoning (Claude 4+).", "workflow"),
            new Tip("compact", "Running low on context? /compact summarises history and keeps going.", "workflow"),
            new Tip("tasks", "Background jobs run under /tasks — check progress or kill from there.", "workflow"),
            new Tip("undo", "/undo drops the last turn if the agent went sideways.", "workflow"),
            new Tip("resume", "/resume lets you jump back to any prior session.", "workflow"),
            new Tip("ask-user", "Workers use the ask_user tool to interrupt and ask you to choose.", "feature")
        );
    }

    /** Merge user tips on top of builtins; user wins on id collision. */
    public static List<Tip> loadAll() {
        Map<String, Tip> merged = new LinkedHashMap<>();
        for (Tip t : builtin()) merged.put(t.id(), t);
        for (Tip t : scanUser()) merged.put(t.id(), t);
        return new ArrayList<>(merged.values());
    }

    public static Path userDir() {
        return Path.of(System.getProperty("user.home"), ".claude", "tips");
    }

    public static List<Tip> scanUser() {
        Path dir = userDir();
        if (!Files.isDirectory(dir)) return List.of();
        List<Tip> tips = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(p -> p.toString().endsWith(".md"))
                  .filter(Files::isRegularFile)
                  .forEach(p -> {
                      try {
                          Tip t = loadFromPath(p);
                          if (t != null) tips.add(t);
                      } catch (IOException ignored) {}
                  });
        } catch (IOException ignored) {}
        return tips;
    }

    public static Tip loadFromPath(Path p) throws IOException {
        String raw = Files.readString(p, StandardCharsets.UTF_8);
        return parse(raw, p.toString());
    }

    /** YAML frontmatter + body parser, delegates to shared {@link cz.krokviak.agents.agent.util.FrontmatterParser}. */
    static Tip parse(String content, String sourcePath) {
        var parsed = cz.krokviak.agents.agent.util.FrontmatterParser.parse(content);
        if (parsed == null) return null;
        String id = parsed.metadata().getOrDefault("id", deriveIdFromPath(sourcePath));
        String category = parsed.metadata().getOrDefault("category", "");
        String text = parsed.body().strip();
        if (text.isBlank()) return null;
        return new Tip(id, text, category);
    }

    private static String deriveIdFromPath(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) return "unknown";
        String filename = Path.of(sourcePath).getFileName().toString();
        if (filename.endsWith(".md")) filename = filename.substring(0, filename.length() - 3);
        return filename;
    }
}
