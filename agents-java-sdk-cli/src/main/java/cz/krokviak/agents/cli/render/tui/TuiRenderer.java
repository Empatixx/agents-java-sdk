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
 * Thread-safe bridge: Renderer calls → CliController commands via runOnRenderThread.
 */
public final class TuiRenderer implements Renderer, cz.krokviak.agents.cli.render.PromptRenderer {

    private final CliController ctrl;
    private volatile ToolkitRunner runner;
    private final ConcurrentLinkedQueue<Runnable> pending = new ConcurrentLinkedQueue<>();

    private static final ThreadLocal<String> CURRENT_AGENT = new ThreadLocal<>();

    @Override public void setCurrentAgent(String agentId) { CURRENT_AGENT.set(agentId); }
    @Override public void clearCurrentAgent() { CURRENT_AGENT.remove(); }

    public TuiRenderer(CliController ctrl) {
        this.ctrl = ctrl;
    }

    public void activate(ToolkitRunner runner) {
        this.runner = runner;
        Runnable r;
        while ((r = pending.poll()) != null) runner.runOnRenderThread(r);
    }

    private void ui(Runnable action) {
        var r = runner;
        if (r != null) r.runOnRenderThread(action);
        else pending.add(action);
    }

    // ---- Core ----

    @Override public void startSpinner(String msg) { ui(() -> ctrl.setSpinner(true, msg)); }
    @Override public void stopSpinner() { ui(() -> ctrl.setSpinner(false, null)); }

    @Override
    public void printToolCall(String name, Map<String, Object> args) {
        if ("agent".equals(name)) return;
        String a = fmtArgs(args);
        String agentName = CURRENT_AGENT.get(); // which agent thread is this?
        ui(() -> {
            boolean inAgent = agentName != null && ctrl.hasActiveAgents();
            var toolLine = new OutputLine.ToolCall(name, a, ToolCallStatus.RUNNING, inAgent);
            if (inAgent) {
                ctrl.pushAgentToolCall(agentName, "● " + name + "(" + a + ")");
                ctrl.addLineAfterAgent(agentName, toolLine);
            } else {
                ctrl.addLine(toolLine);
            }
        });
    }

    @Override
    public void printToolResult(String name, String output) {
        if ("agent".equals(name)) return;
        int lineCount = (output != null && !output.isEmpty()) ? output.split("\n", -1).length : 0;
        ui(() -> {
            ctrl.updateLast(OutputLine.ToolCall.class, tc -> tc.withStatus(ToolCallStatus.COMPLETED));
            if (output != null && !output.isEmpty()) {
                ctrl.pushCollapsed(new CliController.CollapsedResult(output, lineCount));
            }
        });
    }

    @Override
    public void printToolTiming(long startNanos) {
        long ms = (System.nanoTime() - startNanos) / 1_000_000;
        ui(() -> ctrl.updateLast(OutputLine.ToolCall.class, tc -> tc.withResult(
            ctrl.hasCollapsed() ? ctrl.peekCollapsedLines() : 0, ms)));
    }

    @Override
    public void printTextDelta(String delta) {
        ui(() -> { ctrl.appendStreaming(delta); ctrl.flushStreamingLines(); });
    }

    @Override public void printError(String msg) { ui(() -> ctrl.addLine(new OutputLine.Error(msg))); }

    @Override public void printPrompt() {
        ui(() -> { ctrl.flushAll(); ctrl.resetResponseState(); });
    }

    @Override public void printPromptWithCost(String c) {
        ui(() -> { ctrl.flushAll(); ctrl.resetResponseState(); });
    }

    @Override public void printUsage(String f) {
        ui(() -> { for (var l : f.split("\n")) ctrl.addLine(new OutputLine.Dim(l)); });
    }

    @Override public void println(String t) { ui(() -> ctrl.addLine(new OutputLine.Text(t))); }

    @Override public void printPermissionDenied(String t) { ui(() -> ctrl.addLine(new OutputLine.PermissionDenied(t))); }

    // ---- Rich ----

    @Override public void renderDiff(String d) {
        ui(() -> { for (var l : d.split("\n")) ctrl.addLine(new OutputLine.DiffLine(l)); });
    }

    @Override
    public void renderToolCall(String name, Map<String, Object> args, ToolCallStatus status) {
        if ("agent".equals(name) || ctrl.hasActiveAgents()) return;
        String a = fmtArgs(args);
        ui(() -> {
            if (!ctrl.updateLast(OutputLine.ToolCall.class, tc -> tc.name().equals(name) ? tc.withStatus(status) : tc))
                ctrl.addLine(new OutputLine.ToolCall(name, a, status));
        });
    }

    @Override
    public void renderAgentStatus(String name, AgentStatus status, String detail) {
        ui(() -> {
            if (status == AgentStatus.RUNNING || status == AgentStatus.STARTING) {
                ctrl.activateAgent(name);
                if (detail != null) ctrl.updateAgentDetail(name, detail);
                // Update THIS agent's line by name, or add new
                if (!ctrl.updateLast(OutputLine.Agent.class, a -> a.name().equals(name), a -> a.withStatus(status, detail)))
                    ctrl.addLine(new OutputLine.Agent(name, status, detail));
                return;
            }
            ctrl.deactivateAgent(name);
            ctrl.updateLast(OutputLine.Agent.class, a -> a.name().equals(name), a -> a.withStatus(status, detail));
        });
    }

