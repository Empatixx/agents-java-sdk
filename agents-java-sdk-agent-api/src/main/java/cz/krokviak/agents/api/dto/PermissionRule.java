package cz.krokviak.agents.api.dto;

/**
 * A single session-scoped permission rule. Listed via
 * {@link cz.krokviak.agents.api.AgentService#permissionRules()}.
 *
 * @param pattern tool name (optionally with a scope pattern in brackets,
 *                e.g. {@code "bash[*]"} or {@code "write_file[src/main/**]"})
 * @param mode    either {@code "ALLOW"} or {@code "DENY"}
 */
public record PermissionRule(
    String pattern,
    String mode
) {}
