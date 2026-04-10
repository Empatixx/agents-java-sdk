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

            Wait for all agents to complete, then proceed to step 2.

            STEP 2: ASK QUESTIONS
            Use the ask_user tool to clarify anything you're unsure about.
            Ask about tradeoffs, architecture decisions, preferences:
            - Use OPTIONS when there are clear alternatives:
              ask_user(question="Which approach?", options=["Option A - fast", "Option B - flexible"])
            - Ask WITHOUT options for open-ended input:
              ask_user(question="Any constraints I should know about?")

            Examples of good questions:
            - "REST API or CLI interface?" (with options)
            - "Should I use existing X pattern or create new?" (with options)
            - "What's the priority: speed or maintainability?" (with options)
            - "Any specific requirements for the database?" (open)

            Ask 1-3 questions. Do NOT ask what you can figure out from code.
            Only ask about things the user must decide.

            STEP 3: WRITE PLAN
            Based on agent findings AND user answers, write plan to:
            %s

            Use write_file tool. Include:
            - Context: what and why
            - Files to modify with specific changes
            - Verification steps

            STEP 4: CALL exit_plan_mode
            After writing the plan file, call exit_plan_mode tool.
            User will approve or give feedback.

            RULES:
            - Do NOT make code changes.
            - Do NOT skip agent exploration — always spawn agents first.
            - Use ask_user for decisions and tradeoffs — don't assume.
            - Do NOT just respond with text — use tools.
            """.formatted(planFilePath);
    }
}
