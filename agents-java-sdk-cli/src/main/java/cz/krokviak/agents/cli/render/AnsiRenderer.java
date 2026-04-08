package cz.krokviak.agents.cli.render;

import java.util.List;
import java.util.Map;

/**
 * ANSI-colour renderer with Unicode box-drawing characters, diff colouring,
 * progress bars (████░░░), context bars, and braille spinner support.
 */
public final class AnsiRenderer implements Renderer {

    private final Theme theme;
    private final Spinner spinner = new Spinner();

    public AnsiRenderer(Theme theme) {
        this.theme = theme;
    }

    public AnsiRenderer() {
        this(Theme.dark());
    }

    // ------------------------------------------------------------------ //
    //  Spinner                                                             //
    // ------------------------------------------------------------------ //

    @Override
    public void startSpinner(String message) {
        spinner.start(message);
    }

    @Override
    public void stopSpinner() {
        spinner.stop(null);
    }

    @Override
    public void renderSpinner(String label) {
        spinner.start(label);
    }

    @Override
    public void stopSpinner(String finalMessage) {
        spinner.stop(finalMessage);
    }

    // ------------------------------------------------------------------ //
    //  Tool display                                                        //
    // ------------------------------------------------------------------ //

    @Override
    public void printToolCall(String name, Map<String, Object> args) {
        System.out.println();
        System.out.println(theme.primary() + "╭─ " + theme.bold() + name + theme.reset());
        if (args != null) {
            for (var entry : args.entrySet()) {
                String value = truncate(String.valueOf(entry.getValue()), 200);
                System.out.println(theme.primary() + "│  " + theme.dim()
                        + entry.getKey() + ": " + theme.reset() + value);
            }
        }
        System.out.println(theme.primary() + "╰─────" + theme.reset());
    }

    @Override
    public void printToolResult(String name, String output) {
        if (output == null || output.isEmpty()) return;
        String[] lines = output.split("\n", -1);
        int maxLines = 50;
        System.out.print(theme.dim());
        for (int i = 0; i < Math.min(lines.length, maxLines); i++) {
            System.out.println(colorizeResultLine(lines[i]));
        }
        if (lines.length > maxLines) {
            System.out.println("... (" + (lines.length - maxLines) + " more lines)");
        }
        System.out.print(theme.reset());
    }

    @Override
    public void printToolTiming(long startNanos) {
        long elapsed = System.nanoTime() - startNanos;
        double seconds = elapsed / 1_000_000_000.0;
        System.out.println(theme.dim() + "  completed in "
                + String.format("%.1fs", seconds) + theme.reset());
    }

    // ------------------------------------------------------------------ //
    //  Text output                                                         //
    // ------------------------------------------------------------------ //

    @Override
    public void printTextDelta(String delta) {
        System.out.print(delta);
    }

    @Override
    public void printError(String message) {
        System.out.println(theme.error() + "✗ " + message + theme.reset());
    }

    @Override
    public void printPrompt() {
        System.out.println();
        System.out.print(theme.bold() + "you> " + theme.reset());
        System.out.flush();
    }

    @Override
    public void printPromptWithCost(String costInfo) {
        System.out.println();
        System.out.print(theme.bold() + "you" + theme.reset()
                + theme.dim() + " " + costInfo + theme.reset()
                + theme.bold() + "> " + theme.reset());
        System.out.flush();
    }

    @Override
    public void printUsage(String formatted) {
        System.out.println(theme.dim() + formatted + theme.reset());
    }

    @Override
    public void println(String text) {
        System.out.println(text);
    }

    @Override
    public void printPermissionDenied(String toolName) {
        System.out.println(theme.warning() + "⚠ Permission denied for " + toolName + theme.reset());
    }

    // ------------------------------------------------------------------ //
    //  Diff                                                                //
    // ------------------------------------------------------------------ //

    @Override
    public void renderDiff(String diff) {
        if (diff == null || diff.isEmpty()) return;
        for (String line : diff.split("\n", -1)) {
            System.out.println(colorizeResultLine(line));
        }
        System.out.print(theme.reset());
    }

    // ------------------------------------------------------------------ //
    //  Tool call with status                                               //
    // ------------------------------------------------------------------ //

