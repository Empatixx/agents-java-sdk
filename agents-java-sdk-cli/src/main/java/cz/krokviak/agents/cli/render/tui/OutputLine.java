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

    /** First line of AI response — with ● bullet, markdown styled. */
    record TextStart(String content) implements OutputLine {
        public StyledElement<?> render() {
            var md = MarkdownRenderer.renderLine(content, "");
            return row(text("  ● ").dim().fit(), md);
        }
    }

    /** Continuation lines of AI response — no bullet, markdown styled. */
    record Text(String content) implements OutputLine {
        public StyledElement<?> render() {
            return MarkdownRenderer.renderLine(content, "    ");
        }
    }

    record UserMessage(String content) implements OutputLine {
        public StyledElement<?> render() {
            return row(text(" ❯ ").bold().cyan().fit(), text(content).fit())
                .bg(Color.indexed(238));
        }
    }

    /** Image attachment indicator. */
    record ImageAttached(String path, int index) implements OutputLine {
        public StyledElement<?> render() {
            return row(text("  ").fit(),
                text("\ud83d\uddbc ").fit(),
                text("[Image #" + index + "]").bold().cyan().fit(),
                text(" " + path).dim().fit());
        }
    }

    record Dim(String content) implements OutputLine {
        public StyledElement<?> render() {
            return text("  " + content).dim();
        }
    }

    /** Code block line — monospace dim with indent. */
    record CodeLine(String content) implements OutputLine {
        public StyledElement<?> render() {
            return text("      " + content).fg(Color.indexed(246));
        }
    }

    record Error(String message) implements OutputLine {
        public StyledElement<?> render() {
            return text("  ✗ " + message).red().bold();
        }
    }

    /** Tool call: single line that updates in-place. ● name(args) → ● name(args) (1.2s, 47 lines) */
    record ToolCall(String name, String args, ToolCallStatus status, boolean indented,
                    long ms, int resultLines) implements OutputLine {
        public ToolCall(String name, String args, ToolCallStatus status) {
            this(name, args, status, false, 0, 0);
        }
        public ToolCall(String name, String args, ToolCallStatus status, boolean indented) {
            this(name, args, status, indented, 0, 0);
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

            String suffix = "";
            if (status == ToolCallStatus.COMPLETED || status == ToolCallStatus.FAILED) {
                String time = ms > 0 ? (ms >= 1000 ? String.format("%.1fs", ms / 1000.0) : ms + "ms") : "";
                String lines = resultLines > 0 ? resultLines + " lines" : "";
                if (!time.isEmpty() && !lines.isEmpty()) suffix = " (" + time + ", " + lines + ")";
                else if (!time.isEmpty()) suffix = " (" + time + ")";
                else if (!lines.isEmpty()) suffix = " (" + lines + ")";
            }

            return row(text(pad).fit(), iconEl,
                text(name).bold().fit(),
                text("(" + trunc(args, 60) + ")").dim().fit(),
                text(suffix).dim().fit());
        }

        public ToolCall withStatus(ToolCallStatus s) { return new ToolCall(name, args, s, indented, ms, resultLines); }
        public ToolCall withResult(int lines, long ms) { return new ToolCall(name, args, ToolCallStatus.COMPLETED, indented, ms, lines); }
    }

    /** Expanded tool result line (ctrl+o). */
    record Result(String line) implements OutputLine {
        public StyledElement<?> render() {
            return text("    \u23bf  " + line).dim();
        }
    }

    /** Agent — updates in-place. Blinks ● when running. */
    record Agent(String name, AgentStatus status, String detail) implements OutputLine {
        public StyledElement<?> render() {
            boolean blink = (TICK.get() % 2 == 0);
            String icon = switch (status) {
                case STARTING, RUNNING, WAITING -> blink ? "●" : "○";
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

    // Tick counter for blink animation — incremented by CliApp every 500ms
    java.util.concurrent.atomic.AtomicInteger TICK = new java.util.concurrent.atomic.AtomicInteger(0);

    private static String trunc(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
