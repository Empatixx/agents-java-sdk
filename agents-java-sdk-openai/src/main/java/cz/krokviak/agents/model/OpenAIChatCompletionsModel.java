package cz.krokviak.agents.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cz.krokviak.agents.http.AgentHttpClient;
import cz.krokviak.agents.http.SseParser;
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
        ObjectNode body = buildRequestBody(context, settings, false);

        try {
            JsonNode response = httpClient.post("/chat/completions", body, JsonNode.class);
            return parseResponse(response);
        } catch (Exception e) {
            throw new RuntimeException("OpenAI Chat Completions API call failed", e);
        }
    }

    @Override
    public ModelResponseStream callStreamed(LlmContext context, ModelSettings settings) {
        ObjectNode body = buildRequestBody(context, settings, true);
        InputStream stream = httpClient.postStream("/chat/completions", body);
        Iterator<SseParser.SseEvent> sseIterator = SseParser.stream(stream);

        return new ChatCompletionsResponseStream(sseIterator, mapper);
    }

    private ObjectNode buildRequestBody(LlmContext context, ModelSettings settings, boolean stream) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", modelId);

        ArrayNode messages = mapper.createArrayNode();

        // System message
        if (context.systemPrompt() != null && !context.systemPrompt().isEmpty()) {
            ObjectNode sysMsg = mapper.createObjectNode();
            sysMsg.put("role", "system");
            sysMsg.put("content", context.systemPrompt());
            messages.add(sysMsg);
        }

        // Conversation messages
        for (InputItem item : context.messages()) {
            switch (item) {
                case InputItem.UserMessage msg -> {
                    ObjectNode m = mapper.createObjectNode();
                    m.put("role", "user");
                    m.put("content", msg.content());
                    messages.add(m);
                }
                case InputItem.AssistantMessage msg -> {
                    ObjectNode m = mapper.createObjectNode();
                    m.put("role", "assistant");
                    m.put("content", msg.content());
                    if (!msg.toolCalls().isEmpty()) {
                        ArrayNode tc = mapper.createArrayNode();
                        for (InputItem.ToolCall call : msg.toolCalls()) {
                            ObjectNode tcNode = mapper.createObjectNode();
                            tcNode.put("id", call.id());
                            tcNode.put("type", "function");
                            ObjectNode fn = mapper.createObjectNode();
                            fn.put("name", call.name());
                            try {
                                fn.put("arguments", mapper.writeValueAsString(call.arguments()));
                            } catch (Exception e) {
                                fn.put("arguments", "{}");
                            }
                            tcNode.set("function", fn);
                            tc.add(tcNode);
                        }
                        m.set("tool_calls", tc);
                    }
                    messages.add(m);
                }
                case InputItem.ToolResult result -> {
                    ObjectNode m = mapper.createObjectNode();
                    m.put("role", "tool");
                    m.put("tool_call_id", result.toolCallId());
                    m.put("content", result.output());
                    messages.add(m);
                }
                case InputItem.SystemMessage msg -> {
                    ObjectNode m = mapper.createObjectNode();
                    m.put("role", "system");
                    m.put("content", msg.content());
                    messages.add(m);
                }
            }
        }
        body.set("messages", messages);

        // Tools
        if (!context.tools().isEmpty()) {
            ArrayNode tools = mapper.createArrayNode();
            for (ToolDefinition toolDef : context.tools()) {
                ObjectNode toolNode = mapper.createObjectNode();
                toolNode.put("type", "function");
                ObjectNode fn = mapper.createObjectNode();
                fn.put("name", toolDef.name());
                fn.put("description", toolDef.description());
                fn.set("parameters", mapper.valueToTree(toolDef.parametersSchema()));
                toolNode.set("function", fn);
                tools.add(toolNode);
            }
            body.set("tools", tools);
        }

        if (settings != null) {
            if (settings.temperature() != null) body.put("temperature", settings.temperature());
            if (settings.topP() != null) body.put("top_p", settings.topP());
            if (settings.maxTokens() != null) body.put("max_tokens", settings.maxTokens());
        }

        if (stream) body.put("stream", true);

        return body;
    }

    private ModelResponse parseResponse(JsonNode response) {
        String id = response.path("id").asText();
        List<ModelResponse.OutputItem> outputs = new ArrayList<>();
        int inputTokens = response.path("usage").path("prompt_tokens").asInt(0);
        int outputTokens = response.path("usage").path("completion_tokens").asInt(0);

        JsonNode choices = response.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode choice = choices.get(0);
            JsonNode message = choice.path("message");
            String content = message.path("content").asText(null);

            if (content != null && !content.isEmpty()) {
                outputs.add(new ModelResponse.OutputItem.Message(content));
            }

            JsonNode toolCalls = message.path("tool_calls");
            if (toolCalls.isArray()) {
                for (JsonNode tc : toolCalls) {
                    String callId = tc.path("id").asText();
                    String name = tc.path("function").path("name").asText();
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> args = mapper.readValue(
                            tc.path("function").path("arguments").asText(), Map.class);
                        outputs.add(new ModelResponse.OutputItem.ToolCallRequest(callId, name, args));
                    } catch (Exception e) {
                        outputs.add(new ModelResponse.OutputItem.ToolCallRequest(callId, name, Map.of()));
                    }
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
                        JsonNode node = mapper.readTree(sse.data());
                        JsonNode delta = node.path("choices").path(0).path("delta");
                        String content = delta.path("content").asText(null);
                        if (content != null) return new Event.TextDelta(content);
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
