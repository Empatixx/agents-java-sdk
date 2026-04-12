package cz.krokviak.agents.agent.permission;

import java.util.regex.Pattern;

public record PermissionRule(String toolName, String pattern, Behavior behavior) {

    public enum Behavior { ALLOW, DENY }

    public boolean matches(String tool, String target) {
        if (!this.toolName.equals("*") && !this.toolName.equals(tool)) {
            return false;
        }
        if (pattern == null || pattern.equals("*")) {
            return true;
        }
        // Convert glob to regex: * → .*, ? → .
        String regex = Pattern.quote(pattern).replace("\\*", ".*").replace("\\?", ".");
        return target != null && target.matches(regex);
    }
}
