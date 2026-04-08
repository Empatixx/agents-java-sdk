package cz.krokviak.agents.cli.render;

import java.time.Instant;

/**
 * Represents one line of output with associated style (ANSI escape codes) and a timestamp.
 * Immutable value type used by TuiRenderer to build the scrollable output history.
 */
public record OutputLine(
    String text,
    String style,
    Instant timestamp
) {

    /** Convenience factory: plain unstyled line with current timestamp. */
    public static OutputLine plain(String text) {
        return new OutputLine(text, "", Instant.now());
    }

    /** Convenience factory: styled line with current timestamp. */
    public static OutputLine styled(String text, String style) {
        return new OutputLine(text, style, Instant.now());
    }
}
