package cz.krokviak.agents.api.dto;

/**
 * Decision returned by a frontend for a pending {@code PermissionRequested} event.
 * The frontend (TUI dialog, GraphQL mutation, …) calls
 * {@link cz.krokviak.agents.api.AgentService#resolvePermission(String, PermissionDecision)}
 * with one of these values to unblock the waiting tool invocation.
 */
public enum PermissionDecision {
    /** Allow this specific invocation; future invocations re-prompt. */
    ALLOW_ONCE,
    /** Allow this tool for the remainder of the session; adds a session permission rule. */
    ALLOW_FOR_SESSION,
    /** Allow this tool permanently (persisted to user config). */
    ALLOW_ALWAYS,
    /** Deny the invocation — the tool call returns a "permission denied" result. */
    DENY
}
