package cz.krokviak.agents.cli.style;

/**
 * A user-defined alternative system-prompt body that persistently modifies
 * how the agent responds. Loaded from {@code ~/.claude/output-styles/*.md}
 * or {@code .krok/output-styles/*.md}; selected via {@code /output-style}.
 *
 * @param name           Style identifier (from frontmatter or filename stem).
 * @param description    Short human-readable summary.
 * @param systemPrompt   Markdown body appended to the base system prompt.
 * @param source         File path the style was loaded from.
 */
public record OutputStyle(
    String name,
    String description,
    String systemPrompt,
    String source
) {}
