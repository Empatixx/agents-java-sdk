package cz.krokviak.agents.cli.render.tui;

import cz.krokviak.agents.cli.render.AgentStatus;
import cz.krokviak.agents.cli.render.BoxStyle;
import cz.krokviak.agents.cli.render.Renderer;
import cz.krokviak.agents.cli.render.ToolCallStatus;
import dev.tamboui.toolkit.app.ToolkitRunner;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Thread-safe Renderer → CliState bridge.
 * All methods mutate CliState via runOnRenderThread().
 * OutputLine types enable in-place updates (reactive list).
 */
public final class TuiRenderer implements Renderer {

    private final CliState state;
    private volatile ToolkitRunner runner;
    private final ConcurrentLinkedQueue<Runnable> pendingBeforeReady = new ConcurrentLinkedQueue<>();

    public TuiRenderer(CliState state) {
        this.state = state;
    }

    public void activate(ToolkitRunner runner) {
        this.runner = runner;
        Runnable r;
        while ((r = pendingBeforeReady.poll()) != null) {
            runner.runOnRenderThread(r);
        }
    }

    private void onRenderThread(Runnable action) {
        ToolkitRunner r = runner;
        if (r != null) r.runOnRenderThread(action);
        else pendingBeforeReady.add(action);
    }

    // ========================= Core output =========================

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
        if ("agent".equals(name)) return; // agent shown via renderAgentStatus
        String inlineArgs = formatArgs(args);
        onRenderThread(() -> {
            boolean inAgent = state.activeAgentName() != null;
            state.addLine(new OutputLine.ToolCall(name, inlineArgs, ToolCallStatus.RUNNING, inAgent));
            if (inAgent) {
                state.pushAgentToolCall("● " + name + "(" + inlineArgs + ")");
            }
        });
    }

    @Override
    public void printToolResult(String name, String output) {
        if (output == null || output.isEmpty()) return;
        if ("agent".equals(name)) return;
        String[] allLines = output.split("\n", -1);
        // Store timing start — will be merged when printToolTiming is called
        onRenderThread(() -> {
            state.updateLast(OutputLine.ToolCall.class, tc -> tc.withStatus(ToolCallStatus.COMPLETED));
            int previewCount = Math.min(allLines.length, CliState.COLLAPSED_PREVIEW_LINES);
            String[] preview = new String[previewCount];
            System.arraycopy(allLines, 0, preview, 0, previewCount);
            state.addLine(new OutputLine.ToolOutput(preview, allLines.length, 0, allLines.length <= previewCount));
            if (allLines.length > previewCount) {
                state.pushCollapsed(new CliState.CollapsedResult(output, allLines.length));
            }
        });
    }

    @Override
    public void printToolTiming(long startNanos) {
        long ms = (System.nanoTime() - startNanos) / 1_000_000;
        onRenderThread(() -> {
            // Merge timing into last ToolOutput
            state.updateLast(OutputLine.ToolOutput.class,
                to -> new OutputLine.ToolOutput(to.previewLines(), to.totalLines(), ms, to.expanded()));
        });
    }

    @Override
    public void printTextDelta(String delta) {
        onRenderThread(() -> {
            state.appendStreaming(delta);
            state.flushStreamingBuffer(line -> state.addLine(new OutputLine.Text(line)));
        });
    }

    @Override
    public void printError(String message) {
        onRenderThread(() -> state.addLine(new OutputLine.Error(message)));
    }

    @Override
    public void printPrompt() {
        onRenderThread(() -> state.flushAll(line -> state.addLine(new OutputLine.Text(line))));
    }

    @Override
    public void printPromptWithCost(String costInfo) {
        onRenderThread(() -> state.flushAll(line -> state.addLine(new OutputLine.Text(line))));
    }

    @Override
    public void printUsage(String formatted) {
        onRenderThread(() -> {
            for (String line : formatted.split("\n")) {
                state.addLine(new OutputLine.Dim(line));
            }
        });
    }

    @Override
    public void println(String textStr) {
        onRenderThread(() -> state.addLine(new OutputLine.Text(textStr)));
    }

    @Override
    public void printPermissionDenied(String toolName) {
        onRenderThread(() -> state.addLine(new OutputLine.PermissionDenied(toolName)));
    }

    // ========================= Rich rendering =========================

    @Override
    public void renderDiff(String diff) {
        onRenderThread(() -> {
            for (String line : diff.split("\n")) {
                state.addLine(new OutputLine.DiffLine(line));
            }
        });
    }

    @Override
    public void renderToolCall(String name, Map<String, Object> args, ToolCallStatus status) {
        if (state.activeAgentName() != null) return;
        String inlineArgs = formatArgs(args);
        onRenderThread(() -> {
            // Try to update existing tool call line, otherwise add new
            boolean updated = state.updateLast(OutputLine.ToolCall.class,
                tc -> tc.name().equals(name) ? tc.withStatus(status) : tc);
            if (!updated) {
                state.addLine(new OutputLine.ToolCall(name, inlineArgs, status));
            }
        });
    }

    @Override
    public void renderAgentStatus(String agentName, AgentStatus status, String detail) {
        onRenderThread(() -> {
            if (status == AgentStatus.RUNNING || status == AgentStatus.STARTING) {
                state.setActiveAgent(agentName);
                if (detail != null) state.setAgentDetail(detail);
                // Add or update agent line in output log
                boolean updated = state.updateLast(OutputLine.Agent.class,
                    a -> a.name().equals(agentName) ? a.withStatus(status, detail) : a);
                if (!updated) {
                    state.addLine(new OutputLine.Agent(agentName, status, detail));
                }
                return;
            }
            // Final status
            state.clearActiveAgent();
            state.updateLast(OutputLine.Agent.class,
                a -> a.name().equals(agentName) ? a.withStatus(status, detail) : a);
        });
    }

    @Override
    public void renderProgress(String label, int current, int total) {
        int pct = total > 0 ? (int) ((double) current / total * 100) : 0;
        onRenderThread(() -> state.addLine(new OutputLine.Dim(label + " " + pct + "%")));
    }

    @Override
    public void renderSpinner(String label) { startSpinner(label); }

    @Override
    public void stopSpinner(String finalMessage) {
        onRenderThread(() -> {
            state.setSpinner(false, null);
            state.addLine(new OutputLine.Text("✓ " + finalMessage));
        });
    }

    @Override
    public void renderContextBar(int used, int total) {
        onRenderThread(() -> state.setTokens(used, total));
    }

    @Override
    public void renderBox(String title, List<String> content, BoxStyle style) {
        onRenderThread(() -> {
            if (title != null) state.addLine(new OutputLine.Text("── " + title + " ──"));
            for (String line : content) state.addLine(new OutputLine.Text("  " + line));
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
            state.addLine(new OutputLine.Text(sb.toString()));
            for (var row : rows) {
                var rsb = new StringBuilder();
                for (int i = 0; i < row.size(); i++) {
                    if (i > 0) rsb.append(" │ ");
                    rsb.append(row.get(i));
                }
                state.addLine(new OutputLine.Text(rsb.toString()));
            }
        });
    }

    @Override
    public void renderMarkdown(String markdown) {
        onRenderThread(() -> {
            for (String line : markdown.split("\n")) {
                state.addLine(new OutputLine.Text(line));
            }
        });
    }

    // ========================= Expand/collapse =========================

    public void toggleExpandCollapse() {
        onRenderThread(() -> {
            if (!state.hasCollapsed()) return;
            CliState.CollapsedResult cr = state.popCollapsed();
            if (cr == null) return;
            String[] allLines = cr.output().split("\n", -1);
            // Update last ToolOutput to show all lines
            state.updateLast(OutputLine.ToolOutput.class, to -> to.withExpanded(allLines));
        });
    }

    // ========================= Permission prompt =========================

    private final BlockingQueue<Integer> permissionResult = new LinkedBlockingQueue<>();

    public int promptPermission(String header, String[] options) {
        onRenderThread(() -> state.setPermissionPrompt(header, options));
        try { return permissionResult.take(); }
        catch (InterruptedException e) { return options.length - 1; }
    }

    public void resolvePermission(int selectedIndex) {
        onRenderThread(() -> state.clearPermissionPrompt());
        permissionResult.offer(selectedIndex);
    }

    public boolean hasPermissionPrompt() {
        return state.hasPermissionPrompt();
    }

    // ========================= Helpers =========================

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
