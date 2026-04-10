package cz.krokviak.agents.cli.tool;

import java.util.Set;

public final class ToolClassifier {
    private ToolClassifier() {}

    private static final Set<String> READ_ONLY = Set.of(
        "read_file", "glob", "grep", "list_directory", "tool_search",
        "web_fetch", "web_search", "task_get", "task_list", "skill",
        "agent",  // agents run in their own context, safe for parallel
        "ask_user"  // user interaction, safe
    );

    public static boolean isReadOnly(String toolName) {
        return READ_ONLY.contains(toolName);
    }
}
