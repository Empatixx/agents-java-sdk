package cz.krokviak.agents.agent.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parser for the common {@code ---\nkey: value\n---\nbody} markdown
 * frontmatter format used by skills, output styles, and tips.
 *
 * <p>Handles:
 * <ul>
 *   <li>Missing frontmatter — whole content becomes body, metadata empty.</li>
 *   <li>Unclosed frontmatter — treated as plain body (no metadata).</li>
 *   <li>Single- and double-quoted values (surrounding quotes stripped).</li>
 *   <li>Empty / blank input — returns {@code null}.</li>
 * </ul>
 *
 * <p>This class was extracted to end triple-duplication across
 * {@code SkillLoader}, {@code OutputStyleLoader}, {@code TipLoader}.
 */
public final class FrontmatterParser {

    private FrontmatterParser() {}

    /**
     * Parsed result: metadata from YAML frontmatter + body markdown.
     *
     * @param metadata key-value pairs in insertion order; empty if no frontmatter
     * @param body     markdown content after the frontmatter (stripped of leading whitespace),
     *                 or the whole input if no frontmatter was present
     */
    public record Parsed(Map<String, String> metadata, String body) {}

    /**
     * Parse frontmatter + body. Returns {@code null} for {@code null} or blank input.
     */
    public static Parsed parse(String content) {
        if (content == null || content.isBlank()) return null;

        Map<String, String> metadata = new LinkedHashMap<>();
        String body;

        if (content.startsWith("---")) {
            int end = content.indexOf("\n---", 3);
            if (end != -1) {
                String yaml = content.substring(3, end).strip();
                body = content.substring(end + 4).stripLeading();
                for (String line : yaml.split("\n")) {
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        String key = line.substring(0, colon).strip();
                        String val = stripQuotes(line.substring(colon + 1).strip());
                        metadata.put(key, val);
                    }
                }
            } else {
                body = content;
            }
        } else {
            body = content;
        }

        // LinkedHashMap preserves insertion order; callers may rely on original frontmatter order.
        return new Parsed(java.util.Collections.unmodifiableMap(metadata), body);
    }

    private static String stripQuotes(String val) {
        if (val.length() >= 2) {
            if (val.startsWith("\"") && val.endsWith("\"")) {
                return val.substring(1, val.length() - 1);
            }
            if (val.startsWith("'") && val.endsWith("'")) {
                return val.substring(1, val.length() - 1);
            }
        }
        return val;
    }
}
