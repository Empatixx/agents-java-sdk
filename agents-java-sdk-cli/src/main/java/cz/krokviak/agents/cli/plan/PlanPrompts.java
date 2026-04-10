package cz.krokviak.agents.cli.plan;

public final class PlanPrompts {
    private PlanPrompts() {}

    public static String planModeInstructions(String planFilePath) {
        return """
            PLAN MODE IS ACTIVE.

            You MUST follow this workflow:

            STEP 1: EXPLORE using agents
            Spawn 2-3 agents IN PARALLEL using the agent tool with run_in_background=true.
            Each agent should explore a different aspect:
            - Agent 1: Read the main files related to the task (use read_file, glob)
            - Agent 2: Search for relevant patterns, existing code to reuse (use grep, list_directory)
            - Agent 3: Check tests, configs, dependencies if relevant

            Example:
            Call agent tool 3 times in ONE response with run_in_background=true:
            {"prompt": "Read and summarize the main source files in src/", "description": "explore source", "run_in_background": true}
            {"prompt": "Search for patterns and utilities to reuse", "description": "find patterns", "run_in_background": true}
            {"prompt": "Check tests and build config", "description": "check tests", "run_in_background": true}

            Wait for agents to complete, then proceed to step 2.

            STEP 2: WRITE PLAN
            Based on agent findings, write your plan to the plan file:
            %s

            Use write_file tool to write the plan. Include:
            - Context: what and why
            - Files to modify with specific changes
            - Verification steps

            STEP 3: CALL exit_plan_mode
            After writing the plan file, call exit_plan_mode tool.
            User will approve or give feedback.

            RULES:
            - Do NOT make code changes.
            - Do NOT skip agent exploration.
            - Do NOT just respond with text — use agents, write file, call exit_plan_mode.
            """.formatted(planFilePath);
    }
}
