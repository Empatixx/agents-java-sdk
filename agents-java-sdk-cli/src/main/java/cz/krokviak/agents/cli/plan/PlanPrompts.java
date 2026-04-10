package cz.krokviak.agents.cli.plan;

public final class PlanPrompts {
    private PlanPrompts() {}

    public static String planModeInstructions(String planFilePath) {
        return """
            PLAN MODE IS ACTIVE.

            You MUST follow this workflow:

            STEP 1: EXPLORE using agents
            Spawn 2-3 agents to explore the codebase. Call the agent tool multiple times in ONE response.
            They will run in parallel automatically.
            Each agent should explore a different aspect using bash, read_file, glob, grep, list_directory:
            - Agent 1: Explore project structure (bash: ls, find), read main files
            - Agent 2: Search for relevant patterns and existing code (grep, bash: find)
            - Agent 3: Check tests, configs, dependencies (read_file, bash: cat)

            Example — call agent tool 3 times in one response:
            {"prompt": "Use bash ls -la and list_directory to explore the project structure, then read key files", "description": "explore structure"}
            {"prompt": "Use grep and bash to search for relevant patterns and utilities to reuse", "description": "find patterns"}
            {"prompt": "Use bash and read_file to check tests, build config, and dependencies", "description": "check setup"}

            Wait for all agents to complete, then proceed to step 2.

            STEP 2: WRITE PLAN
            Based on agent findings, write your plan to the plan file using write_file:
            %s

            Include:
            - Context: what and why
            - Files to modify with specific changes
            - Verification steps

            STEP 3: CALL exit_plan_mode
            After writing the plan file, call exit_plan_mode tool.
            User will approve or give feedback.

            RULES:
            - Do NOT make code changes.
            - Do NOT skip agent exploration — always spawn agents first.
            - Agents SHOULD use bash (ls, find, cat) alongside read_file, grep, glob.
            - Do NOT just respond with text — use agents, write file, call exit_plan_mode.
            """.formatted(planFilePath);
    }
}
