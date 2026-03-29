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

public class OpenAIResponsesModel implements Model {
    private final AgentHttpClient httpClient;
    private final ObjectMapper mapper;
    private final String modelId;

    public OpenAIResponsesModel(String apiKey) {
        this(apiKey, "https://api.openai.com/v1", "gpt-4.1");
    }

    public OpenAIResponsesModel(String apiKey, String baseUrl, String modelId) {
        this.httpClient = new AgentHttpClient(baseUrl, apiKey);
        this.mapper = httpClient.objectMapper();
        this.modelId = modelId;
    }

    @Override
    public ModelResponse call(LlmContext context, ModelSettings settings) {
        ObjectNode body = buildRequestBody(context, settings, false);

        try {
            JsonNode response = httpClient.post("/responses", body, JsonNode.class);
            return parseResponse(response);
        } catch (Exception e) {
            throw new RuntimeException("OpenAI Responses API call failed", e);
        }
    }

    @Override
    public ModelResponseStream callStreamed(LlmContext context, ModelSettings settings) {
        ObjectNode body = buildRequestBody(context, settings, true);

        InputStream stream = httpClient.postStream("/responses", body);
        Iterator<SseParser.SseEvent> sseIterator = SseParser.stream(stream);

        return new OpenAIResponseStream(sseIterator, mapper);
    }

    private ObjectNode buildRequestBody(LlmContext context, ModelSettings settings, boolean stream) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", modelId);

        if (context.systemPrompt() != null && !context.systemPrompt().isEmpty()) {
            body.put("instructions", context.systemPrompt());
        }

        // Build input array
        ArrayNode input = mapper.createArrayNode();
        for (InputItem item : context.messages()) {
            switch (item) {
                case InputItem.UserMessage msg -> {
                    ObjectNode msgNode = mapper.createObjectNode();
                    msgNode.put("role", "user");
                    msgNode.put("content", msg.content());
                    input.add(msgNode);
                }
                case InputItem.AssistantMessage msg -> {
                    ObjectNode msgNode = mapper.createObjectNode();
                    msgNode.put("role", "assistant");
                    msgNode.put("content", msg.content());
                    input.add(msgNode);
                }
                case InputItem.ToolResult result -> {
                    ObjectNode msgNode = mapper.createObjectNode();
                    msgNode.put("type", "function_call_output");
                    msgNode.put("call_id", result.toolCallId());
                    msgNode.put("output", result.output());
                    input.add(msgNode);
                }
                case InputItem.SystemMessage msg -> {
                    ObjectNode msgNode = mapper.createObjectNode();
                    msgNode.put("role", "system");
                    msgNode.put("content", msg.content());
                    input.add(msgNode);
                }
            }
        }
        body.set("input", input);

        // Tools
        if (!context.tools().isEmpty()) {
            ArrayNode tools = mapper.createArrayNode();
            for (ToolDefinition toolDef : context.tools()) {
                ObjectNode toolNode = mapper.createObjectNode();
                toolNode.put("type", "function");
                toolNode.put("name", toolDef.name());
                toolNode.put("description", toolDef.description());
                toolNode.set("parameters", mapper.valueToTree(toolDef.parametersSchema()));
                tools.add(toolNode);
            }
            body.set("tools", tools);
        }

        // Structured output
        if (context.outputType() != null) {
            ObjectNode textFormat = mapper.createObjectNode();
            textFormat.put("type", "json_schema");
            ObjectNode schemaNode = mapper.createObjectNode();
            schemaNode.put("name", context.outputType().getSimpleName());
            schemaNode.set("schema", mapper.valueToTree(
                cz.krokviak.agents.output.JsonSchemaGenerator.generate(context.outputType())));
            schemaNode.put("strict", true);
            textFormat.set("json_schema", schemaNode);
            body.set("text", mapper.createObjectNode().set("format", textFormat));
        }

        // Settings
        if (settings != null) {
            if (settings.temperature() != null) body.put("temperature", settings.temperature());
            if (settings.topP() != null) body.put("top_p", settings.topP());
            if (settings.maxTokens() != null) body.put("max_output_tokens", settings.maxTokens());
        }

        if (stream) body.put("stream", true);

        return body;
    }

    private ModelResponse parseResponse(JsonNode response) {
        String id = response.path("id").asText();
        List<ModelResponse.OutputItem> outputs = new ArrayList<>();
        int inputTokens = response.path("usage").path("input_tokens").asInt(0);
        int outputTokens = response.path("usage").path("output_tokens").asInt(0);

        JsonNode outputArray = response.path("output");
        if (outputArray.isArray()) {
            for (JsonNode item : outputArray) {
                String type = item.path("type").asText();
                if ("message".equals(type)) {
                    String content = "";
                    JsonNode contentArray = item.path("content");
                    if (contentArray.isArray()) {
                        for (JsonNode c : contentArray) {
                            if ("output_text".equals(c.path("type").asText())) {
                                content = c.path("text").asText();
                            }
                        }
                    }
                    outputs.add(new ModelResponse.OutputItem.Message(content));
                } else if ("function_call".equals(type)) {
                    String callId = item.path("call_id").asText();
                    String name = item.path("name").asText();
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> args = mapper.readValue(
                            item.path("arguments").asText(), Map.class);
                        outputs.add(new ModelResponse.OutputItem.ToolCallRequest(callId, name, args));
                    } catch (Exception e) {
                        outputs.add(new ModelResponse.OutputItem.ToolCallRequest(callId, name, Map.of()));
                    }
                }
            }
        }

        return new ModelResponse(id, outputs, new Usage(inputTokens, outputTokens));
    }

    private static class OpenAIResponseStream implements ModelResponseStream {
        private final Iterator<SseParser.SseEvent> sseIterator;
        private final ObjectMapper mapper;

        OpenAIResponseStream(Iterator<SseParser.SseEvent> sseIterator, ObjectMapper mapper) {
            this.sseIterator = sseIterator;
            this.mapper = mapper;
        }

        @Override
        public Iterator<Event> iterator() {
            return new Iterator<>() {
                @Override
                public boolean hasNext() { return sseIterator.hasNext(); }

                @Override
                public Event next() {
                    SseParser.SseEvent sse = sseIterator.next();
                    if (sse.isDone()) return new Event.Done(null);
                    try {
                        JsonNode node = mapper.readTree(sse.data());
                        String type = node.path("type").asText();
                        if ("response.output_text.delta".equals(type)) {
                            return new Event.TextDelta(node.path("delta").asText());
                        }
                        if ("response.function_call_arguments.delta".equals(type)) {
                            return new Event.ToolCallDelta(
                                node.path("call_id").asText(),
                                node.path("name").asText(""),
                                node.path("delta").asText());
                        }
                        if ("response.completed".equals(type)) {
                            return new Event.Done(null);
                        }
                        return new Event.TextDelta("");
                    } catch (Exception e) {
                        return new Event.TextDelta("");
                    }
                }
            };
        }

        @Override
        public void close() {}
    }
}
