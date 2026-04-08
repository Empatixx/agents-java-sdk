package cz.krokviak.agents.cli.skill;

import java.util.Map;

public record Skill(
    String name,
    String description,
    String content,
    Map<String, String> metadata,
    String sourcePath
) {
    public boolean isUserInvocable() {
        return "true".equals(metadata.getOrDefault("user_invocable", "false"));
    }
}
