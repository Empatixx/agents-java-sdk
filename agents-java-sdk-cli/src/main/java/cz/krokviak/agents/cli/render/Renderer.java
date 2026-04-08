package cz.krokviak.agents.cli.render;

import java.util.List;
import java.util.Map;

/**
 * Pluggable renderer interface. Implementations may use ANSI codes (AnsiRenderer)
 * or produce plain text suitable for piped output (PlainRenderer).
 */
public interface Renderer {

    // --- From TerminalOutput ---

    void startSpinner(String message);

    void stopSpinner();

    void printToolCall(String name, Map<String, Object> args);

    void printToolResult(String name, String output);

    void printToolTiming(long startNanos);

    void printTextDelta(String delta);

    void printError(String message);

    void printPrompt();

    void printPromptWithCost(String costInfo);

    void printUsage(String formatted);

    void println(String text);

    void printPermissionDenied(String toolName);

    // --- New rendering methods ---

    /**
     * Render a unified diff string with coloured +/- lines.
     */
    void renderDiff(String diff);

    /**
     * Render a tool call with a status badge (PENDING/RUNNING/COMPLETED/FAILED).
     */
    void renderToolCall(String name, Map<String, Object> args, ToolCallStatus status);

    /**
     * Render a one-line agent status line with an icon and optional detail.
     */
    void renderAgentStatus(String agentName, AgentStatus status, String detail);

    /**
     * Render an inline progress bar.
     *
     * @param label   description shown before the bar
     * @param current current value
     * @param total   maximum value
     */
    void renderProgress(String label, int current, int total);

    /**
     * Start an animated spinner with a label. Call stopSpinner() or
     * renderSpinner(label, finalMessage) to stop it.
     */
    void renderSpinner(String label);

    /**
     * Stop the currently running spinner and print a final message.
     */
    void stopSpinner(String finalMessage);

    /**
     * Render a compact context-usage bar (e.g. "Context: [████░░] 65%").
     *
     * @param used  tokens used
     * @param total token limit
     */
    void renderContextBar(int used, int total);

    /**
     * Render content inside a styled box.
     *
     * @param title   optional title shown in the top border (null for none)
     * @param content lines to display inside the box
     * @param style   visual style of the box
     */
    void renderBox(String title, List<String> content, BoxStyle style);

    /**
     * Render a simple table with a header row and data rows.
     *
     * @param headers column headers
     * @param rows    data rows (each row must have the same number of columns as headers)
     */
    void renderTable(List<String> headers, List<List<String>> rows);

    /**
     * Render a markdown string with basic formatting (bold, code, headers).
     */
    void renderMarkdown(String markdown);
}
