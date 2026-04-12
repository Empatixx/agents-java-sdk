package cz.krokviak.agents.adapter.anthropic;
import cz.krokviak.agents.model.Usage;
import cz.krokviak.agents.model.LlmContext;
import cz.krokviak.agents.model.ModelSettings;
import cz.krokviak.agents.model.ModelResponseStream;
import cz.krokviak.agents.model.ModelResponse;
import cz.krokviak.agents.model.Model;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.krokviak.agents.exception.ContextTooLongException;
import cz.krokviak.agents.http.SseParser;
import cz.krokviak.agents.adapter.anthropic.dto.AnthropicDto;
import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.tool.ToolDefinition;

import java.io.InputStream;
import java.util.*;

public class AnthropicModel implements Model {
    private final AnthropicHttpClient httpClient;
    private final ObjectMapper mapper;
    private final String modelId;

    public AnthropicModel(String apiKey) {
        this(apiKey, "https://api.anthropic.com", "claude-sonnet-4-20250514");
    }

    public AnthropicModel(String apiKey, String baseUrl, String modelId) {
        this.httpClient = new AnthropicHttpClient(baseUrl, apiKey);
        this.mapper = httpClient.objectMapper();
        this.modelId = modelId;
    }

    @Override
    public ModelResponse call(LlmContext context, ModelSettings settings) {
        AnthropicDto.Request body = buildRequestBody(context, settings, false);

        try {
            AnthropicDto.Response response = httpClient.post(
                "/v1/messages", body, AnthropicDto.Response.class);
            return parseResponse(response);
        } catch (Exception e) {
            if (isPromptTooLong(e)) {
                throw new ContextTooLongException("Prompt is too long for the model context window", e);
            }
            throw new RuntimeException("Anthropic Messages API call failed", e);
        }
    }

    @Override
    public ModelResponseStream callStreamed(LlmContext context, ModelSettings settings) {
        AnthropicDto.Request body = buildRequestBody(context, settings, true);
        InputStream stream = httpClient.postStream("/v1/messages", body);
        Iterator<SseParser.SseEvent> sseIterator = SseParser.stream(stream);

        return new AnthropicResponseStream(sseIterator, mapper);
    }

    private AnthropicDto.Request buildRequestBody(LlmContext context, ModelSettings settings, boolean stream) {
        StringBuilder systemPrompt = new StringBuilder();
        if (context.systemPrompt() != null && !context.systemPrompt().isEmpty()) {
            systemPrompt.append(context.systemPrompt());
        }

        List<AnthropicDto.Message> messages = buildMessages(context.messages(), systemPrompt);

        List<AnthropicDto.Tool> tools = context.tools().isEmpty() ? null :
            context.tools().stream().map(this::mapTool).toList();

        int maxTokens = (settings != null && settings.maxTokens() != null)
            ? settings.maxTokens() : 4096;

        String system = systemPrompt.isEmpty() ? null : systemPrompt.toString();

        AnthropicDto.Thinking thinking = null;
        if (settings != null && settings.thinking() != null && settings.thinking().enabled()) {
            thinking = AnthropicDto.Thinking.enabled(settings.thinking().budgetTokens());
        }

        return new AnthropicDto.Request(
            modelId,
            maxTokens,
            system,
            messages,
            stream ? Boolean.TRUE : null,
            tools,
            settings != null ? settings.temperature() : null,
            settings != null ? settings.topP() : null,
            thinking
        );
    }

