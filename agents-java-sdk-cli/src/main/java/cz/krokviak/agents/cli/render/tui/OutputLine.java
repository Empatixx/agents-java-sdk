package cz.krokviak.agents.cli.render.tui;

import cz.krokviak.agents.cli.render.AgentStatus;
import cz.krokviak.agents.cli.render.ToolCallStatus;
import dev.tamboui.toolkit.element.StyledElement;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * Sealed type for output log lines. Each variant knows how to render itself.
 * Lines can be updated in-place via CliState.updateLast().
 */
public sealed interface OutputLine {

    StyledElement<?> render();

    /** Plain text line. */
    record Text(String content) implements OutputLine {
        public StyledElement<?> render() {
            return row(spacer(2), text(content).fit());
        }
    }

    /** User input message. */
    record UserMessage(String content) implements OutputLine {
        public StyledElement<?> render() {
            return row(spacer(2), text("❯ ").bold().cyan().fit(), text(content).bold().fit());
        }
    }

    /** Tool call with updatable status. */
    record ToolCall(String name, String args, ToolCallStatus status) implements OutputLine {
        public StyledElement<?> render() {
            String icon = switch (status) {
                case PENDING -> "○"; case RUNNING -> "●"; case COMPLETED -> "✓"; case FAILED -> "✗";
            };
            var iconEl = switch (status) {
                case PENDING -> text(icon + " ").dim().fit();
                case RUNNING -> text(icon + " ").cyan().fit();
                case COMPLETED -> text(icon + " ").green().fit();
                case FAILED -> text(icon + " ").red().fit();
            };
            return row(spacer(2), iconEl, text(name).bold().fit(), text("(" + args + ")").dim().fit());
        }

        public ToolCall withStatus(ToolCallStatus newStatus) {
            return new ToolCall(name, args, newStatus);
        }
    }

    /** Agent with updatable status and detail. */
    record Agent(String name, AgentStatus status, String detail) implements OutputLine {
        public StyledElement<?> render() {
            String icon = switch (status) {
                case STARTING -> "◌"; case RUNNING -> "●"; case WAITING -> "◎";
                case COMPLETED -> "✓"; case FAILED -> "✗"; case KILLED -> "⊘";
            };
            var iconEl = switch (status) {
                case COMPLETED -> text(icon + " ").green().fit();
                case FAILED, KILLED -> text(icon + " ").red().fit();
                default -> text(icon + " ").cyan().fit();
            };
            return row(spacer(2), iconEl, text(name).bold().fit(),
                detail != null ? text("  " + detail).dim().fit() : text("").fit());
        }

        public Agent withStatus(AgentStatus newStatus, String newDetail) {
            return new Agent(name, newStatus, newDetail);
        }
    }

    /** Tool result (collapsible). */
    record Result(String line) implements OutputLine {
        public StyledElement<?> render() {
            return row(spacer(2), text("⎿  " + line).dim().fit());
        }
    }

    /** Collapse hint. */
    record CollapseHint(int hiddenLines) implements OutputLine {
        public StyledElement<?> render() {
            return row(spacer(2), text("⎿  (" + hiddenLines + " more lines, ctrl+o to expand)").dim().fit());
        }
    }

    /** Error message. */
    record Error(String message) implements OutputLine {
        public StyledElement<?> render() {
            return row(spacer(2), text("✗ " + message).red().bold().fit());
        }
    }

    /** Dim/info text. */
    record Dim(String content) implements OutputLine {
        public StyledElement<?> render() {
            return row(spacer(2), text(content).dim().fit());
        }
    }

    /** Permission denied. */
    record PermissionDenied(String toolName) implements OutputLine {
        public StyledElement<?> render() {
            return row(spacer(2), text("⚠ Permission denied: ").yellow().fit(), text(toolName).bold().fit());
        }
    }

    /** Timing info. */
    record Timing(long ms) implements OutputLine {
        public StyledElement<?> render() {
            return row(spacer(2), text("⎿  (" + ms + "ms)").dim().fit());
        }
    }

    /** Diff line. */
    record DiffLine(String line) implements OutputLine {
        public StyledElement<?> render() {
            if (line.startsWith("+")) return row(spacer(2), text(line).green().fit());
            if (line.startsWith("-")) return row(spacer(2), text(line).red().fit());
            if (line.startsWith("@@")) return row(spacer(2), text(line).cyan().fit());
            return row(spacer(2), text(line).dim().fit());
        }
    }
}
