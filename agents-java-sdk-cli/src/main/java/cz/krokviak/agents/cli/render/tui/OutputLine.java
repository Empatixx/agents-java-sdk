package cz.krokviak.agents.cli.render.tui;

import cz.krokviak.agents.cli.render.AgentStatus;
import cz.krokviak.agents.cli.render.ToolCallStatus;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.toolkit.element.StyledElement;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * Sealed type for output log lines. Each variant renders Claude Code style.
 * Lines can be updated in-place via CliState.updateLast().
 */
public sealed interface OutputLine {

    StyledElement<?> render();

    // ---- Text ----

    /** Plain text (assistant response). */
    record Text(String content) implements OutputLine {
        public StyledElement<?> render() {
            return row(spacer(2), text(content).fit());
        }
    }

    /** User input with ❯ prompt. */
    record UserMessage(String content) implements OutputLine {
        public StyledElement<?> render() {
            return row(spacer(1), text("❯ ").bold().cyan().fit(), text(content).bold().fit());
        }
    }

    /** Dim info text. */
    record Dim(String content) implements OutputLine {
        public StyledElement<?> render() {
            return row(spacer(2), text(content).dim().fit());
        }
    }

    /** Error message. */
    record Error(String message) implements OutputLine {
        public StyledElement<?> render() {
            return row(spacer(2), text("✗ " + message).red().bold().fit());
        }
    }

    // ---- Tools ----

    /** Tool call — green ● when done, cyan ● when running. */
    record ToolCall(String name, String args, ToolCallStatus status) implements OutputLine {
        public StyledElement<?> render() {
            return switch (status) {
                case RUNNING -> row(
                    spacer(1),
                    text("● ").cyan().fit(),
                    text(name).bold().fit(),
                    text("(" + truncate(args, 80) + ")").dim().fit()
                );
                case COMPLETED -> row(
                    spacer(1),
                    text("● ").green().fit(),
                    text(name).bold().fit(),
                    text("(" + truncate(args, 80) + ")").dim().fit()
                );
                case FAILED -> row(
                    spacer(1),
                    text("✗ ").red().fit(),
                    text(name).bold().fit(),
                    text("(" + truncate(args, 80) + ")").dim().fit()
                );
                case PENDING -> row(
                    spacer(1),
                    text("○ ").dim().fit(),
                    text(name).bold().fit(),
                    text("(" + truncate(args, 80) + ")").dim().fit()
                );
            };
        }

        public ToolCall withStatus(ToolCallStatus s) { return new ToolCall(name, args, s); }
    }

    /** Tool result line with ⎿ prefix. */
    record Result(String line) implements OutputLine {
        public StyledElement<?> render() {
            return row(spacer(2), text("⎿  " + line).dim().fit());
        }
    }

    /** Collapse hint (ctrl+o). */
    record CollapseHint(int hiddenLines) implements OutputLine {
        public StyledElement<?> render() {
            return row(spacer(2), text("⎿  (" + hiddenLines + " more lines, ctrl+o to expand)").dim().fit());
        }
    }

    /** Timing. */
    record Timing(long ms) implements OutputLine {
        public StyledElement<?> render() {
            String time = ms >= 1000 ? String.format("%.1fs", ms / 1000.0) : ms + "ms";
            return row(spacer(2), text("⎿  (" + time + ")").dim().fit());
        }
    }

    // ---- Agent ----

    /** Agent line — updates in-place as status changes. */
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

    /** Diff line with background coloring like Claude Code. */
    record DiffLine(String line) implements OutputLine {
        public StyledElement<?> render() {
            if (line.startsWith("+") && !line.startsWith("+++")) {
                return text("  " + line).fg(Color.GREEN).fill();
            }
            if (line.startsWith("-") && !line.startsWith("---")) {
                return text("  " + line).fg(Color.RED).fill();
            }
            if (line.startsWith("@@")) {
                return row(spacer(2), text(line).cyan().fit());
            }
            return row(spacer(2), text(line).dim().fit());
        }
    }

    // ---- Permission ----

    record PermissionDenied(String toolName) implements OutputLine {
        public StyledElement<?> render() {
            return row(spacer(2), text("⚠ Permission denied: ").yellow().fit(), text(toolName).bold().fit());
        }
    }

    // ---- Helpers ----

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
