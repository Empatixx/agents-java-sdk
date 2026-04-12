package cz.krokviak.agents.cli.permission;

import cz.krokviak.agents.api.AgentService;
import cz.krokviak.agents.api.dto.PermissionDecision;
import cz.krokviak.agents.cli.render.PromptRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class PermissionManager {

    public enum PermissionMode { DEFAULT, TRUST, DENY_ALL }
    public enum PermissionResult { ALLOW, DENY }

    // Tools that never need permission (read-only + safe operations)
    private static final Set<String> AUTO_ALLOW_TOOLS = Set.of(
        "read_file", "glob", "grep", "list_directory", "sub_agent",
        "agent", "write_file", "edit_file", "web_fetch", "web_search",
        "tool_search", "skill", "task_create", "task_update", "task_get", "task_list",
        "memory_read", "memory_write", "send_message", "brief"
    );

    // Only these tools require permission prompt
    private static final Set<String> PERMISSION_REQUIRED = Set.of("bash");

    private final PermissionMode mode;
    private final List<PermissionRule> sessionRules = new ArrayList<>();
    private volatile PromptRenderer promptRenderer;
    private volatile cz.krokviak.agents.cli.render.Renderer renderer;
    private volatile AgentService agentService;

    public PermissionManager(PermissionMode mode) {
        this.mode = mode;
    }

    public void setPromptRenderer(PromptRenderer pr) {
        this.promptRenderer = pr;
    }
    public void setRenderer(cz.krokviak.agents.cli.render.Renderer renderer) {
        this.renderer = renderer;
    }

    /**
     * Attach the {@link AgentService} so permission prompts go through the
     * UI-agnostic event bus (PermissionRequested) rather than calling
     * {@link PromptRenderer} directly. Without an AgentService installed,
     * we fall back to the legacy synchronous path (used by headless tests).
     */
    public void setAgentService(AgentService agentService) {
        this.agentService = agentService;
    }

    public PermissionResult check(String toolName, Map<String, Object> args) {
        if (mode == PermissionMode.TRUST) return PermissionResult.ALLOW;
        if (!PERMISSION_REQUIRED.contains(toolName)) return PermissionResult.ALLOW;
        if (mode == PermissionMode.DENY_ALL) return PermissionResult.DENY;

        String target = extractTarget(toolName, args);
        for (PermissionRule rule : sessionRules) {
            if (rule.matches(toolName, target)) {
                return rule.behavior() == PermissionRule.Behavior.ALLOW
                    ? PermissionResult.ALLOW : PermissionResult.DENY;
            }
        }

        return promptUser(toolName, args, target);
    }

    private PermissionResult promptUser(String toolName, Map<String, Object> args, String target) {
        String detail = target != null ? target : args.toString();
        if (detail.length() > 100) detail = detail.substring(0, 100) + "...";
        String header = "⚠ Allow " + toolName + "(" + detail + ")?";
        List<String> options = List.of(
            "Yes, this time",
            "Yes, always for " + toolName,
            "No"
        );

        // Preferred path: async event via AgentService — works headless (GraphQL) and TUI alike.
        if (agentService != null) {
            try {
                PermissionDecision decision = agentService
                    .requestPermission(toolName, args, /*toolCallId*/ null, options)
                    .get();
                return applyDecision(decision, toolName);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return PermissionResult.DENY;
            } catch (ExecutionException e) {
                return PermissionResult.DENY;
            }
        }

        // Legacy fallback (no AgentService wired): direct prompt or auto-deny.
        int selected;
        if (promptRenderer != null) {
            selected = promptRenderer.promptSelection(header, options.toArray(new String[0]));
        } else if (renderer != null) {
            renderer.println(header);
            for (int i = 0; i < options.size(); i++) renderer.println("  " + (i + 1) + ") " + options.get(i));
            selected = 2;
        } else {
            selected = 2;
        }
        return applyDecision(mapLegacyIndex(selected), toolName);
    }

    private PermissionDecision mapLegacyIndex(int idx) {
        return switch (idx) {
            case 0 -> PermissionDecision.ALLOW_ONCE;
            case 1 -> PermissionDecision.ALLOW_FOR_SESSION;
            default -> PermissionDecision.DENY;
        };
    }

    private PermissionResult applyDecision(PermissionDecision decision, String toolName) {
        return switch (decision) {
            case ALLOW_ONCE -> PermissionResult.ALLOW;
            case ALLOW_FOR_SESSION, ALLOW_ALWAYS -> {
                sessionRules.add(new PermissionRule(toolName, "*", PermissionRule.Behavior.ALLOW));
                yield PermissionResult.ALLOW;
            }
            case DENY -> PermissionResult.DENY;
        };
    }

    private String extractTarget(String toolName, Map<String, Object> args) {
        return switch (toolName) {
            case "bash" -> args.get("command") instanceof String s ? s : null;
            case "write_file", "edit_file" -> args.get("file_path") instanceof String s ? s : null;
            default -> null;
        };
    }

    public List<PermissionRule> sessionRules() { return List.copyOf(sessionRules); }

    public void listRules(cz.krokviak.agents.cli.render.Renderer out) {
        if (sessionRules.isEmpty()) {
            out.println("No session permission rules. Mode: " + mode);
            return;
        }
        out.println("Permission mode: " + mode);
        for (PermissionRule rule : sessionRules) {
            out.println("  " + rule.behavior() + " " + rule.toolName() +
                (rule.pattern() != null ? " [" + rule.pattern() + "]" : ""));
        }
    }
}
