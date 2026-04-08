package cz.krokviak.agents.cli.skill;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public final class SkillLoader {

    private SkillLoader() {}

    /**
     * Scan a directory for .md files and parse each as a skill.
     */
    public static List<Skill> scanDirectory(Path dir) {
        if (!Files.isDirectory(dir)) return List.of();
        List<Skill> skills = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(p -> p.toString().endsWith(".md"))
                  .filter(Files::isRegularFile)
                  .forEach(p -> {
                      try {
                          Skill s = loadFromPath(p);
                          if (s != null) skills.add(s);
                      } catch (IOException ignored) {}
                  });
        } catch (IOException ignored) {}
        return skills;
    }

    /**
     * Load skills bundled as classpath resources under /skills/.
     */
    public static List<Skill> loadBuiltinSkills() {
        List<Skill> skills = new ArrayList<>();
        URL resource = SkillLoader.class.getResource("/skills/");
        if (resource == null) return skills;
        try {
            URI uri = resource.toURI();
            Path skillsDir;
            if ("jar".equals(uri.getScheme())) {
                try (FileSystem fs = FileSystems.newFileSystem(uri, Map.of())) {
                    skillsDir = fs.getPath("/skills/");
                    skills.addAll(scanDirectory(skillsDir));
                }
            } else {
                skillsDir = Path.of(uri);
                skills.addAll(scanDirectory(skillsDir));
            }
        } catch (URISyntaxException | IOException ignored) {}
        return skills;
    }

    /**
     * Load skills from .claude/skills/ in the project directory.
     */
    public static List<Skill> loadProjectSkills(Path cwd) {
        return scanDirectory(cwd.resolve(".claude").resolve("skills"));
    }

    /**
     * Load skills from ~/.claude/skills/.
     */
    public static List<Skill> loadUserSkills() {
        Path userSkills = Path.of(System.getProperty("user.home"), ".claude", "skills");
        return scanDirectory(userSkills);
    }

    /**
     * Parse a markdown file from a Path.
     */
    static Skill loadFromPath(Path p) throws IOException {
        String raw = Files.readString(p, StandardCharsets.UTF_8);
        return parseSkill(raw, p.toString());
    }

    /**
     * Parse YAML frontmatter + markdown body. Returns null if content is blank.
     */
    static Skill parseSkill(String content, String sourcePath) {
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
                        // strip surrounding quotes if present
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

        // Derive name from metadata or source path
        String name = metadata.getOrDefault("name", deriveNameFromPath(sourcePath));
        String description = metadata.getOrDefault("description", "");

        return new Skill(name, description, body, Collections.unmodifiableMap(metadata), sourcePath);
    }

    private static String deriveNameFromPath(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) return "unknown";
        String filename = Path.of(sourcePath).getFileName().toString();
        // Remove .md extension
        if (filename.endsWith(".md")) {
            filename = filename.substring(0, filename.length() - 3);
        }
        return filename;
    }
}
