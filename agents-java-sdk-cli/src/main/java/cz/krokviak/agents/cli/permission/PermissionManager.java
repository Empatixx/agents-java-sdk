package cz.krokviak.agents.cli.permission;

import cz.krokviak.agents.cli.render.tui.PermissionDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PermissionManager {

    public enum PermissionMode { DEFAULT, TRUST, DENY_ALL }
    public enum PermissionResult { ALLOW, DENY }

    private static final Set<String> READ_ONLY_TOOLS = Set.of(
        "read_file", "glob", "grep", "list_directory", "sub_agent"
    );

    private final PermissionMode mode;
    private final List<PermissionRule> sessionRules = new ArrayList<>();

    public PermissionManager(PermissionMode mode) {
        this.mode = mode;
    }

    public PermissionResult check(String toolName, Map<String, Object> args) {
        if (mode == PermissionMode.TRUST) return PermissionResult.ALLOW;
        if (READ_ONLY_TOOLS.contains(toolName)) return PermissionResult.ALLOW;
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

        int selected = PermissionDialog.prompt(header, options);

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

    public void listRules() {
        if (sessionRules.isEmpty()) {
            System.out.println("No session permission rules. Mode: " + mode);
            return;
        }
        System.out.println("Permission mode: " + mode);
        for (PermissionRule rule : sessionRules) {
            System.out.println("  " + rule.behavior() + " " + rule.toolName() +
                (rule.pattern() != null ? " [" + rule.pattern() + "]" : ""));
        }
    }
}
