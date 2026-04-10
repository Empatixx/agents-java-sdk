package cz.krokviak.agents.cli.render.tui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;

/**
 * Single source of truth for all TUI state. Pure Java — no TamboUI dependencies.
 * Mutated only on the render thread via TuiRenderer.onRenderThread().
 *
 * <p>Queries return defensive copies or immutable views.
 * Commands modify state — caller is responsible for thread safety.
 */
public final class CliController {

    private static final int MAX_LINES = 5000;
    static final int MAX_VISIBLE_TOOLS = 2;
    static final int COLLAPSED_PREVIEW_LINES = 2;

    // ========================= Output log =========================

    private final List<OutputLine> lines = new ArrayList<>();

    public void addLine(OutputLine line) {
        lines.add(line);
        if (lines.size() > MAX_LINES) lines.subList(0, lines.size() - MAX_LINES).clear();
    }

    /** Update last line matching type AND predicate. */
    @SuppressWarnings("unchecked")
    public <T extends OutputLine> boolean updateLast(Class<T> type, java.util.function.Predicate<T> predicate, Function<T, T> updater) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (type.isInstance(lines.get(i)) && predicate.test((T) lines.get(i))) {
                lines.set(i, updater.apply((T) lines.get(i)));
                return true;
            }
        }
        return false;
    }

    /** Update last line matching type (any instance). */
    @SuppressWarnings("unchecked")
    public <T extends OutputLine> boolean updateLast(Class<T> type, Function<T, T> updater) {
        return updateLast(type, _ -> true, updater);
    }

    /** Collapse old ToolCall groups to keep max N visible. */
    public void collapseOldTools() {
        int count = 0;
        for (var line : lines) { if (line instanceof OutputLine.ToolCall) count++; }
        while (count >= MAX_VISIBLE_TOOLS) {
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i) instanceof OutputLine.ToolCall) {
                    lines.remove(i);
                    if (i < lines.size() && lines.get(i) instanceof OutputLine.CollapseHint) lines.remove(i);
                    count--;
                    break;
                }
            }
        }
    }

    public List<OutputLine> outputLines() { return lines; }

    // ========================= Streaming =========================

    private final StringBuilder streamBuf = new StringBuilder();
    private boolean firstResponseLine = true;

    public void appendStreaming(String delta) { streamBuf.append(delta); }

    public void flushStreamingLines() {
        String content = streamBuf.toString();
        int nl = content.lastIndexOf('\n');
        if (nl < 0) return;
        for (String line : content.substring(0, nl).split("\n", -1)) {
            addLine(firstResponseLine ? new OutputLine.TextStart(line) : new OutputLine.Text(line));
            firstResponseLine = false;
        }
        streamBuf.setLength(0);
        streamBuf.append(content.substring(nl + 1));
    }

    public void flushAll() {
        if (streamBuf.length() > 0) {
            addLine(firstResponseLine ? new OutputLine.TextStart(streamBuf.toString()) : new OutputLine.Text(streamBuf.toString()));
            streamBuf.setLength(0);
        }
    }

    public void resetResponseState() { firstResponseLine = true; }

    // ========================= Spinner =========================

    private boolean spinnerActive;
    private String spinnerLabel = "";

    public void setSpinner(boolean active, String label) {
        this.spinnerActive = active;
        this.spinnerLabel = label != null ? label : "";
    }

    public boolean spinnerActive() { return spinnerActive; }
    public String spinnerLabel() { return spinnerLabel; }

    // ========================= Status bar =========================

    private String model = "";
    private double cost;
    private String permMode = "default";
    private int tokensUsed;
    private int tokensMax;

    public void setModel(String m) { this.model = m; }
    public void setCost(double c) { this.cost = c; }
    public void setPermMode(String m) { this.permMode = m; }
    public void setTokens(int used, int max) { this.tokensUsed = used; this.tokensMax = max; }

    public String model() { return model; }
    public double cost() { return cost; }
    public String permMode() { return permMode; }
    public int tokensUsed() { return tokensUsed; }
    public int tokensMax() { return tokensMax; }

    // ========================= Collapsed results =========================

    public record CollapsedResult(String output, int totalLines) {}
    private final java.util.ArrayDeque<CollapsedResult> collapsed = new java.util.ArrayDeque<>();

    public void pushCollapsed(CollapsedResult r) {
        collapsed.addLast(r);
        while (collapsed.size() > 50) collapsed.pollFirst();
    }
    public CollapsedResult popCollapsed() { return collapsed.pollLast(); }
    public boolean hasCollapsed() { return !collapsed.isEmpty(); }

    // ========================= Permission prompt =========================

    private String permHeader;
    private String[] permOptions;

    public void setPermissionPrompt(String header, String[] options) {
        this.permHeader = header; this.permOptions = options;
    }
    public void clearPermissionPrompt() { this.permHeader = null; this.permOptions = null; }
    public boolean hasPermissionPrompt() { return permOptions != null; }
    public String permissionHeader() { return permHeader; }
    public String[] permissionOptions() { return permOptions; }

    // ========================= Agents (multiple) =========================

    public record AgentInfo(String name, String detail, List<String> toolCalls) {
        public AgentInfo(String name) { this(name, "", new ArrayList<>()); }
    }

    private final LinkedHashMap<String, AgentInfo> agents = new LinkedHashMap<>();

    public void activateAgent(String name) {
        agents.computeIfAbsent(name, AgentInfo::new);
    }

    public void deactivateAgent(String name) {
        agents.remove(name);
    }

    public void updateAgentDetail(String name, String detail) {
        var info = agents.get(name);
        if (info != null) agents.put(name, new AgentInfo(name, detail, info.toolCalls()));
    }

    public void pushAgentToolCall(String agentName, String call) {
        var info = agents.get(agentName);
        if (info != null) {
            info.toolCalls().add(call);
            if (info.toolCalls().size() > 3) info.toolCalls().removeFirst();
        }
    }

    public boolean hasActiveAgents() { return !agents.isEmpty(); }
    public String firstActiveAgent() { return agents.isEmpty() ? null : agents.keySet().iterator().next(); }
    public Collection<AgentInfo> activeAgents() { return agents.values(); }

    /** Insert a line right after the given agent's block, keeping max 2 tool calls per agent. */
    public void addLineAfterAgent(String agentName, OutputLine line) {
        int agentIdx = -1;
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i) instanceof OutputLine.Agent a && a.name().equals(agentName)) {
                agentIdx = i;
                break;
            }
        }
        if (agentIdx < 0) { lines.add(line); return; }

        // Find end of this agent's block
        int blockEnd = agentIdx + 1;
        while (blockEnd < lines.size()) {
            var next = lines.get(blockEnd);
            if (next instanceof OutputLine.ToolCall tc && tc.indented()) blockEnd++;
            else if (next instanceof OutputLine.CollapseHint) blockEnd++;
            else if (next instanceof OutputLine.Timing) blockEnd++;
            else break;
        }

        // Count tool calls in this agent's block
        int toolCount = 0;
        for (int i = agentIdx + 1; i < blockEnd; i++) {
            if (lines.get(i) instanceof OutputLine.ToolCall) toolCount++;
        }

        // Remove oldest tool call groups if >= MAX_VISIBLE_TOOLS
        while (toolCount >= MAX_VISIBLE_TOOLS) {
            for (int i = agentIdx + 1; i < blockEnd; i++) {
                if (lines.get(i) instanceof OutputLine.ToolCall) {
                    lines.remove(i); blockEnd--;
                    if (i < blockEnd && lines.get(i) instanceof OutputLine.CollapseHint) {
                        lines.remove(i); blockEnd--;
                    }
                    toolCount--;
                    break;
                }
            }
        }

        // Insert at end of block
        lines.add(blockEnd, line);
    }

    // ========================= Command trie =========================

    private final CommandTrie trie = new CommandTrie();

    public CommandTrie commandTrie() { return trie; }

    public List<CommandTrie.Match> suggestCommands(String input) {
        if (input == null || !input.startsWith("/") || input.length() < 2) return List.of();
        return trie.search(input.substring(1));
    }

    // ========================= Plan mode =========================

    private boolean planMode;
    private String planSlug;

    public void setPlanMode(boolean active) { this.planMode = active; }
    public boolean isPlanMode() { return planMode; }
    public void setPlanSlug(String slug) { this.planSlug = slug; }
    public String planSlug() { return planSlug; }
}
