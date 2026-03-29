package cz.krokviak.agents.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.krokviak.agents.http.AgentHttpClient;
import cz.krokviak.agents.http.SseParser;
import cz.krokviak.agents.model.dto.ChatCompletionsDto;
import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.tool.ToolDefinition;

import java.io.InputStream;
import java.util.*;

public class OpenAIChatCompletionsModel implements Model {
    private final AgentHttpClient httpClient;
    private final ObjectMapper mapper;
    private final String modelId;

    public OpenAIChatCompletionsModel(String apiKey) {
        this(apiKey, "https://api.openai.com/v1", "gpt-4.1");
    }

    public OpenAIChatCompletionsModel(String apiKey, String baseUrl, String modelId) {
        this.httpClient = new AgentHttpClient(baseUrl, apiKey);
        this.mapper = httpClient.objectMapper();
        this.modelId = modelId;
    }

    @Override
    public ModelResponse call(LlmContext context, ModelSettings settings) {
        ChatCompletionsDto.Request body = buildRequestBody(context, settings, false);

        try {
            ChatCompletionsDto.Response response = httpClient.post(
                "/chat/completions", body, ChatCompletionsDto.Response.class);
            return parseResponse(response);
        } catch (Exception e) {
            throw new RuntimeException("OpenAI Chat Completions API call failed", e);
        }
    }

    @Override
    public ModelResponseStream callStreamed(LlmContext context, ModelSettings settings) {
        ChatCompletionsDto.Request body = buildRequestBody(context, settings, true);
        InputStream stream = httpClient.postStream("/chat/completions", body);
        Iterator<SseParser.SseEvent> sseIterator = SseParser.stream(stream);

        return new ChatCompletionsResponseStream(sseIterator, mapper);
    }

    private ChatCompletionsDto.Request buildRequestBody(LlmContext context, ModelSettings settings, boolean stream) {
        List<ChatCompletionsDto.Message> messages = new ArrayList<>();

        if (context.systemPrompt() != null && !context.systemPrompt().isEmpty()) {
            messages.add(new ChatCompletionsDto.Message("system", context.systemPrompt(), null, null));
        }

        for (InputItem item : context.messages()) {
            switch (item) {
                case InputItem.UserMessage msg ->
                    messages.add(new ChatCompletionsDto.Message("user", msg.content(), null, null));
                case InputItem.AssistantMessage msg -> {
                    List<ChatCompletionsDto.ToolCallOut> toolCalls = msg.toolCalls().isEmpty() ? null :
                        msg.toolCalls().stream().map(tc -> new ChatCompletionsDto.ToolCallOut(
                            tc.id(), "function",
                            new ChatCompletionsDto.Function(tc.name(), serializeArgs(tc.arguments()))
                        )).toList();
                    messages.add(new ChatCompletionsDto.Message("assistant", msg.content(), null, toolCalls));
                }
                case InputItem.ToolResult result ->
                    messages.add(new ChatCompletionsDto.Message("tool", result.output(), result.toolCallId(), null));
                case InputItem.SystemMessage msg ->
                    messages.add(new ChatCompletionsDto.Message("system", msg.content(), null, null));
            }
        }

        List<ChatCompletionsDto.Tool> tools = context.tools().isEmpty() ? null :
            context.tools().stream().map(td -> new ChatCompletionsDto.Tool(
                "function",
                new ChatCompletionsDto.Tool.Function(td.name(), td.description(), td.parametersSchema())
            )).toList();

        return new ChatCompletionsDto.Request(
            modelId, messages, tools,
            settings != null ? settings.temperature() : null,
            settings != null ? settings.topP() : null,
            settings != null ? settings.maxTokens() : null,
            stream ? Boolean.TRUE : null
        );
    }

    private String serializeArgs(Map<String, Object> arguments) {
        try {
            return mapper.writeValueAsString(arguments);
        } catch (Exception e) {
            return "{}";
        }
    }

    private ModelResponse parseResponse(ChatCompletionsDto.Response response) {
        String id = response.id();
        int inputTokens = response.usage() != null ? response.usage().promptTokens() : 0;
        int outputTokens = response.usage() != null ? response.usage().completionTokens() : 0;

        List<ModelResponse.OutputItem> outputs = new ArrayList<>();
        if (response.choices() != null && !response.choices().isEmpty()) {
            ChatCompletionsDto.ResponseMessage message = response.choices().get(0).message();
            if (message.content() != null && !message.content().isEmpty()) {
                outputs.add(new ModelResponse.OutputItem.Message(message.content()));
            }
            if (message.toolCalls() != null) {
                for (ChatCompletionsDto.ToolCallIn tc : message.toolCalls()) {
                    Map<String, Object> args;
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> parsed = mapper.readValue(tc.function().arguments(), Map.class);
                        args = parsed;
                    } catch (Exception e) {
                        args = Map.of();
                    }
                    outputs.add(new ModelResponse.OutputItem.ToolCallRequest(tc.id(), tc.function().name(), args));
                }
            }
        }

        return new ModelResponse(id, outputs, new Usage(inputTokens, outputTokens));
    }

    private static class ChatCompletionsResponseStream implements ModelResponseStream {
        private final Iterator<SseParser.SseEvent> sseIterator;
        private final ObjectMapper mapper;

        ChatCompletionsResponseStream(Iterator<SseParser.SseEvent> sseIterator, ObjectMapper mapper) {
            this.sseIterator = sseIterator;
            this.mapper = mapper;
        }

        @Override
        public Iterator<Event> iterator() {
            return new Iterator<>() {
                @Override public boolean hasNext() { return sseIterator.hasNext(); }
                @Override
                public Event next() {
                    SseParser.SseEvent sse = sseIterator.next();
                    if (sse.isDone()) return new Event.Done(null);
                    try {
                        ChatCompletionsDto.StreamChunk chunk = mapper.readValue(
                            sse.data(), ChatCompletionsDto.StreamChunk.class);
                        if (chunk.choices() != null && !chunk.choices().isEmpty()) {
                            ChatCompletionsDto.StreamDelta delta = chunk.choices().get(0).delta();
                            if (delta != null && delta.content() != null) {
                                return new Event.TextDelta(delta.content());
                            }
                        }
                        return new Event.TextDelta("");
                    } catch (Exception e) {
                        return new Event.TextDelta("");
                    }
                }
            };
        }

        @Override public void close() {}
    }
}