    @Override
    public void renderToolCall(String name, Map<String, Object> args, ToolCallStatus status) {
        String badge = statusBadge(status);
        System.out.println();
        System.out.println(theme.primary() + "╭─ " + theme.bold() + name
                + theme.reset() + "  " + badge);
        if (args != null) {
            for (var entry : args.entrySet()) {
                String value = truncate(String.valueOf(entry.getValue()), 200);
                System.out.println(theme.primary() + "│  " + theme.dim()
                        + entry.getKey() + ": " + theme.reset() + value);
            }
        }
        System.out.println(theme.primary() + "╰─────" + theme.reset());
    }

    // ------------------------------------------------------------------ //
    //  Agent status                                                        //
    // ------------------------------------------------------------------ //

    @Override
    public void renderAgentStatus(String agentName, AgentStatus status, String detail) {
        String icon   = agentStatusIcon(status);
        String colour = agentStatusColour(status);
        String line   = colour + icon + " " + theme.bold() + agentName
                + theme.reset() + colour + "  " + statusLabel(status) + theme.reset();
        if (detail != null && !detail.isEmpty()) {
            line += "  " + theme.dim() + detail + theme.reset();
        }
        System.out.println(line);
    }

    // ------------------------------------------------------------------ //
    //  Progress bar                                                        //
    // ------------------------------------------------------------------ //

    @Override
    public void renderProgress(String label, int current, int total) {
        int barWidth = 20;
        double pct   = total > 0 ? (double) current / total : 0.0;
        int filled   = (int) Math.round(pct * barWidth);
        filled = Math.max(0, Math.min(barWidth, filled));
        String bar = "█".repeat(filled) + "░".repeat(barWidth - filled);
        int percent  = (int) Math.round(pct * 100);
        System.out.println(theme.primary() + label + " ["
                + bar + "] " + percent + "%" + theme.reset());
    }

    // ------------------------------------------------------------------ //
    //  Context bar                                                         //
    // ------------------------------------------------------------------ //

    @Override
    public void renderContextBar(int used, int total) {
        int barWidth = 20;
        double pct   = total > 0 ? (double) used / total : 0.0;
        int filled   = (int) Math.round(pct * barWidth);
        filled = Math.max(0, Math.min(barWidth, filled));
        String bar     = "█".repeat(filled) + "░".repeat(barWidth - filled);
        int percent    = (int) Math.round(pct * 100);
        String colour  = pct >= 0.9 ? theme.error() : pct >= 0.7 ? theme.warning() : theme.primary();
        System.out.println(colour + "Context: [" + bar + "] " + percent + "%" + theme.reset());
    }

    // ------------------------------------------------------------------ //
    //  Box                                                                 //
    // ------------------------------------------------------------------ //

    @Override
    public void renderBox(String title, List<String> content, BoxStyle style) {
        String colour = boxColour(style);
        int width = 60;
        if (content != null) {
            for (String line : content) {
                width = Math.max(width, line.length() + 4);
            }
        }
        if (title != null && !title.isEmpty()) {
            width = Math.max(width, title.length() + 6);
        }

        // Top border
        if (title != null && !title.isEmpty()) {
            int dashLen = width - title.length() - 4;
            int left  = dashLen / 2;
            int right = dashLen - left;
            System.out.println(colour
                    + "╭" + "─".repeat(left) + " " + title + " " + "─".repeat(right) + "╮"
                    + theme.reset());
        } else {
            System.out.println(colour + "╭" + "─".repeat(width) + "╮" + theme.reset());
        }

        // Content
        if (content != null) {
            for (String line : content) {
                int padding = width - line.length() - 2;
                System.out.println(colour + "│ " + theme.reset()
                        + line + " ".repeat(Math.max(0, padding))
                        + colour + " │" + theme.reset());
            }
        }

        // Bottom border
        System.out.println(colour + "╰" + "─".repeat(width) + "╯" + theme.reset());
    }

    // ------------------------------------------------------------------ //
    //  Table                                                               //
    // ------------------------------------------------------------------ //

