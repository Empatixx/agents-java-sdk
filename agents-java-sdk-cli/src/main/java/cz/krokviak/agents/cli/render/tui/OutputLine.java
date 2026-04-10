package cz.krokviak.agents.cli.render.tui;

import cz.krokviak.agents.cli.render.AgentStatus;
import cz.krokviak.agents.cli.render.ToolCallStatus;
import dev.tamboui.style.Color;
import dev.tamboui.toolkit.element.StyledElement;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * Output log line types. Each is one row in the list — no nested columns.
 */
public sealed interface OutputLine {

    StyledElement<?> render();

    /** First line of AI response — with ● bullet. */
    record TextStart(String content) implements OutputLine {
        public StyledElement<?> render() {
            return row(text("  ● ").dim().fit(), text(content).fit());
        }
    }

    /** Continuation lines of AI response — no bullet. */
    record Text(String content) implements OutputLine {
        public StyledElement<?> render() {
            return text("    " + content);
        }
    }

    record UserMessage(String content) implements OutputLine {
        public StyledElement<?> render() {
            return row(text(" ❯ ").bold().cyan().fit(), text(content).fit())
                .bg(Color.indexed(238));
        }
    }

    record Dim(String content) implements OutputLine {
        public StyledElement<?> render() {
            return text("  " + content).dim();
        }
    }

    record Error(String message) implements OutputLine {
        public StyledElement<?> render() {
            return text("  ✗ " + message).red().bold();
        }
    }

    /** Tool call: ● name(args) — updates in-place. */
    record ToolCall(String name, String args, ToolCallStatus status, boolean indented) implements OutputLine {
        public ToolCall(String name, String args, ToolCallStatus status) {
            this(name, args, status, false);
        }

        public StyledElement<?> render() {
            String pad = indented ? "      " : "  ";
            String icon = switch (status) {
                case RUNNING -> "●";
                case COMPLETED -> "●";
                case FAILED -> "✗";
                case PENDING -> "○";
            };
            var iconEl = switch (status) {
                case RUNNING -> text(icon + " ").cyan().fit();
                case COMPLETED -> text(icon + " ").green().fit();
                case FAILED -> text(icon + " ").red().fit();
                case PENDING -> text(icon + " ").dim().fit();
            };
            return row(text(pad).fit(), iconEl, text(name).bold().fit(), text("(" + trunc(args, 80) + ")").dim().fit());
        }

        public ToolCall withStatus(ToolCallStatus s) { return new ToolCall(name, args, s, indented); }
    }

    /** Tool result line: ⎿ text */
    record Result(String line) implements OutputLine {
        public StyledElement<?> render() {
            return text("    ⎿  " + line).dim();
        }
    }

    /** Collapse hint with timing. */
    record CollapseHint(int hiddenLines, long ms) implements OutputLine {
        public StyledElement<?> render() {
            String time = ms > 0 ? (ms >= 1000 ? String.format("%.1fs", ms / 1000.0) : ms + "ms") : "";
            String hint = hiddenLines > 0
                ? "(" + hiddenLines + " more lines, ctrl+o to expand)"
                : "(ctrl+o to expand)";
            String suffix = time.isEmpty() ? "" : " · " + time;
            return text("    ⎿  " + hint + suffix).dim();
        }

        public CollapseHint withTiming(long newMs) { return new CollapseHint(hiddenLines, newMs); }
    }

    /** Timing only (when no collapse). */
    record Timing(long ms) implements OutputLine {
        public StyledElement<?> render() {
            String time = ms >= 1000 ? String.format("%.1fs", ms / 1000.0) : ms + "ms";
            return text("    ⎿  (" + time + ")").dim();
        }
    }

    /** Agent — updates in-place. */
    record Agent(String name, AgentStatus status, String detail) implements OutputLine {
        public StyledElement<?> render() {
            String icon = switch (status) {
                case STARTING, RUNNING, WAITING -> "●";
                case COMPLETED -> "✓";
                case FAILED, KILLED -> "✗";
            };
            var iconEl = switch (status) {
                case STARTING, RUNNING, WAITING -> text(icon + " ").cyan().fit();
                case COMPLETED -> text(icon + " ").green().fit();
                case FAILED, KILLED -> text(icon + " ").red().fit();
            };
            String d = detail != null ? "  " + detail : "";
            return row(text("  ").fit(), iconEl, text(name).bold().fit(), text(d).dim().fit());
        }

        public Agent withStatus(AgentStatus s, String d) { return new Agent(name, s, d); }
    }

    record DiffLine(String line) implements OutputLine {
        public StyledElement<?> render() {
            if (line.startsWith("+") && !line.startsWith("+++"))
                return text("  " + line).fg(Color.GREEN);
            if (line.startsWith("-") && !line.startsWith("---"))
                return text("  " + line).fg(Color.RED);
            if (line.startsWith("@@"))
                return text("  " + line).cyan();
            return text("  " + line).dim();
        }
    }

    record PermissionDenied(String toolName) implements OutputLine {
        public StyledElement<?> render() {
            return row(text("  ⚠ Permission denied: ").yellow().fit(), text(toolName).bold().fit());
        }
    }

    private static String trunc(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
