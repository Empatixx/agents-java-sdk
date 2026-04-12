package cz.krokviak.agents.agent.permission;

import cz.krokviak.agents.api.AgentService;
import cz.krokviak.agents.api.dto.PermissionDecision;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class PermissionManager {

    public enum PermissionMode { DEFAULT, TRUST, DENY_ALL }
    public enum PermissionResult { ALLOW, DENY }

    private static final Set<String> PERMISSION_REQUIRED = Set.of("bash");

    private final PermissionMode mode;
    private final List<PermissionRule> sessionRules = new ArrayList<>();
    private volatile AgentService agentService;

    public PermissionManager(PermissionMode mode) {
        this.mode = mode;
    }

    /**
     * Attach the {@link AgentService} — permission prompts go through the
     * UI-agnostic event bus (PermissionRequested) and are resolved by whatever
     * frontend is installed. Without an AgentService set, prompts auto-deny
     * (headless safe default).
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

        return promptUser(toolName, args);
    }

    private PermissionResult promptUser(String toolName, Map<String, Object> args) {
        List<String> options = List.of(
            "Yes, this time",
            "Yes, always for " + toolName,
            "No"
        );
        if (agentService == null) return PermissionResult.DENY;
        PermissionDecision decision = cz.krokviak.agents.util.FutureTimeouts.awaitUserPrompt(
            agentService.requestPermission(toolName, args, /*toolCallId*/ null, options),
            () -> PermissionDecision.DENY);
        return applyDecision(decision, toolName);
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

    public PermissionMode mode() { return mode; }
    public List<PermissionRule> sessionRules() { return List.copyOf(sessionRules); }
}
