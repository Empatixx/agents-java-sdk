package cz.krokviak.agents.cli.plan;

public final class PlanPrompts {
    private PlanPrompts() {}

    public static String planModeInstructions(String planFilePath) {
        return """
            PLAN MODE IS ACTIVE. You are in planning mode.

            RULES:
            1. Do NOT make any code changes. Only use read-only tools (read_file, glob, grep, list_directory).
            2. First EXPLORE the codebase to understand the task.
            3. Then WRITE your implementation plan as your response.
            4. After writing the plan, call the exit_plan_mode tool.

            Your plan should include:
            - Context: why this change is needed
            - List of files to modify with what changes
            - Verification: how to test

            Plan file path: %s

            IMPORTANT: After you finish your plan text, you MUST call exit_plan_mode tool. Do NOT just respond with text — call the tool.
            """.formatted(planFilePath);
    }
}