    @Override
    public void renderTable(List<String> headers, List<List<String>> rows) {
        if (headers == null || headers.isEmpty()) return;
        int cols = headers.size();
        int[] widths = new int[cols];
        for (int i = 0; i < cols; i++) {
            widths[i] = headers.get(i).length();
        }
        if (rows != null) {
            for (List<String> row : rows) {
                for (int i = 0; i < Math.min(cols, row.size()); i++) {
                    widths[i] = Math.max(widths[i], row.get(i).length());
                }
            }
        }

        // Header
        StringBuilder sb = new StringBuilder(theme.bold() + theme.primary());
        sb.append("│");
        for (int i = 0; i < cols; i++) {
            sb.append(" ").append(pad(headers.get(i), widths[i])).append(" │");
        }
        System.out.println(sb + theme.reset());

        // Separator
        StringBuilder sep = new StringBuilder(theme.primary() + "├");
        for (int i = 0; i < cols; i++) {
            sep.append("─".repeat(widths[i] + 2));
            sep.append(i < cols - 1 ? "┼" : "┤");
        }
        System.out.println(sep + theme.reset());

        // Rows
        if (rows != null) {
            for (List<String> row : rows) {
                StringBuilder row_sb = new StringBuilder(theme.primary() + "│" + theme.reset());
                for (int i = 0; i < cols; i++) {
                    String cell = i < row.size() ? row.get(i) : "";
                    row_sb.append(" ").append(pad(cell, widths[i])).append(" ")
                            .append(theme.primary()).append("│").append(theme.reset());
                }
                System.out.println(row_sb);
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Markdown (basic)                                                    //
    // ------------------------------------------------------------------ //

    @Override
    public void renderMarkdown(String markdown) {
        if (markdown == null) return;
        for (String line : markdown.split("\n", -1)) {
            System.out.println(renderMarkdownLine(line));
        }
    }

    // ------------------------------------------------------------------ //
    //  Private helpers                                                     //
    // ------------------------------------------------------------------ //

    private String colorizeResultLine(String line) {
        if (line.startsWith("+") && !line.startsWith("+++")) {
            return theme.success() + line + theme.dim();
        } else if (line.startsWith("-") && !line.startsWith("---")) {
            return theme.error() + line + theme.dim();
        } else if (line.startsWith("@@")) {
            return theme.primary() + line + theme.dim();
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
            case STARTING  -> "⟳";
            case RUNNING   -> "⟳";
            case WAITING   -> "…";
            case COMPLETED -> "✓";
            case FAILED    -> "✗";
        };
    }

    private String agentStatusColour(AgentStatus status) {
        return switch (status) {
            case STARTING  -> theme.primary();
            case RUNNING   -> theme.primary();
            case WAITING   -> theme.warning();
            case COMPLETED -> theme.success();
            case FAILED    -> theme.error();
        };
    }

    private String statusLabel(AgentStatus status) {
        return switch (status) {
            case STARTING  -> "starting";
            case RUNNING   -> "running";
            case WAITING   -> "waiting";
            case COMPLETED -> "completed";
            case FAILED    -> "failed";
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
        // Headers
        if (line.startsWith("### ")) {
            return theme.bold() + theme.accent() + line.substring(4) + theme.reset();
        } else if (line.startsWith("## ")) {
            return theme.bold() + theme.accent() + line.substring(3) + theme.reset();
        } else if (line.startsWith("# ")) {
            return theme.bold() + theme.primary() + line.substring(2) + theme.reset();
        }
        // Horizontal rule
        if (line.matches("^[-*_]{3,}$")) {
            return theme.muted() + "─".repeat(60) + theme.reset();
        }
        // Code block fence — just dim it
        if (line.startsWith("```")) {
            return theme.dim() + line + theme.reset();
        }
        // Inline bold (**text**) and code (`code`)
        String result = line;
        result = result.replaceAll("\\*\\*(.+?)\\*\\*", theme.bold() + "$1" + theme.reset());
        result = result.replaceAll("`(.+?)`", theme.accent() + "$1" + theme.reset());
        // List bullets
        if (result.startsWith("- ") || result.startsWith("* ")) {
            return theme.primary() + "• " + theme.reset() + result.substring(2);
        }
        return result;
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }

    private static String pad(String s, int width) {
        if (s.length() >= width) return s;
        return s + " ".repeat(width - s.length());
    }
}
