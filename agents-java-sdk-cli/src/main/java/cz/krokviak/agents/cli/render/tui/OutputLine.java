package cz.krokviak.agents.cli.render.tui;

import cz.krokviak.agents.cli.render.AgentStatus;
import cz.krokviak.agents.cli.render.ToolCallStatus;
import dev.tamboui.style.Color;
import dev.tamboui.toolkit.element.StyledElement;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * Sealed type for output log lines. Each variant renders Claude Code style.
 * Lines can be updated in-place via CliState.updateLast().
 */
public sealed interface OutputLine {

    StyledElement<?> render();

    // ---- Text ----

    record Text(String content) implements OutputLine {
        public StyledElement<?> render() {
            return row(spacer(2), text(content).fit());
        }
    }

    record UserMessage(String content) implements OutputLine {
        public StyledElement<?> render() {
            return row(spacer(1), text("❯ ").bold().cyan().fit(), text(content).bold().fit());
        }
    }

    record Dim(String content) implements OutputLine {
        public StyledElement<?> render() {
            return row(spacer(2), text(content).dim().fit());
        }
    }

    record Error(String message) implements OutputLine {
        public StyledElement<?> render() {
            return row(spacer(2), text("✗ " + message).red().bold().fit());
        }
    }

    // ---- Tools ----

    /** Tool call with updatable status. indented=true for sub-agent tools. */
    record ToolCall(String name, String args, ToolCallStatus status, boolean indented) implements OutputLine {
        public ToolCall(String name, String args, ToolCallStatus status) {
            this(name, args, status, false);
        }

        public StyledElement<?> render() {
            int indent = indented ? 4 : 1;
            var icon = switch (status) {
                case RUNNING -> text("● ").cyan().fit();
                case COMPLETED -> text("● ").green().fit();
                case FAILED -> text("✗ ").red().fit();
                case PENDING -> text("○ ").dim().fit();
            };
            return row(spacer(indent), icon, text(name).bold().fit(), text("(" + trunc(args, 80) + ")").dim().fit());
        }

        public ToolCall withStatus(ToolCallStatus s) { return new ToolCall(name, args, s, indented); }
    }

    /**
     * Compact tool result — preview lines + collapse hint + timing, all in one element.
     * Renders as:
     *   ⎿  first line
     *   ⎿  second line
     *   ⎿  (N more lines, ctrl+o to expand) · 2.5s
     */
    record ToolOutput(String[] previewLines, int totalLines, long ms, boolean expanded) implements OutputLine {
        public StyledElement<?> render() {
            var items = new java.util.ArrayList<dev.tamboui.toolkit.element.Element>();
            for (String line : previewLines) {
                items.add(row(spacer(2), text("⎿  " + line).dim().fit()));
            }
            // Footer: collapse hint + timing
            String time = ms >= 1000 ? String.format("%.1fs", ms / 1000.0) : ms + "ms";
            if (!expanded && totalLines > previewLines.length) {
                int hidden = totalLines - previewLines.length;
                items.add(row(spacer(2), text("⎿  (" + hidden + " more lines, ctrl+o to expand) · " + time).dim().fit()));
            } else {
                items.add(row(spacer(2), text("⎿  (" + time + ")").dim().fit()));
            }
            return column(items.toArray(new dev.tamboui.toolkit.element.Element[0]));
        }

        public ToolOutput withExpanded(String[] allLines) {
            return new ToolOutput(allLines, totalLines, ms, true);
        }
    }

    // ---- Agent ----

    record Agent(String name, AgentStatus status, String detail) implements OutputLine {
        public StyledElement<?> render() {
            return switch (status) {
                case STARTING, RUNNING, WAITING -> row(
                    spacer(1),
                    text("● ").cyan().fit(),
                    text(name).bold().cyan().fit(),
                    detail != null ? text("  " + detail).dim().fit() : text("").fit()
                );
                case COMPLETED -> row(
                    spacer(1),
                    text("✓ ").green().fit(),
                    text(name).bold().fit(),
                    detail != null ? text("  " + detail).dim().fit() : text("").fit()
                );
                case FAILED, KILLED -> row(
                    spacer(1),
                    text("✗ ").red().fit(),
                    text(name).bold().fit(),
                    detail != null ? text("  " + detail).dim().fit() : text("").fit()
                );
            };
        }

        public Agent withStatus(AgentStatus s, String d) { return new Agent(name, s, d); }
    }

    // ---- Diff ----

    record DiffLine(String line) implements OutputLine {
        public StyledElement<?> render() {
            if (line.startsWith("+") && !line.startsWith("+++"))
                return text("  " + line).fg(Color.GREEN).fill();
            if (line.startsWith("-") && !line.startsWith("---"))
                return text("  " + line).fg(Color.RED).fill();
            if (line.startsWith("@@"))
                return row(spacer(2), text(line).cyan().fit());
            return row(spacer(2), text(line).dim().fit());
        }
    }

    record PermissionDenied(String toolName) implements OutputLine {
        public StyledElement<?> render() {
            return row(spacer(2), text("⚠ Permission denied: ").yellow().fit(), text(toolName).bold().fit());
        }
    }

    private static String trunc(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
