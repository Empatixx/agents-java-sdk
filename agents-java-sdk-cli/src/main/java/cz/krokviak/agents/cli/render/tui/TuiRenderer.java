package cz.krokviak.agents.cli.render.tui;

import cz.krokviak.agents.cli.render.AgentStatus;
import cz.krokviak.agents.cli.render.BoxStyle;
import cz.krokviak.agents.cli.render.Renderer;
import cz.krokviak.agents.cli.render.ToolCallStatus;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.elements.ListElement;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * Thread-safe Renderer implementation that bridges AgentRunner calls (on REPL thread)
 * to CliState mutations (on render thread).
 *
 * <p>Before runner is set, mutations are buffered. After runner is set, mutations
 * are dispatched via {@code runner.runOnRenderThread()}.
 */
public final class TuiRenderer implements Renderer {

    private final CliState state;
    private final ListElement<?> outputLog;
    private volatile ToolkitRunner runner;
    private final ConcurrentLinkedQueue<Runnable> pendingBeforeReady = new ConcurrentLinkedQueue<>();

    public TuiRenderer(CliState state, ListElement<?> outputLog) {
        this.state = state;
        this.outputLog = outputLog;
    }

    /** Called once by CliApp.onStart() after runner is initialized. */
    public void activate(ToolkitRunner runner) {
        this.runner = runner;
        // Drain buffered mutations
        Runnable r;
        while ((r = pendingBeforeReady.poll()) != null) {
            runner.runOnRenderThread(r);
        }
    }

    private void onRenderThread(Runnable action) {
        ToolkitRunner r = runner;
        if (r != null) {
            r.runOnRenderThread(action);
        } else {
            pendingBeforeReady.add(action);
        }
    }

    // ========================= Renderer methods =========================

    @Override
    public void startSpinner(String message) {
        onRenderThread(() -> state.setSpinner(true, message));
    }

    @Override
    public void stopSpinner() {
        onRenderThread(() -> state.setSpinner(false, null));
    }

    @Override
    public void printToolCall(String name, Map<String, Object> args) {
        String inlineArgs = formatArgs(args);
        onRenderThread(() -> outputLog.add(row(
            text("● ").green().fit(),
            text(name).bold().fit(),
            text("(" + inlineArgs + ")").dim().fit()
        )));
    }

    @Override
    public void printToolResult(String name, String output) {
        if (output == null || output.isEmpty()) return;
        String[] lines = output.split("\n", -1);
        onRenderThread(() -> {
            if (lines.length <= CliState.COLLAPSED_PREVIEW_LINES) {
                for (String line : lines) {
                    outputLog.add(text("  ⎿  " + line).dim());
                }
            } else {
                for (int i = 0; i < CliState.COLLAPSED_PREVIEW_LINES; i++) {
                    outputLog.add(text("  ⎿  " + lines[i]).dim());
                }
                outputLog.add(text("  ⎿  (" + (lines.length - CliState.COLLAPSED_PREVIEW_LINES)
                    + " more lines, ctrl+o to expand)").dim());
                state.pushCollapsed(new CliState.CollapsedResult(output, lines.length));
            }
        });
    }

    @Override
    public void printToolTiming(long startNanos) {
        long ms = (System.nanoTime() - startNanos) / 1_000_000;
        onRenderThread(() -> outputLog.add(text("  ⎿  (" + ms + "ms)").dim()));
    }

    @Override
    public void printTextDelta(String delta) {
        onRenderThread(() -> {
            state.appendStreaming(delta);
            state.flushStreamingBuffer(line -> outputLog.add(line));
        });
    }

    @Override
    public void printError(String message) {
        onRenderThread(() -> outputLog.add(text("✗ " + message).red().bold()));
    }

    @Override
    public void printPrompt() {
        onRenderThread(() -> state.flushAll(line -> outputLog.add(line)));
    }

    @Override
    public void printPromptWithCost(String costInfo) {
        onRenderThread(() -> state.flushAll(line -> outputLog.add(line)));
    }

    @Override
    public void printUsage(String formatted) {
        onRenderThread(() -> {
            for (String line : formatted.split("\n")) {
                outputLog.add(text(line).dim());
            }
        });
    }

    @Override
    public void println(String textStr) {
        onRenderThread(() -> outputLog.add(textStr));
    }

    @Override
    public void printPermissionDenied(String toolName) {
        onRenderThread(() -> outputLog.add(row(
            text("⚠ Permission denied: ").yellow().fit(),
            text(toolName).bold().fit()
        )));
    }

    @Override
    public void renderDiff(String diff) {
        onRenderThread(() -> {
            for (String line : diff.split("\n")) {
                if (line.startsWith("+")) outputLog.add(text(line).green());
                else if (line.startsWith("-")) outputLog.add(text(line).red());
                else if (line.startsWith("@@")) outputLog.add(text(line).cyan());
                else outputLog.add(text(line).dim());
            }
        });
    }

