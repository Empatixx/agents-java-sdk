package cz.krokviak.agents.agent.summary;

import cz.krokviak.agents.agent.AgentContext;
import cz.krokviak.agents.model.LlmContext;
import cz.krokviak.agents.model.Model;
import cz.krokviak.agents.model.ModelResponse;
import cz.krokviak.agents.model.ModelSettings;
import cz.krokviak.agents.runner.InputItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * One-shot, non-streaming LLM call producing short natural-language summaries
 * (tool-batch labels, periodic sub-agent progress lines).
 *
 * <p>Uses {@link AgentContext#summaryModelOrMain()} so whoever wires the
 * agent can plug in a cheaper model without engine code knowing which. All
 * failures are swallowed into {@link Optional#empty()} — summaries are a
 * nice-to-have, never a hard dependency.
 */
public final class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    private final AgentContext ctx;

    public SummaryService(AgentContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Execute a single summarize call. Returns {@code Optional.empty()} on
     * any failure (no model, provider error, empty result).
     *
     * @param systemPrompt short instruction for the summary model
     * @param userText user content to summarise
     * @param maxTokens hard cap on response size (keep small — 80-200 typical)
     */
    public Optional<String> summarize(String systemPrompt, String userText, int maxTokens) {
        Model m = ctx.summaryModelOrMain();
        if (m == null) return Optional.empty();
        try {
            ModelSettings settings = ModelSettings.builder().maxTokens(maxTokens).build();
            LlmContext llm = new LlmContext(
                systemPrompt,
                List.of(new InputItem.UserMessage(userText)),
                List.of(),
                null,
                settings);
            ModelResponse resp = m.call(llm, settings);
            String text = extractText(resp);
            if (text == null || text.isBlank()) return Optional.empty();
            return Optional.of(text.strip());
        } catch (Exception e) {
            log.debug("Summary call failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String extractText(ModelResponse resp) {
        if (resp == null || resp.output() == null) return null;
        StringBuilder sb = new StringBuilder();
        for (var item : resp.output()) {
            if (item instanceof ModelResponse.OutputItem.Message msg && msg.content() != null) {
                sb.append(msg.content());
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
