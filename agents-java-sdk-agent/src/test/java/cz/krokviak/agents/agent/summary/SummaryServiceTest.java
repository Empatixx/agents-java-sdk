package cz.krokviak.agents.agent.summary;

import cz.krokviak.agents.agent.AgentContext;
import cz.krokviak.agents.model.LlmContext;
import cz.krokviak.agents.model.Model;
import cz.krokviak.agents.model.ModelResponse;
import cz.krokviak.agents.model.ModelResponseStream;
import cz.krokviak.agents.model.ModelSettings;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SummaryServiceTest {

    private AgentContext newCtx(Model summaryModel) {
        var ctx = new AgentContext(null, "test", null, null, null, null,
            Path.of("/tmp"), "base", null, null,
            new cz.krokviak.agents.agent.task.TaskManager(),
            new cz.krokviak.agents.agent.mailbox.MailboxManager());
        ctx.setSummaryModel(summaryModel);
        return ctx;
    }

    @Test
    void returnsEmptyWhenNoModelAvailable() {
        var ctx = newCtx(null);
        var svc = new SummaryService(ctx);
        assertTrue(svc.summarize("sys", "user", 64).isEmpty());
    }

    @Test
    void returnsAssistantTextFromModel() {
        AtomicReference<LlmContext> captured = new AtomicReference<>();
        Model fake = new Model() {
            @Override public ModelResponse call(LlmContext ctx, ModelSettings s) {
                captured.set(ctx);
                return new ModelResponse("id-1",
                    List.of(new ModelResponse.OutputItem.Message("Reading runAgent.ts")),
                    null);
            }
            @Override public ModelResponseStream callStreamed(LlmContext ctx, ModelSettings s) {
                throw new UnsupportedOperationException();
            }
        };
        var svc = new SummaryService(newCtx(fake));
        var result = svc.summarize("sys prompt", "user text", 64);
        assertTrue(result.isPresent());
        assertEquals("Reading runAgent.ts", result.get());
        assertEquals("sys prompt", captured.get().systemPrompt());
    }

    @Test
    void swallowsExceptionsIntoEmpty() {
        Model boom = new Model() {
            @Override public ModelResponse call(LlmContext ctx, ModelSettings s) {
                throw new RuntimeException("boom");
            }
            @Override public ModelResponseStream callStreamed(LlmContext ctx, ModelSettings s) { return null; }
        };
        var svc = new SummaryService(newCtx(boom));
        assertTrue(svc.summarize("s", "u", 64).isEmpty());
    }

    @Test
    void emptyModelResponseReturnsEmpty() {
        Model blank = new Model() {
            @Override public ModelResponse call(LlmContext ctx, ModelSettings s) {
                return new ModelResponse("id", List.of(), null);
            }
            @Override public ModelResponseStream callStreamed(LlmContext ctx, ModelSettings s) { return null; }
        };
        assertTrue(new SummaryService(newCtx(blank)).summarize("s", "u", 64).isEmpty());
    }

    @Test
    void fallsBackToMainModelWhenSummaryUnset() {
        Model main = new Model() {
            @Override public ModelResponse call(LlmContext ctx, ModelSettings s) {
                return new ModelResponse("id",
                    List.of(new ModelResponse.OutputItem.Message("from main")), null);
            }
            @Override public ModelResponseStream callStreamed(LlmContext ctx, ModelSettings s) { return null; }
        };
        var ctx = new AgentContext(main, "test", null, null, null, null,
            Path.of("/tmp"), "base", null, null,
            new cz.krokviak.agents.agent.task.TaskManager(),
            new cz.krokviak.agents.agent.mailbox.MailboxManager());
        var svc = new SummaryService(ctx);
        assertEquals("from main", svc.summarize("s", "u", 64).orElseThrow());
    }
}
