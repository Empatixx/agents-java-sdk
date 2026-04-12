package cz.krokviak.agents.api.dto;

/** Decision returned by a frontend for a pending {@code PermissionRequested} event. */
public enum PermissionDecision {
    ALLOW_ONCE,
    ALLOW_FOR_SESSION,
    ALLOW_ALWAYS,
    DENY
}
