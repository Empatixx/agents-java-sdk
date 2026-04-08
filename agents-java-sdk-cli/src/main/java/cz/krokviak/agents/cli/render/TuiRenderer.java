package cz.krokviak.agents.cli.render;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Full-screen TUI renderer backed by JLine3.
 *
 * <p>Architecture:
 * <ul>
 *   <li>{@link ScreenBuffer} — double-buffered 2D grid; only changed cells are re-drawn.</li>
 *   <li>{@link StatusBar} — pinned to the last terminal row.</li>
 *   <li>Scrollable output area occupying rows 0 .. (rows-2).</li>
 *   <li>Spinner runs on a background {@link ScheduledExecutorService} thread.</li>
 * </ul>
 *
 * <p>All public methods are synchronised on {@code this}.
 */
public final class TuiRenderer implements Renderer {

    private static final String[] SPINNER_FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};

    private final Theme theme;
    private final ScreenBuffer buffer;
    private final StatusBar statusBar;
    private final List<OutputLine> lines = new ArrayList<>();

    // Scroll offset: index of the first visible output line (0 = top)
    private int scrollOffset = 0;

    // Spinner state
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "tui-spinner");
                t.setDaemon(true);
                return t;
            });
    private ScheduledFuture<?> spinnerTask = null;
    private volatile String spinnerLabel   = "";
    private int spinnerFrame               = 0;
    private final AtomicBoolean spinnerActive = new AtomicBoolean(false);

    // ------------------------------------------------------------------
    //  Construction / teardown
    // ------------------------------------------------------------------

    public TuiRenderer(Theme theme) {
        this.theme     = theme;
        this.buffer    = new ScreenBuffer(buildTerminal());
        this.statusBar = new StatusBar();
        buffer.hideCursor();
        buffer.eraseDisplay();
    }

    private static Terminal buildTerminal() {
        try {
            return TerminalBuilder.builder().system(true).dumb(true).build();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot open JLine terminal", e);
        }
    }

    /** Restore terminal state. Call when the application exits. */
    public synchronized void shutdown() {
        stopAllSpinners();
        scheduler.shutdownNow();
        buffer.showCursor();
        buffer.close();
    }

    // ------------------------------------------------------------------
    //  Renderer — spinner
    // ------------------------------------------------------------------

    @Override
    public synchronized void startSpinner(String message) {
        renderSpinner(message);
    }

    @Override
    public synchronized void stopSpinner() {
        stopSpinner(null);
    }

    @Override
    public synchronized void renderSpinner(String label) {
        spinnerLabel  = label == null ? "" : label;
        spinnerFrame  = 0;
        spinnerActive.set(true);
        if (spinnerTask != null) {
            spinnerTask.cancel(false);
        }
        spinnerTask = scheduler.scheduleAtFixedRate(() -> {
            synchronized (TuiRenderer.this) {
                tickSpinner();
            }
        }, 0, 80, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void stopSpinner(String finalMessage) {
        stopAllSpinners();
        if (finalMessage != null && !finalMessage.isEmpty()) {
            addLine(OutputLine.styled(finalMessage, theme.success()));
        }
        redraw();
    }

    private void stopAllSpinners() {
        spinnerActive.set(false);
        if (spinnerTask != null) {
            spinnerTask.cancel(false);
            spinnerTask = null;
        }
        spinnerLabel = "";
    }

    private void tickSpinner() {
        if (!spinnerActive.get()) return;
        spinnerFrame = (spinnerFrame + 1) % SPINNER_FRAMES.length;
        redraw();
    }

    // ------------------------------------------------------------------
    //  Renderer — tool display
    // ------------------------------------------------------------------

    @Override
    public synchronized void printToolCall(String name, Map<String, Object> args) {
        addLine(OutputLine.styled("", ""));
        addLine(OutputLine.styled(theme.primary() + "╭─ " + theme.bold() + name + theme.reset(), ""));
        if (args != null) {
            for (var entry : args.entrySet()) {
                String value = truncate(String.valueOf(entry.getValue()), 200);
                addLine(OutputLine.styled(
                        theme.primary() + "│  " + theme.dim() + entry.getKey() + ": " + theme.reset() + value, ""));
            }
        }
        addLine(OutputLine.styled(theme.primary() + "╰─────" + theme.reset(), ""));
        redraw();
    }

    @Override
    public synchronized void printToolResult(String name, String output) {
        if (output == null || output.isEmpty()) return;
        String[] outputLines = output.split("\n", -1);
        int max = 50;
        for (int i = 0; i < Math.min(outputLines.length, max); i++) {
            addLine(OutputLine.styled(theme.dim() + outputLines[i] + theme.reset(), ""));
        }
        if (outputLines.length > max) {
            addLine(OutputLine.styled(theme.dim() + "... (" + (outputLines.length - max) + " more lines)" + theme.reset(), ""));
        }
        redraw();
    }

    @Override
    public synchronized void printToolTiming(long startNanos) {
        double sec = (System.nanoTime() - startNanos) / 1_000_000_000.0;
        addLine(OutputLine.styled(theme.dim() + String.format("  completed in %.1fs", sec) + theme.reset(), ""));
        redraw();
    }

    // ------------------------------------------------------------------
    //  Renderer — text output
    // ------------------------------------------------------------------

    @Override
    public synchronized void printTextDelta(String delta) {
        if (delta == null || delta.isEmpty()) return;
        // Split delta on newlines; append to the last line if it exists, else new line
        String[] parts = delta.split("\n", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i == 0 && !lines.isEmpty()) {
                // Append to last line
                OutputLine last = lines.get(lines.size() - 1);
                lines.set(lines.size() - 1, new OutputLine(last.text() + parts[i], last.style(), last.timestamp()));
            } else {
                addLine(OutputLine.plain(parts[i]));
            }
        }
        scrollToBottom();
        redraw();
    }

    @Override
    public synchronized void printError(String message) {
        addLine(OutputLine.styled(theme.error() + "✗ " + message + theme.reset(), ""));
        redraw();
    }

    @Override
    public synchronized void printPrompt() {
        addLine(OutputLine.styled("", ""));
        addLine(OutputLine.styled(theme.bold() + "you> " + theme.reset(), ""));
        scrollToBottom();
        redraw();
    }

    @Override
    public synchronized void printPromptWithCost(String costInfo) {
        addLine(OutputLine.styled("", ""));
        addLine(OutputLine.styled(
                theme.bold() + "you" + theme.reset() + theme.dim() + " " + costInfo + theme.reset()
                        + theme.bold() + "> " + theme.reset(), ""));
        scrollToBottom();
        redraw();
    }

    @Override
    public synchronized void printUsage(String formatted) {
        addLine(OutputLine.styled(theme.dim() + formatted + theme.reset(), ""));
        redraw();
    }

    @Override
    public synchronized void println(String text) {
        addLine(OutputLine.plain(text == null ? "" : text));
        redraw();
    }

    @Override
    public synchronized void printPermissionDenied(String toolName) {
        addLine(OutputLine.styled(theme.warning() + "⚠ Permission denied for " + toolName + theme.reset(), ""));
        redraw();
    }

    // ------------------------------------------------------------------
    //  Renderer — diff
    // ------------------------------------------------------------------

    @Override
    public synchronized void renderDiff(String diff) {
        if (diff == null || diff.isEmpty()) return;
        for (String line : diff.split("\n", -1)) {
            addLine(OutputLine.styled(colorizeDiffLine(line), ""));
        }
        redraw();
    }

    // ------------------------------------------------------------------
    //  Renderer — tool call with status
    // ------------------------------------------------------------------

    @Override
    public synchronized void renderToolCall(String name, Map<String, Object> args, ToolCallStatus status) {
        String badge = statusBadge(status);
        addLine(OutputLine.styled("", ""));
        addLine(OutputLine.styled(theme.primary() + "╭─ " + theme.bold() + name + theme.reset() + "  " + badge, ""));
        if (args != null) {
            for (var entry : args.entrySet()) {
                String value = truncate(String.valueOf(entry.getValue()), 200);
                addLine(OutputLine.styled(
                        theme.primary() + "│  " + theme.dim() + entry.getKey() + ": " + theme.reset() + value, ""));
            }
        }
        addLine(OutputLine.styled(theme.primary() + "╰─────" + theme.reset(), ""));
        redraw();
    }

    // ------------------------------------------------------------------
    //  Renderer — agent status
    // ------------------------------------------------------------------

    @Override
    public synchronized void renderAgentStatus(String agentName, AgentStatus status, String detail) {
        String icon   = agentStatusIcon(status);
        String colour = agentStatusColour(status);
        String line   = colour + icon + " " + theme.bold() + agentName
                + theme.reset() + colour + "  " + agentStatusLabel(status) + theme.reset();
        if (detail != null && !detail.isEmpty()) {
            line += "  " + theme.dim() + detail + theme.reset();
        }
        addLine(OutputLine.styled(line, ""));
        redraw();
    }

    // ------------------------------------------------------------------
    //  Renderer — progress bar
    // ------------------------------------------------------------------

    @Override
    public synchronized void renderProgress(String label, int current, int total) {
        int barWidth = 20;
        double pct   = total > 0 ? (double) current / total : 0.0;
        int filled   = clamp((int) Math.round(pct * barWidth), 0, barWidth);
        String bar   = "█".repeat(filled) + "░".repeat(barWidth - filled);
        int percent  = (int) Math.round(pct * 100);
        addLine(OutputLine.styled(theme.primary() + label + " [" + bar + "] " + percent + "%" + theme.reset(), ""));
        redraw();
    }

    // ------------------------------------------------------------------
    //  Renderer — context bar (updates status bar)
    // ------------------------------------------------------------------

    @Override
    public synchronized void renderContextBar(int used, int total) {
        statusBar.setTokensUsed(used);
        statusBar.setTokensMax(total);
        redraw();
    }

    // ------------------------------------------------------------------
    //  Renderer — box
    // ------------------------------------------------------------------

    @Override
    public synchronized void renderBox(String title, List<String> content, BoxStyle style) {
        String colour = boxColour(style);
        int width = 60;
        if (content != null) {
            for (String l : content) width = Math.max(width, l.length() + 4);
        }
        if (title != null && !title.isEmpty()) width = Math.max(width, title.length() + 6);

        if (title != null && !title.isEmpty()) {
            int dashLen = width - title.length() - 4;
            int left    = dashLen / 2;
            int right   = dashLen - left;
            addLine(OutputLine.styled(colour + "╭" + "─".repeat(Math.max(0, left))
                    + " " + title + " " + "─".repeat(Math.max(0, right)) + "╮" + theme.reset(), ""));
        } else {
            addLine(OutputLine.styled(colour + "╭" + "─".repeat(width) + "╮" + theme.reset(), ""));
        }
        if (content != null) {
            for (String l : content) {
                int padding = width - l.length() - 2;
                addLine(OutputLine.styled(colour + "│ " + theme.reset()
                        + l + " ".repeat(Math.max(0, padding)) + colour + " │" + theme.reset(), ""));
            }
        }
        addLine(OutputLine.styled(colour + "╰" + "─".repeat(width) + "╯" + theme.reset(), ""));
        redraw();
    }

    // ------------------------------------------------------------------
    //  Renderer — table
    // ------------------------------------------------------------------

    @Override
    public synchronized void renderTable(List<String> headers, List<List<String>> rows) {
        if (headers == null || headers.isEmpty()) return;
        int cols = headers.size();
        int[] widths = new int[cols];
        for (int i = 0; i < cols; i++) widths[i] = headers.get(i).length();
        if (rows != null) {
            for (List<String> row : rows) {
                for (int i = 0; i < Math.min(cols, row.size()); i++) {
                    widths[i] = Math.max(widths[i], row.get(i).length());
                }
            }
        }
        // Header
        StringBuilder sb = new StringBuilder(theme.bold() + theme.primary() + "│");
        for (int i = 0; i < cols; i++) sb.append(" ").append(pad(headers.get(i), widths[i])).append(" │");
        addLine(OutputLine.styled(sb + theme.reset(), ""));
        // Separator
        StringBuilder sep = new StringBuilder(theme.primary() + "├");
        for (int i = 0; i < cols; i++) sep.append("─".repeat(widths[i] + 2)).append(i < cols - 1 ? "┼" : "┤");
        addLine(OutputLine.styled(sep + theme.reset(), ""));
        // Rows
        if (rows != null) {
            for (List<String> row : rows) {
                StringBuilder rsb = new StringBuilder(theme.primary() + "│" + theme.reset());
                for (int i = 0; i < cols; i++) {
                    String cell = i < row.size() ? row.get(i) : "";
                    rsb.append(" ").append(pad(cell, widths[i])).append(" ")
                            .append(theme.primary()).append("│").append(theme.reset());
                }
                addLine(OutputLine.styled(rsb.toString(), ""));
            }
        }
        redraw();
    }

    // ------------------------------------------------------------------
    //  Renderer — markdown
    // ------------------------------------------------------------------

    @Override
    public synchronized void renderMarkdown(String markdown) {
        if (markdown == null) return;
        for (String line : markdown.split("\n", -1)) {
            addLine(OutputLine.styled(renderMarkdownLine(line), ""));
        }
        redraw();
    }

    // ------------------------------------------------------------------
    //  Status bar helpers (public so callers can update model/cost/mode)
    // ------------------------------------------------------------------

    public synchronized void setModel(String model)        { statusBar.setModel(model); }
    public synchronized void setCost(double cost)          { statusBar.setCost(cost); }
    public synchronized void setPermMode(String permMode)  { statusBar.setPermMode(permMode); }

    // ------------------------------------------------------------------
    //  Core rendering loop
    // ------------------------------------------------------------------

    /**
     * Re-paint the whole screen into the buffer and flush changed cells.
     * Must be called with the monitor held.
     */
    private void redraw() {
        buffer.syncSize();
        int totalRows = buffer.rows();
        int totalCols = buffer.cols();
        int outputRows = totalRows - 1; // last row is status bar

        buffer.clear();

        // --- Output area ---
        int visibleCount = Math.min(outputRows, lines.size() - scrollOffset);
        for (int r = 0; r < visibleCount; r++) {
            int lineIndex = scrollOffset + r;
            if (lineIndex >= lines.size()) break;
            OutputLine ol   = lines.get(lineIndex);
            String text     = ol.text() == null ? "" : ol.text();
            // Truncate to terminal width (visual chars, ignoring ANSI — best effort)
            if (text.length() > totalCols * 4) {
                text = text.substring(0, totalCols * 4);
            }
            buffer.write(r, 0, stripAnsi(text).length() > totalCols
                    ? stripAnsi(text).substring(0, totalCols)
                    : text, "");
        }

        // --- Spinner overlay (last output row if active) ---
        if (spinnerActive.get() && outputRows > 0) {
            int spinRow = visibleCount < outputRows ? visibleCount : outputRows - 1;
            String spinText = SPINNER_FRAMES[spinnerFrame % SPINNER_FRAMES.length]
                    + " " + spinnerLabel;
            buffer.write(spinRow, 0, spinText, theme.primary());
        }

        // --- Status bar (last row) ---
        statusBar.render(buffer, totalRows - 1, totalCols);

        buffer.flush();
    }

    // ------------------------------------------------------------------
    //  Line management
    // ------------------------------------------------------------------

    private void addLine(OutputLine line) {
        lines.add(line);
    }

    private void scrollToBottom() {
        int outputRows = Math.max(1, buffer.rows() - 1);
        scrollOffset = Math.max(0, lines.size() - outputRows);
    }

    // ------------------------------------------------------------------
    //  Private helpers
    // ------------------------------------------------------------------

    private String colorizeDiffLine(String line) {
        if (line.startsWith("+") && !line.startsWith("+++")) {
            return theme.success() + line + theme.reset();
        } else if (line.startsWith("-") && !line.startsWith("---")) {
            return theme.error() + line + theme.reset();
        } else if (line.startsWith("@@")) {
            return theme.primary() + line + theme.reset();
        }
        return line;
    }

    private String statusBadge(ToolCallStatus status) {
        return switch (status) {
            case PENDING   -> theme.muted()   + "[pending]"   + theme.reset();
            case RUNNING   -> theme.primary() + "[running]"   + theme.reset();
            case COMPLETED -> theme.success() + "[completed]" + theme.reset();
            case FAILED    -> theme.error()   + "[failed]"    + theme.reset();
        };
    }

    private String agentStatusIcon(AgentStatus status) {
        return switch (status) {
            case STARTING, RUNNING -> "⟳";
            case WAITING           -> "…";
            case COMPLETED         -> "✓";
            case FAILED, KILLED    -> "✗";
        };
    }

    private String agentStatusColour(AgentStatus status) {
        return switch (status) {
            case STARTING, RUNNING -> theme.primary();
            case WAITING           -> theme.warning();
            case COMPLETED         -> theme.success();
            case FAILED            -> theme.error();
            case KILLED            -> theme.muted();
        };
    }

    private String agentStatusLabel(AgentStatus status) {
        return switch (status) {
            case STARTING  -> "starting";
            case RUNNING   -> "running";
            case WAITING   -> "waiting";
            case COMPLETED -> "completed";
            case FAILED    -> "failed";
            case KILLED    -> "killed";
        };
    }

    private String boxColour(BoxStyle style) {
        return switch (style) {
            case INFO    -> theme.primary();
            case WARNING -> theme.warning();
            case ERROR   -> theme.error();
            case SUCCESS -> theme.success();
            case PLAIN   -> theme.muted();
        };
    }

    private String renderMarkdownLine(String line) {
        if (line.startsWith("### ")) return theme.bold() + theme.accent() + line.substring(4) + theme.reset();
        if (line.startsWith("## "))  return theme.bold() + theme.accent() + line.substring(3) + theme.reset();
        if (line.startsWith("# "))   return theme.bold() + theme.primary() + line.substring(2) + theme.reset();
        if (line.matches("^[-*_]{3,}$")) return theme.muted() + "─".repeat(60) + theme.reset();
        if (line.startsWith("```"))  return theme.dim() + line + theme.reset();
        String result = line;
        result = result.replaceAll("\\*\\*(.+?)\\*\\*", theme.bold() + "$1" + theme.reset());
        result = result.replaceAll("`(.+?)`", theme.accent() + "$1" + theme.reset());
        if (result.startsWith("- ") || result.startsWith("* ")) {
            return theme.primary() + "• " + theme.reset() + result.substring(2);
        }
        return result;
    }

    /** Strip ANSI escape sequences to measure visible length. */
    private static String stripAnsi(String s) {
        return s.replaceAll("\033\\[[0-9;]*[mGKHJA-Za-z]", "");
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static String pad(String s, int width) {
        return s.length() >= width ? s : s + " ".repeat(width - s.length());
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
