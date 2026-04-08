package cz.krokviak.agents.cli.permission;

import java.io.BufferedReader;
import java.io.IOException;
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
    private final BufferedReader reader;
    private final List<PermissionRule> sessionRules = new ArrayList<>();

    public PermissionManager(PermissionMode mode, BufferedReader reader) {
        this.mode = mode;
        this.reader = reader;
    }

    public PermissionResult check(String toolName, Map<String, Object> args) {
        if (mode == PermissionMode.TRUST) return PermissionResult.ALLOW;
        if (READ_ONLY_TOOLS.contains(toolName)) return PermissionResult.ALLOW;
        if (mode == PermissionMode.DENY_ALL) return PermissionResult.DENY;

        // Check session rules
        String target = extractTarget(toolName, args);
        for (PermissionRule rule : sessionRules) {
            if (rule.matches(toolName, target)) {
                return rule.behavior() == PermissionRule.Behavior.ALLOW
                    ? PermissionResult.ALLOW : PermissionResult.DENY;
            }
        }

        // Ask user
        return promptUser(toolName, args, target);
    }

    private PermissionResult promptUser(String toolName, Map<String, Object> args, String target) {
        String detail = target != null ? target : args.toString();
        if (detail.length() > 100) detail = detail.substring(0, 100) + "...";

        System.out.print("\033[33m⚠ Allow " + toolName + "(" + detail + ")? [y/n/always] \033[0m");
        System.out.flush();

        try {
            String response = reader.readLine();
            if (response == null) return PermissionResult.DENY;
            response = response.trim().toLowerCase();

            return switch (response) {
                case "y", "yes" -> PermissionResult.ALLOW;
                case "always", "a" -> {
                    sessionRules.add(new PermissionRule(toolName, "*", PermissionRule.Behavior.ALLOW));
                    System.out.println("\033[2mAdded always-allow rule for " + toolName + "\033[0m");
                    yield PermissionResult.ALLOW;
                }
                default -> PermissionResult.DENY;
            };
        } catch (IOException e) {
            return PermissionResult.DENY;
        }
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
        System.out.println("Session rules:");
        for (PermissionRule rule : sessionRules) {
            System.out.println("  " + rule.behavior() + " " + rule.toolName() +
                (rule.pattern() != null ? " [" + rule.pattern() + "]" : ""));
        }
    }
}
