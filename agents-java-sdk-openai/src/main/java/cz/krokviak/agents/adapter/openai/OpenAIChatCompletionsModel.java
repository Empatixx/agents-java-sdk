package cz.krokviak.agents.adapter.openai;
import cz.krokviak.agents.model.Usage;
import cz.krokviak.agents.model.LlmContext;
import cz.krokviak.agents.model.ModelSettings;
import cz.krokviak.agents.model.ModelResponseStream;
import cz.krokviak.agents.model.ModelResponse;
import cz.krokviak.agents.model.Model;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.krokviak.agents.http.AgentHttpClient;
import cz.krokviak.agents.http.SseParser;
import cz.krokviak.agents.adapter.openai.dto.ChatCompletionsDto;
import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.tool.ToolDefinition;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
                case InputItem.CompactionMarker marker ->
                    messages.add(new ChatCompletionsDto.Message("system", "[Conversation Summary]\n" + marker.summary(), null, null));
                case InputItem.ImageContent _ -> {} // images not forwarded in this context
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
            stream ? Boolean.TRUE : null,
            stream ? new ChatCompletionsDto.StreamOptions(true) : null
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

        // Accumulate tool call state across deltas
        private final Map<Integer, String> toolCallIds = new HashMap<>();
        private final Map<Integer, String> toolCallNames = new HashMap<>();
        private final Map<Integer, StringBuilder> toolCallArgs = new HashMap<>();
        private String responseId;
        private Usage usage;

        ChatCompletionsResponseStream(Iterator<SseParser.SseEvent> sseIterator, ObjectMapper mapper) {
            this.sseIterator = sseIterator;
            this.mapper = mapper;
        }

        @Override
        public Iterator<Event> iterator() {
            return new Iterator<>() {
                private final Queue<Event> buffer = new ArrayDeque<>();

                @Override
                public boolean hasNext() {
                    return !buffer.isEmpty() || sseIterator.hasNext();
                }

                @Override
                public Event next() {
                    if (!buffer.isEmpty()) return buffer.poll();

                    while (sseIterator.hasNext()) {
                        SseParser.SseEvent sse = sseIterator.next();
                        if (sse.isDone()) {
                            // Build final response with accumulated tool calls
                            List<ModelResponse.OutputItem> outputs = new ArrayList<>();
                            for (var entry : toolCallIds.entrySet()) {
                                int idx = entry.getKey();
                                Map<String, Object> args;
                                try {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> parsed = mapper.readValue(
                                        toolCallArgs.getOrDefault(idx, new StringBuilder("{}")).toString(), Map.class);
                                    args = parsed;
                                } catch (Exception e) {
                                    args = Map.of();
                                }
                                outputs.add(new ModelResponse.OutputItem.ToolCallRequest(
                                    entry.getValue(),
                                    toolCallNames.getOrDefault(idx, ""),
                                    args));
                            }
                            return new Event.Done(new ModelResponse(
                                responseId, outputs,
                                usage != null ? usage : Usage.zero()));
                        }

                        try {
                            ChatCompletionsDto.StreamChunk chunk = mapper.readValue(
                                sse.data(), ChatCompletionsDto.StreamChunk.class);

                            if (chunk.id() != null) responseId = chunk.id();
                            if (chunk.usage() != null) {
                                usage = new Usage(chunk.usage().promptTokens(), chunk.usage().completionTokens());
                            }

                            if (chunk.choices() == null || chunk.choices().isEmpty()) continue;
                            var choice = chunk.choices().get(0);
                            ChatCompletionsDto.StreamDelta delta = choice.delta();

                            if (delta == null) {
                                if ("stop".equals(choice.finishReason())) continue;
                                continue;
                            }

                            // Text content
                            if (delta.content() != null && !delta.content().isEmpty()) {
                                return new Event.TextDelta(delta.content());
                            }

                            // Tool call deltas
                            if (delta.toolCalls() != null) {
                                for (var tc : delta.toolCalls()) {
                                    int idx = tc.index();
                                    if (tc.id() != null) {
                                        toolCallIds.put(idx, tc.id());
                                    }
                                    if (tc.function() != null) {
                                        if (tc.function().name() != null) {
                                            toolCallNames.put(idx, tc.function().name());
                                        }
                                        if (tc.function().arguments() != null) {
                                            toolCallArgs.computeIfAbsent(idx, k -> new StringBuilder())
                                                .append(tc.function().arguments());
                                            // Emit delta for UI feedback
                                            buffer.add(new Event.ToolCallDelta(
                                                toolCallIds.getOrDefault(idx, ""),
                                                toolCallNames.getOrDefault(idx, ""),
                                                tc.function().arguments()));
                                        }
                                    }
                                }
                                if (!buffer.isEmpty()) return buffer.poll();
                            }
                        } catch (Exception e) {
                            // Skip unparseable chunks
                        }
                    }
                    // Stream ended without [DONE]
                    return new Event.Done(new ModelResponse(responseId, List.of(), usage != null ? usage : Usage.zero()));
                }
            };
        }

        @Override
        public void close() {}
    }
}