    @Override public void renderProgress(String l, int cur, int tot) {
        int p = tot > 0 ? (int)((double)cur/tot*100) : 0;
        ui(() -> ctrl.addLine(new OutputLine.Dim(l + " " + p + "%")));
    }

    @Override public void renderSpinner(String l) { startSpinner(l); }

    @Override public void stopSpinner(String msg) {
        ui(() -> { ctrl.setSpinner(false, null); ctrl.addLine(new OutputLine.Text("✓ " + msg)); });
    }

    @Override public void renderContextBar(int u, int t) { ui(() -> ctrl.setTokens(u, t)); }

    @Override public void renderBox(String title, List<String> content, BoxStyle s) {
        ui(() -> { if (title != null) ctrl.addLine(new OutputLine.Text("── " + title + " ──"));
            for (var l : content) ctrl.addLine(new OutputLine.Text("  " + l)); });
    }

    @Override public void renderTable(List<String> h, List<List<String>> rows) {
        ui(() -> { var sb = new StringBuilder(); for (int i=0;i<h.size();i++) { if(i>0) sb.append(" │ "); sb.append(h.get(i)); }
            ctrl.addLine(new OutputLine.Text(sb.toString()));
            for (var r : rows) { var rsb = new StringBuilder(); for (int i=0;i<r.size();i++) { if(i>0) rsb.append(" │ "); rsb.append(r.get(i)); }
                ctrl.addLine(new OutputLine.Text(rsb.toString())); } });
    }

    @Override public void renderMarkdown(String md) {
        ui(() -> { for (var l : md.split("\n")) ctrl.addLine(new OutputLine.Text(l)); });
    }

    // ---- Expand ----

    public void toggleExpand() {
        ui(() -> { if (!ctrl.hasCollapsed()) return; var cr = ctrl.popCollapsed(); if (cr == null) return;
            for (var l : cr.output().split("\n")) ctrl.addLine(new OutputLine.Result(l)); });
    }

    // ---- Permission ----

    private final BlockingQueue<Integer> permResult = new LinkedBlockingQueue<>();

    @Override
    public int promptSelection(String header, String[] options) {
        return promptPermission(header, options);
    }

    public int promptPermission(String header, String[] options) {
        ui(() -> ctrl.setPermissionPrompt(header, options));
        try { return permResult.take(); } catch (InterruptedException e) { return options.length - 1; }
    }

    public void resolvePermission(int idx) {
        ui(() -> ctrl.clearPermissionPrompt());
        permResult.offer(idx);
    }

    public boolean hasPermissionPrompt() { return ctrl.hasPermissionPrompt(); }

    public String consumeQueuedPrompt() {
        return ctrl.consumeQueuedPrompt();
    }

    // ---- Text input prompt ----

    private final BlockingQueue<String> textResult = new LinkedBlockingQueue<>();

    /**
     * Show a text input panel with a bold header. Returns the typed text, or empty on Escape.
     */
    @Override
    public String promptTextInput(String header, String placeholder) {
        ui(() -> ctrl.setTextInputPrompt(header, placeholder));
        try { return textResult.take(); } catch (InterruptedException e) { return ""; }
    }

    public void resolveTextInput(String value) {
        ui(() -> ctrl.clearTextInputPrompt());
        textResult.offer(value != null ? value : "");
    }

    /**
     * Multi-question prompt with left/right navigation between questions.
     * Returns the confirmed answers as a Map of header → answer.
     */
    public java.util.Map<String, String> promptMultiQuestion(java.util.List<CliController.QuestionCard> questions) {
        ui(() -> ctrl.setMultiQuestions(questions));
        // Wait for all questions to be answered (signaled by resolvePermission(-1))
        try { permResult.take(); } catch (InterruptedException ignored) {}
        // Collect answers
        var answers = new java.util.LinkedHashMap<String, String>();
        String[] confirmed = ctrl.confirmedAnswers();
        for (int i = 0; i < questions.size(); i++) {
            String tag = questions.get(i).header() != null ? questions.get(i).header() : "Q" + (i + 1);
            answers.put(tag, confirmed != null && confirmed[i] != null ? confirmed[i] : "(no answer)");
        }
        ui(() -> ctrl.clearPermissionPrompt());
        return answers;
    }

    // ---- Helpers ----

    private String fmtArgs(Map<String, Object> args) {
        if (args == null || args.isEmpty()) return "";
        var sb = new StringBuilder(); int i = 0;
        for (var e : args.entrySet()) { if (i++ > 0) sb.append(", ");
            String v = String.valueOf(e.getValue()); if (v.length() > 60) v = v.substring(0,57) + "...";
            sb.append(e.getKey()).append("=").append(v); if (i >= 3) { sb.append(", ..."); break; } }
        return sb.toString();
    }
}
