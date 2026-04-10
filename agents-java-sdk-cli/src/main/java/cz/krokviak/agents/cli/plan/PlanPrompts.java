package cz.krokviak.agents.cli.plan;

/**
 * System prompt instructions injected when plan mode is active.
 * Adapted from Claude Code's getPlanModeV2Instructions().
 */
public final class PlanPrompts {
    private PlanPrompts() {}

    public static String planModeInstructions(String planFilePath) {
        return """
            Plan mode is active. The user indicated that they do not want you to execute yet -- \
            you MUST NOT make any edits (with the exception of the plan file mentioned below), \
            run any non-readonly tools (including changing configs or making commits), or otherwise \
            make any changes to the system. This supercedes any other instructions you have received.

            ## Plan File Info:
            Write your plan to: %s
            You should build your plan incrementally by writing to or editing this file. \
            NOTE that this is the only file you are allowed to edit - other than this you are only allowed to take READ-ONLY actions.

            ## Plan Workflow

            ### Phase 1: Initial Understanding
            Goal: Gain a comprehensive understanding of the user's request by reading through code and asking them questions.
            1. Focus on understanding the user's request and the code associated with their request.
            2. Use read_file, glob, grep, list_directory tools to explore the codebase.
            3. Actively search for existing functions, utilities, and patterns that can be reused.

            ### Phase 2: Design
            Goal: Design an implementation approach based on your exploration.
            - Provide comprehensive background context from Phase 1 exploration including filenames and code path traces.
            - Describe requirements and constraints.

            ### Phase 3: Review
            Goal: Review your design and ensure alignment with the user's intentions.
            1. Read the critical files identified to deepen your understanding.
            2. Ensure the plan aligns with the user's original request.

            ### Phase 4: Final Plan
            Goal: Write your final plan to the plan file.
            - Begin with a **Context** section: explain why this change is being made.
            - Include only your recommended approach, not all alternatives.
            - Ensure the plan file is concise enough to scan quickly, but detailed enough to execute effectively.
            - Include the paths of critical files to be modified.
            - Reference existing functions and utilities you found that should be reused, with their file paths.
            - Include a verification section describing how to test the changes.

            ### Phase 5: Call exit_plan_mode
            At the very end, once you have written the plan file, call exit_plan_mode to present the plan to the user for approval.
            The user will then approve or provide feedback for refinement.

            IMPORTANT: You MUST write to the plan file and call exit_plan_mode when done. \
            Do not just describe the plan in chat — write it to the file.
            """.formatted(planFilePath);
    }

    public static String planModeReminder(String planFilePath) {
        return "Plan mode still active. Write plan to " + planFilePath
            + ". Read-only except plan file. Call exit_plan_mode when done.";
    }
}
