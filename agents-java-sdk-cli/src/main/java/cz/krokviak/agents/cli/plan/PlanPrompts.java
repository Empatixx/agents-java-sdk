package cz.krokviak.agents.cli.plan;

public final class PlanPrompts {
    private PlanPrompts() {}

    public static String planModeInstructions(String planFilePath) {
        return """
            PLAN MODE IS ACTIVE.

            You MUST follow this workflow:

            STEP 1: EXPLORE — Use read_file, glob, grep, list_directory to understand the codebase.
            Read relevant files, search for patterns, understand the architecture.

            STEP 2: WRITE PLAN — Write your plan to the plan file using write_file:
            Plan file: %s
            The plan must include:
            - What needs to change and why
            - Which files to modify
            - How to verify

            STEP 3: CALL exit_plan_mode — You MUST call the exit_plan_mode tool when done.
            This shows the plan to the user for approval.

            RULES:
            - Do NOT make code changes (only write to the plan file).
            - Do NOT skip exploration — read the actual code first.
            - Do NOT just respond with text — write the plan to the file and call exit_plan_mode.
            - The user will approve your plan or give feedback to refine it.
            """.formatted(planFilePath);
    }
}