    @Override
    public void renderToolCall(String name, Map<String, Object> args, ToolCallStatus status) {
        String icon = switch (status) {
            case PENDING -> "○"; case RUNNING -> "●"; case COMPLETED -> "✓"; case FAILED -> "✗";
        };
        onRenderThread(() -> {
            var iconEl = switch (status) {
                case PENDING -> text(icon + " ").dim().fit();
                case RUNNING -> text(icon + " ").cyan().fit();
                case COMPLETED -> text(icon + " ").green().fit();
                case FAILED -> text(icon + " ").red().fit();
            };
            outputLog.add(row(iconEl, text(name).bold().fit(), text("(" + formatArgs(args) + ")").dim().fit()));
        });
    }

    @Override
    public void renderAgentStatus(String agentName, AgentStatus status, String detail) {
        String icon = switch (status) {
            case STARTING -> "◌"; case RUNNING -> "●"; case WAITING -> "◎";
            case COMPLETED -> "✓"; case FAILED -> "✗"; case KILLED -> "⊘";
        };
        onRenderThread(() -> outputLog.add(row(
            text(icon + " ").cyan().fit(),
            text(agentName).bold().fit(),
            text(" — " + status.name().toLowerCase()).dim().fit(),
            detail != null ? text(" " + detail).dim().fit() : text("").fit()
        )));
    }

    @Override
    public void renderProgress(String label, int current, int total) {
        double ratio = total > 0 ? (double) current / total : 0;
        int pct = (int) (ratio * 100);
        onRenderThread(() -> outputLog.add(row(
            text(label + " ").bold().fit(),
            gauge(ratio).green().fill(),
            text(" " + pct + "%").dim().fit()
        )));
    }

    @Override
    public void renderSpinner(String label) { startSpinner(label); }

    @Override
    public void stopSpinner(String finalMessage) {
        onRenderThread(() -> {
            state.setSpinner(false, null);
            outputLog.add(text("✓ " + finalMessage).green());
        });
    }

    @Override
    public void renderContextBar(int used, int total) {
        onRenderThread(() -> state.setTokens(used, total));
    }

    @Override
    public void renderBox(String title, List<String> content, BoxStyle style) {
        onRenderThread(() -> {
            if (title != null) outputLog.add(text("── " + title + " ──").bold());
            for (String line : content) outputLog.add(text("  " + line));
        });
    }

    @Override
    public void renderTable(List<String> headers, List<List<String>> rows) {
        onRenderThread(() -> {
            var sb = new StringBuilder();
            for (int i = 0; i < headers.size(); i++) {
                if (i > 0) sb.append(" │ ");
                sb.append(headers.get(i));
            }
            outputLog.add(text(sb.toString()).bold().cyan());
            for (var row : rows) {
                var rsb = new StringBuilder();
                for (int i = 0; i < row.size(); i++) {
                    if (i > 0) rsb.append(" │ ");
                    rsb.append(row.get(i));
                }
                outputLog.add(rsb.toString());
            }
        });
    }

    @Override
    public void renderMarkdown(String markdown) {
        onRenderThread(() -> {
            for (String line : markdown.split("\n")) {
                if (line.startsWith("# ")) outputLog.add(text(line.substring(2)).bold().cyan());
                else if (line.startsWith("## ")) outputLog.add(text(line.substring(3)).bold());
                else if (line.startsWith("- ") || line.startsWith("* "))
                    outputLog.add(text("  • " + line.substring(2)));
                else outputLog.add(line);
            }
        });
    }

    // ---- Expand/collapse ----

    public void toggleExpandCollapse() {
        onRenderThread(() -> {
            if (!state.hasCollapsed()) return;
            CliState.CollapsedResult cr = state.popCollapsed();
            if (cr == null) return;
            for (String line : cr.output().split("\n")) {
                outputLog.add(text("  ⎿  " + line).dim());
            }
        });
    }

    // ---- Permission prompt ----

    private final java.util.concurrent.BlockingQueue<Integer> permissionResult = new java.util.concurrent.LinkedBlockingQueue<>();

    /**
     * Show permission prompt in the UI and block until user responds.
     * Called from REPL thread. CliApp swaps the input area for a selection list.
     * Returns selected option index.
     */
    public int promptPermission(String header, String[] options) {
        onRenderThread(() -> state.setPermissionPrompt(header, options));

        try {
            return permissionResult.take();
        } catch (InterruptedException e) {
            return options.length - 1; // deny
        }
    }

    /** Called by CliApp when user responds to permission prompt. */
    public void resolvePermission(int selectedIndex) {
        onRenderThread(() -> state.clearPermissionPrompt());
        permissionResult.offer(selectedIndex);
    }

    public boolean hasPermissionPrompt() {
        return state.hasPermissionPrompt();
    }

    // ---- Helpers ----

    private String formatArgs(Map<String, Object> args) {
        if (args == null || args.isEmpty()) return "";
        var sb = new StringBuilder();
        int i = 0;
        for (var entry : args.entrySet()) {
            if (i++ > 0) sb.append(", ");
            String val = String.valueOf(entry.getValue());
            if (val.length() > 60) val = val.substring(0, 57) + "...";
            sb.append(entry.getKey()).append("=").append(val);
            if (i >= 3) { sb.append(", ..."); break; }
        }
        return sb.toString();
    }
}
