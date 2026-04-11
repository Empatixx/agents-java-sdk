package cz.krokviak.agents.cli.permission;

import cz.krokviak.agents.cli.render.PromptRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public PermissionManager(PermissionMode mode) {
        this.mode = mode;
    }

    public void setPromptRenderer(PromptRenderer pr) {
        this.promptRenderer = pr;
    }
    public void setRenderer(cz.krokviak.agents.cli.render.Renderer renderer) {
        this.renderer = renderer;
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
        String[] options = {
            "Yes, this time",
            "Yes, always for " + toolName,
            "No"
        };

        int selected;
        if (promptRenderer != null) {
            selected = promptRenderer.promptSelection(header, options);
        } else if (renderer != null) {
            // Fallback: show via renderer, auto-deny
            renderer.println(header);
            for (int i = 0; i < options.length; i++) renderer.println("  " + (i + 1) + ") " + options[i]);
            selected = 2; // auto-deny in non-interactive
        } else {
            selected = 2;
        }

        return switch (selected) {
            case 0 -> PermissionResult.ALLOW;
            case 1 -> {
                sessionRules.add(new PermissionRule(toolName, "*", PermissionRule.Behavior.ALLOW));
                yield PermissionResult.ALLOW;
            }
            default -> PermissionResult.DENY;
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
