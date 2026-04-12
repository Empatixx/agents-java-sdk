package cz.krokviak.agents.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AgentContextSystemPromptSuffixTest {

    private AgentContext newCtx(String base) {
        return new AgentContext(null, "test", null, null, null, null,
            Path.of("/tmp"), base, null, null,
            new cz.krokviak.agents.agent.task.TaskManager(),
            new cz.krokviak.agents.agent.mailbox.MailboxManager());
    }

    @Test
    void defaultEffectivePromptMatchesBase() {
        var ctx = newCtx("You are an agent.");
        assertEquals("You are an agent.", ctx.effectiveSystemPrompt());
    }

    @Test
    void suffixAppendedAfterBlankLine() {
        var ctx = newCtx("base prompt");
        ctx.setSystemPromptSuffix("extra rule");
        assertEquals("base prompt\n\nextra rule", ctx.effectiveSystemPrompt());
    }

    @Test
    void nullOrBlankSuffixIgnored() {
        var ctx = newCtx("base");
        ctx.setSystemPromptSuffix(null);
        assertEquals("base", ctx.effectiveSystemPrompt());
        ctx.setSystemPromptSuffix("   ");
        assertEquals("base", ctx.effectiveSystemPrompt());
    }

    @Test
    void blankBaseReturnsSuffix() {
        var ctx = newCtx("");
        ctx.setSystemPromptSuffix("only suffix");
        assertEquals("only suffix", ctx.effectiveSystemPrompt());
    }
}
