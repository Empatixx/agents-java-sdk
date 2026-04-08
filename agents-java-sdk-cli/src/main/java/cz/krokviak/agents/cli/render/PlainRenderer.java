package cz.krokviak.agents.cli.render;

import java.util.List;
import java.util.Map;

/**
 * Plain-text renderer with no ANSI escape codes, suitable for piped output
 * or environments that do not support terminal colours.
 */
public final class PlainRenderer implements Renderer {

    // ------------------------------------------------------------------ //
    //  Spinner (no-op animation, just prints label on start)              //
    // ------------------------------------------------------------------ //

    @Override
    public void startSpinner(String message) {
        System.out.println("[ ] " + message);
        System.out.flush();
    }

    @Override
    public void stopSpinner() {
        // nothing to stop
    }

    @Override
    public void renderSpinner(String label) {
        startSpinner(label);
    }

    @Override
    public void stopSpinner(String finalMessage) {
        if (finalMessage != null && !finalMessage.isEmpty()) {
            System.out.println(finalMessage);
            System.out.flush();
        }
    }

    // ------------------------------------------------------------------ //
    //  Tool display                                                        //
    // ------------------------------------------------------------------ //

    @Override
    public void printToolCall(String name, Map<String, Object> args) {
        System.out.println();
        System.out.println("--- " + name + " ---");
        if (args != null) {
            for (var entry : args.entrySet()) {
                String value = truncate(String.valueOf(entry.getValue()), 200);
                System.out.println("  " + entry.getKey() + ": " + value);
            }
        }
    }

    @Override
    public void printToolResult(String name, String output) {
        if (output == null || output.isEmpty()) return;
        String[] lines = output.split("\n", -1);
        int maxLines = 50;
        for (int i = 0; i < Math.min(lines.length, maxLines); i++) {
            System.out.println(lines[i]);
        }
        if (lines.length > maxLines) {
            System.out.println("... (" + (lines.length - maxLines) + " more lines)");
        }
    }

    @Override
    public void printToolTiming(long startNanos) {
        long elapsed = System.nanoTime() - startNanos;
        double seconds = elapsed / 1_000_000_000.0;
        System.out.println("  completed in " + String.format("%.1fs", seconds));
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
        System.out.println("ERROR: " + message);
    }

    @Override
    public void printPrompt() {
        System.out.println();
        System.out.print("you> ");
        System.out.flush();
    }

    @Override
    public void printPromptWithCost(String costInfo) {
        System.out.println();
        System.out.print("you [" + costInfo + "]> ");
        System.out.flush();
    }

    @Override
    public void printUsage(String formatted) {
        System.out.println(formatted);
    }

    @Override
    public void println(String text) {
        System.out.println(text);
    }

    @Override
    public void printPermissionDenied(String toolName) {
        System.out.println("WARNING: Permission denied for " + toolName);
    }

    // ------------------------------------------------------------------ //
    //  Diff                                                                //
    // ------------------------------------------------------------------ //

    @Override
    public void renderDiff(String diff) {
        if (diff == null || diff.isEmpty()) return;
        System.out.println(diff);
    }

    // ------------------------------------------------------------------ //
    //  Tool call with status                                               //
    // ------------------------------------------------------------------ //

    @Override
    public void renderToolCall(String name, Map<String, Object> args, ToolCallStatus status) {
        System.out.println();
        System.out.println("--- " + name + " [" + status.name().toLowerCase() + "] ---");
        if (args != null) {
            for (var entry : args.entrySet()) {
                String value = truncate(String.valueOf(entry.getValue()), 200);
                System.out.println("  " + entry.getKey() + ": " + value);
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Agent status                                                        //
    // ------------------------------------------------------------------ //

    @Override
    public void renderAgentStatus(String agentName, AgentStatus status, String detail) {
        String icon = switch (status) {
            case STARTING, RUNNING -> "~";
            case WAITING           -> "...";
            case COMPLETED         -> "+";
            case FAILED            -> "!";
        };
        String line = icon + " " + agentName + "  " + status.name().toLowerCase();
        if (detail != null && !detail.isEmpty()) {
            line += "  " + detail;
        }
        System.out.println(line);
    }

    // ------------------------------------------------------------------ //
    //  Progress bar                                                        //
    // ------------------------------------------------------------------ //

    @Override
    public void renderProgress(String label, int current, int total) {
        int percent = total > 0 ? (int) Math.round((double) current / total * 100) : 0;
        System.out.println(label + " " + current + "/" + total + " (" + percent + "%)");
    }

    // ------------------------------------------------------------------ //
    //  Context bar                                                         //
    // ------------------------------------------------------------------ //

    @Override
    public void renderContextBar(int used, int total) {
        int percent = total > 0 ? (int) Math.round((double) used / total * 100) : 0;
        System.out.println("Context: " + used + "/" + total + " tokens (" + percent + "%)");
    }

    // ------------------------------------------------------------------ //
    //  Box                                                                 //
    // ------------------------------------------------------------------ //

    @Override
    public void renderBox(String title, List<String> content, BoxStyle style) {
        System.out.println("[" + style.name() + "]" + (title != null ? " " + title : ""));
        if (content != null) {
            for (String line : content) {
                System.out.println("  " + line);
            }
        }
        System.out.println("[/" + style.name() + "]");
    }

    // ------------------------------------------------------------------ //
    //  Table                                                               //
    // ------------------------------------------------------------------ //

    @Override
    public void renderTable(List<String> headers, List<List<String>> rows) {
        if (headers == null || headers.isEmpty()) return;
        System.out.println(String.join(" | ", headers));
        System.out.println("-".repeat(headers.stream().mapToInt(String::length).sum()
                + (headers.size() - 1) * 3));
        if (rows != null) {
            for (List<String> row : rows) {
                System.out.println(String.join(" | ", row));
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Markdown                                                            //
    // ------------------------------------------------------------------ //

    @Override
    public void renderMarkdown(String markdown) {
        if (markdown == null) return;
        // Strip common markdown markers for plain output
        String result = markdown
                .replaceAll("^#{1,6} ", "")
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("`(.+?)`", "$1")
                .replaceAll("^[-*] ", "- ");
        System.out.println(result);
    }

    // ------------------------------------------------------------------ //
    //  Private helpers                                                     //
    // ------------------------------------------------------------------ //

    private static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }
}
