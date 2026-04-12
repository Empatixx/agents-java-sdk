package cz.krokviak.agents.agent.plan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanPromptsTest {

    @Test
    void containsCoordinatorWorkflowDirectives() {
        String prompt = PlanPrompts.planModeInstructions("/tmp/plans/my-plan.md");
        assertTrue(prompt.contains("COORDINATOR"), "plan mode must cast agent as coordinator");
        assertTrue(prompt.contains("task-notification"), "must teach XML result format");
        assertTrue(prompt.contains("parallel"), "must emphasise parallelism");
        assertTrue(prompt.contains("synth") || prompt.contains("Synth"),
            "must teach synthesis discipline");
        assertTrue(prompt.contains("Continue vs spawn") || prompt.contains("continue vs spawn"),
            "must cover continue-vs-spawn decision");
        assertTrue(prompt.contains("task_stop"), "must document task_stop");
        assertTrue(prompt.contains("/tmp/plans/my-plan.md"),
            "must reference the plan file path");
    }

    @Test
    void discouragesTrivialDelegation() {
        String prompt = PlanPrompts.planModeInstructions("x.md");
        assertTrue(prompt.toLowerCase().contains("directly"),
            "must tell agent to answer trivial questions directly");
    }
}
