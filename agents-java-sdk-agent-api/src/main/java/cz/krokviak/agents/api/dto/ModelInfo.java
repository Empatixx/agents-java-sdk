package cz.krokviak.agents.api.dto;

/**
 * Identity of the model powering the current session. Returned by
 * {@link cz.krokviak.agents.api.AgentService#currentModel()}.
 *
 * @param id       model identifier (e.g. {@code "claude-sonnet-4-20250514"}, {@code "gpt-5.4"})
 * @param provider provider name ({@code "anthropic"}, {@code "openai"}), may be {@code null}
 * @param baseUrl  API base URL, useful for self-hosted deployments; may be {@code null}
 */
public record ModelInfo(
    String id,
    String provider,
    String baseUrl
) {}
