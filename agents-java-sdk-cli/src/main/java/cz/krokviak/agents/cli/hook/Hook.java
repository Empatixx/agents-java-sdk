package cz.krokviak.agents.cli.hook;

public interface Hook {
    enum Phase { PRE_TOOL, POST_TOOL, PRE_MODEL, POST_MODEL }
    Phase phase();
    HookResult execute(ToolUseEvent event);
}
