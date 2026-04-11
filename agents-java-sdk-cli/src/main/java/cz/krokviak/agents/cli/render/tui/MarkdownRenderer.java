package cz.krokviak.agents.cli.render.tui;

import dev.tamboui.style.Color;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * Converts a markdown line into a styled Tamboui Element.
 * Handles: **bold**, *italic*, `code`, headers (#), list bullets (- *).
 */
public final class MarkdownRenderer {
    private MarkdownRenderer() {}

    // Inline patterns — order matters (longer matches first)
    private static final Pattern BOLD_ITALIC = Pattern.compile("\\*\\*\\*(.+?)\\*\\*\\*");
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("(?<!\\*)\\*(.+?)\\*(?!\\*)");
    private static final Pattern CODE = Pattern.compile("`([^`]+)`");

    /**
     * Render a single line of markdown as a Tamboui row element.
     * Prefix (indent) is prepended as-is.
     */
    public static StyledElement<?> renderLine(String line, String prefix) {
        if (line == null || line.isEmpty()) {
            return text(prefix);
        }

        // Headers: # ## ###
        if (line.startsWith("### ")) {
            return row(text(prefix).fit(), text(line.substring(4)).bold().italic().fit());
        }
        if (line.startsWith("## ")) {
            return row(text(prefix).fit(), text(line.substring(3)).bold().underlined().fit());
        }
        if (line.startsWith("# ")) {
            return row(text(prefix).fit(), text(line.substring(2)).bold().underlined().fit());
        }

        // List bullets: - or *
        String bulletPrefix = null;
        String bulletContent = line;
        if (line.matches("^\\s*[-*]\\s+.*")) {
            int dashIdx = line.indexOf('-') != -1 ? line.indexOf('-') : line.indexOf('*');
            bulletPrefix = line.substring(0, dashIdx) + "• ";
            bulletContent = line.substring(dashIdx + 1).trim();
        }

        // Numbered lists: 1. 2. etc
        if (line.matches("^\\s*\\d+\\.\\s+.*")) {
            int dotIdx = line.indexOf('.');
            bulletPrefix = line.substring(0, dotIdx + 1) + " ";
            bulletContent = line.substring(dotIdx + 1).trim();
        }

        // Parse inline markdown
        List<Element> spans = parseInline(bulletContent);

        if (bulletPrefix != null) {
            List<Element> all = new ArrayList<>();
            all.add(text(prefix + bulletPrefix).fit());
            all.addAll(spans);
            return row(all.toArray(Element[]::new));
        }

        if (spans.size() == 1 && prefix.isEmpty() && spans.getFirst() instanceof StyledElement<?> se) {
            return se;
        }

        List<Element> all = new ArrayList<>();
        if (!prefix.isEmpty()) all.add(text(prefix).fit());
        all.addAll(spans);
        return row(all.toArray(Element[]::new));
    }

    /**
     * Parse inline markdown (bold, italic, code) into styled spans.
     */
    static List<Element> parseInline(String text) {
        List<Element> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;

        // Combined pattern to find all inline markers
        Pattern combined = Pattern.compile(
            "\\*\\*\\*(.+?)\\*\\*\\*" +   // group 1: bold+italic
            "|\\*\\*(.+?)\\*\\*" +          // group 2: bold
            "|(?<!\\*)\\*(.+?)\\*(?!\\*)" + // group 3: italic
            "|`([^`]+)`"                     // group 4: code
        );

        Matcher m = combined.matcher(text);
        int lastEnd = 0;

        while (m.find()) {
            // Add plain text before this match
            if (m.start() > lastEnd) {
                result.add(text(text.substring(lastEnd, m.start())).fit());
            }

            if (m.group(1) != null) {
                // ***bold italic***
                result.add(text(m.group(1)).bold().italic().fit());
            } else if (m.group(2) != null) {
                // **bold**
                result.add(text(m.group(2)).bold().fit());
            } else if (m.group(3) != null) {
                // *italic*
                result.add(text(m.group(3)).italic().fit());
            } else if (m.group(4) != null) {
                // `code`
                result.add(text(m.group(4)).fg(Color.CYAN).fit());
            }

            lastEnd = m.end();
        }

        // Remaining plain text
        if (lastEnd < text.length()) {
            result.add(text(text.substring(lastEnd)).fit());
        }

        if (result.isEmpty()) {
            result.add(text(text).fit());
        }

        return result;
    }
}
