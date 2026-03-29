package cz.krokviak.agents.agent;

import cz.krokviak.agents.context.RunContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromptTest {

    @Test
    void staticPromptReturnsText() {
        Prompt prompt = Prompt.of("Hello, world!");
        RunContext<Void> ctx = new RunContext<>(null);
        Agent<Void> agent = Agent.<Void>builder("test").build();

        String resolved = prompt.resolve(ctx, agent);
        assertEquals("Hello, world!", resolved);
    }

    @Test
    void staticPromptExposesStaticText() {
        Prompt prompt = Prompt.of("static text");
        assertEquals("static text", prompt.staticText());
    }

    @Test
    void dynamicPromptCallsFunction() {
        Prompt prompt = Prompt.dynamic((ctx, agent) -> "Dynamic: " + agent.name());
        RunContext<Void> ctx = new RunContext<>(null);
        Agent<Void> agent = Agent.<Void>builder("MyAgent").build();

        String resolved = prompt.resolve(ctx, agent);
        assertEquals("Dynamic: MyAgent", resolved);
    }

    @Test
    void templatePromptSubstitutesVariables() {
        Prompt prompt = Prompt.template("Hello {{name}}, you are {{role}}!",
            Map.of("name", "Alice", "role", "admin"));
        RunContext<Void> ctx = new RunContext<>(null);
        Agent<Void> agent = Agent.<Void>builder("test").build();

        String resolved = prompt.resolve(ctx, agent);
        assertEquals("Hello Alice, you are admin!", resolved);
    }

    @Test
    void templatePromptWithNoVarsReturnsTemplateAsIs() {
        Prompt prompt = Prompt.template("No substitutions here", Map.of());
        RunContext<Void> ctx = new RunContext<>(null);
        Agent<Void> agent = Agent.<Void>builder("test").build();

        String resolved = prompt.resolve(ctx, agent);
        assertEquals("No substitutions here", resolved);
    }

    @Test
    void emptyStaticPromptReturnsEmptyString() {
        Prompt prompt = Prompt.of(null);
        RunContext<Void> ctx = new RunContext<>(null);
        Agent<Void> agent = Agent.<Void>builder("test").build();

        String resolved = prompt.resolve(ctx, agent);
        assertEquals("", resolved);
    }

    @Test
    void agentWithPromptResolvesViaPrompt() {
        Prompt prompt = Prompt.of("System instructions from Prompt");
        Agent<Void> agent = Agent.<Void>builder("test")
            .instructions("Old instructions")
            .prompt(prompt)
            .build();

        RunContext<Void> ctx = new RunContext<>(null);
        String resolved = agent.resolveInstructions(ctx);
        assertEquals("System instructions from Prompt", resolved);
    }

    @Test
    void agentWithoutPromptFallsBackToInstructions() {
        Agent<Void> agent = Agent.<Void>builder("test")
            .instructions("Fallback instructions")
            .build();

        RunContext<Void> ctx = new RunContext<>(null);
        String resolved = agent.resolveInstructions(ctx);
        assertEquals("Fallback instructions", resolved);
    }
}