    private List<AnthropicDto.Message> buildMessages(List<InputItem> items, StringBuilder systemPrompt) {
        List<AnthropicDto.Message> raw = new ArrayList<>();

        for (InputItem item : items) {
            switch (item) {
                case InputItem.CompactionMarker marker -> {
                    if (!systemPrompt.isEmpty()) systemPrompt.append("\n");
                    systemPrompt.append("[Conversation Summary]\n").append(marker.summary());
                }
                case InputItem.SystemMessage msg -> {
                    if (!systemPrompt.isEmpty()) systemPrompt.append("\n");
                    systemPrompt.append(msg.content());
                }
                case InputItem.UserMessage msg ->
                    raw.add(new AnthropicDto.Message("user",
                        List.of(new AnthropicDto.ContentBlock.TextBlock(msg.content()))));
                case InputItem.AssistantMessage msg -> {
                    List<AnthropicDto.ContentBlock> content = new ArrayList<>();
                    if (msg.content() != null && !msg.content().isEmpty()) {
                        content.add(new AnthropicDto.ContentBlock.TextBlock(msg.content()));
                    }
                    for (InputItem.ToolCall tc : msg.toolCalls()) {
                        content.add(new AnthropicDto.ContentBlock.ToolUseBlock(tc.id(), tc.name(), tc.arguments()));
                    }
                    raw.add(new AnthropicDto.Message("assistant", content));
                }
                case InputItem.ToolResult result ->
                    raw.add(new AnthropicDto.Message("user",
                        List.of(new AnthropicDto.ContentBlock.ToolResultBlock(result.toolCallId(), result.output()))));
                case InputItem.ImageContent img -> {
                    List<AnthropicDto.ContentBlock> content = new ArrayList<>();
                    content.add(new AnthropicDto.ContentBlock.ImageBlock(
                        AnthropicDto.ContentBlock.ImageSource.base64(img.mediaType(), img.base64Data())));
                    if (img.description() != null && !img.description().isBlank()) {
                        content.add(new AnthropicDto.ContentBlock.TextBlock(img.description()));
                    }
                    raw.add(new AnthropicDto.Message("user", content));
                }
            }
        }

        // Merge adjacent same-role messages
        List<AnthropicDto.Message> merged = new ArrayList<>();
        for (AnthropicDto.Message msg : raw) {
            if (!merged.isEmpty() && merged.getLast().role().equals(msg.role())) {
                AnthropicDto.Message last = merged.removeLast();
                List<AnthropicDto.ContentBlock> combined = new ArrayList<>(last.content());
                combined.addAll(msg.content());
                merged.add(new AnthropicDto.Message(last.role(), combined));
            } else {
                merged.add(msg);
            }
        }

        return merged;
    }

    private AnthropicDto.Tool mapTool(ToolDefinition td) {
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) td.parametersSchema().getOrDefault("properties", Map.of());
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) td.parametersSchema().get("required");

        return new AnthropicDto.Tool(
            td.name(),
            td.description(),
            new AnthropicDto.InputSchema("object", properties, required)
        );
    }

    private ModelResponse parseResponse(AnthropicDto.Response response) {
        String id = response.id();
        int inputTokens = response.usage() != null ? response.usage().inputTokens() : 0;
        int outputTokens = response.usage() != null ? response.usage().outputTokens() : 0;

        List<ModelResponse.OutputItem> outputs = new ArrayList<>();
        if (response.content() != null) {
            for (AnthropicDto.ContentBlock block : response.content()) {
                switch (block) {
                    case AnthropicDto.ContentBlock.TextBlock tb ->
                        outputs.add(new ModelResponse.OutputItem.Message(tb.text()));
                    case AnthropicDto.ContentBlock.ToolUseBlock tub ->
                        outputs.add(new ModelResponse.OutputItem.ToolCallRequest(tub.id(), tub.name(), tub.input()));
                    case AnthropicDto.ContentBlock.ToolResultBlock _ -> {}
                    case AnthropicDto.ContentBlock.ImageBlock _ -> {} // not expected in responses
                }
            }
        }

        return new ModelResponse(id, outputs, new Usage(inputTokens, outputTokens));
    }

    private static boolean isPromptTooLong(Throwable e) {
        String msg = e.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase();
            if (lower.contains("prompt is too long") || lower.contains("prompt_too_long")
                || lower.contains("context length exceeded") || lower.contains("max tokens")) {
                return true;
            }
        }
        return e.getCause() != null && isPromptTooLong(e.getCause());
    }
}
