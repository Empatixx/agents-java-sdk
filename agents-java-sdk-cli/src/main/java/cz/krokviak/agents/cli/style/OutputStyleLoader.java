package cz.krokviak.agents.cli.style;

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
 * Scans {@code ~/.claude/output-styles/*.md} (user) and {@code <cwd>/.krok/output-styles/*.md}
 * (project, overrides user on name collision) and parses each as an {@link OutputStyle}.
 *
 * <p>Same frontmatter format as claude-code: {@code ---\nname: X\ndescription: Y\n---\n<body>}.
 */
public final class OutputStyleLoader {

    private OutputStyleLoader() {}

    /** Load user + project styles, with project winning on name collision. */
    public static List<OutputStyle> load(Path cwd) {
        Map<String, OutputStyle> merged = new LinkedHashMap<>();
        for (OutputStyle s : scanDirectory(userDir())) merged.put(s.name(), s);
        for (OutputStyle s : scanDirectory(projectDir(cwd))) merged.put(s.name(), s);
        return new ArrayList<>(merged.values());
    }

    public static Path userDir() {
        return Path.of(System.getProperty("user.home"), ".claude", "output-styles");
    }

    public static Path projectDir(Path cwd) {
        return cwd.resolve(".krok").resolve("output-styles");
    }

    public static List<OutputStyle> scanDirectory(Path dir) {
        if (!Files.isDirectory(dir)) return List.of();
        List<OutputStyle> styles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(p -> p.toString().endsWith(".md"))
                  .filter(Files::isRegularFile)
                  .forEach(p -> {
                      try {
                          OutputStyle s = loadFromPath(p);
                          if (s != null) styles.add(s);
                      } catch (IOException ignored) {}
                  });
        } catch (IOException ignored) {}
        return styles;
    }

    public static OutputStyle loadFromPath(Path p) throws IOException {
        String raw = Files.readString(p, StandardCharsets.UTF_8);
        return parse(raw, p.toString());
    }

    /** YAML frontmatter + body parser (same shape as SkillLoader.parseSkill). */
    static OutputStyle parse(String content, String sourcePath) {
        if (content == null || content.isBlank()) return null;
        Map<String, String> metadata = new LinkedHashMap<>();
        String body;

        if (content.startsWith("---")) {
            int end = content.indexOf("\n---", 3);
            if (end != -1) {
                String yamlBlock = content.substring(3, end).strip();
                body = content.substring(end + 4).stripLeading();
                for (String line : yamlBlock.split("\n")) {
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        String key = line.substring(0, colon).strip();
                        String val = line.substring(colon + 1).strip();
                        if (val.length() >= 2 && val.startsWith("\"") && val.endsWith("\"")) {
                            val = val.substring(1, val.length() - 1);
                        } else if (val.length() >= 2 && val.startsWith("'") && val.endsWith("'")) {
                            val = val.substring(1, val.length() - 1);
                        }
                        metadata.put(key, val);
                    }
                }
            } else {
                body = content;
            }
        } else {
            body = content;
        }

        String name = metadata.getOrDefault("name", deriveNameFromPath(sourcePath));
        String description = metadata.getOrDefault("description", "");
        return new OutputStyle(name, description, body.strip(), sourcePath);
    }

    private static String deriveNameFromPath(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) return "unknown";
        String filename = Path.of(sourcePath).getFileName().toString();
        if (filename.endsWith(".md")) filename = filename.substring(0, filename.length() - 3);
        return filename;
    }
}
