package cz.krokviak.agents.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.krokviak.agents.http.AgentHttpClient;
import cz.krokviak.agents.http.SseParser;
import cz.krokviak.agents.model.dto.ResponsesDto;
import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.tool.ToolDefinition;

import java.io.InputStream;
import java.util.*;

public class OpenAIResponsesModel implements Model {
    private final AgentHttpClient httpClient;
    private final ObjectMapper mapper;
    private final String modelId;

    public OpenAIResponsesModel(String apiKey) {
        this(apiKey, "https://api.openai.com/v1", "gpt-5.4-2026-03-05");
    }

    public OpenAIResponsesModel(String apiKey, String baseUrl, String modelId) {
        this.httpClient = new AgentHttpClient(baseUrl, apiKey);
        this.mapper = httpClient.objectMapper();
        this.modelId = modelId;
    }

    @Override
    public ModelResponse call(LlmContext context, ModelSettings settings) {
        ResponsesDto.Request body = buildRequestBody(context, settings, false);
        try {
            ResponsesDto.Response response = httpClient.post(
                "/responses", body, ResponsesDto.Response.class);
            return parseResponse(response);
        } catch (Exception e) {
            throw new RuntimeException("OpenAI Responses API call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public ModelResponseStream callStreamed(LlmContext context, ModelSettings settings) {
        ResponsesDto.Request body = buildRequestBody(context, settings, true);
        InputStream stream = httpClient.postStream("/responses", body);
        Iterator<SseParser.SseEvent> sseIterator = SseParser.stream(stream);
        return new OpenAIResponseStream(sseIterator, mapper);
    }

    private ResponsesDto.Request buildRequestBody(LlmContext context, ModelSettings settings, boolean stream) {
        String instructions = (context.systemPrompt() != null && !context.systemPrompt().isEmpty())
            ? context.systemPrompt() : null;

        List<ResponsesDto.InputMessage> input = new ArrayList<>();
        for (InputItem item : context.messages()) {
            switch (item) {
                case InputItem.UserMessage msg ->
                    input.add(new ResponsesDto.InputMessage("user", null, msg.content(), null, null));
                case InputItem.AssistantMessage msg ->
                    input.add(new ResponsesDto.InputMessage("assistant", null, msg.content(), null, null));
                case InputItem.ToolResult result ->
                    input.add(new ResponsesDto.InputMessage(null, "function_call_output", null, result.toolCallId(), result.output()));
                case InputItem.SystemMessage msg ->
                    input.add(new ResponsesDto.InputMessage("system", null, msg.content(), null, null));
                case InputItem.CompactionMarker marker ->
                    input.add(new ResponsesDto.InputMessage("system", null, "[Conversation Summary]\n" + marker.summary(), null, null));
                case InputItem.ImageContent _ -> {} // images not forwarded in this context
            }
        }

        List<ResponsesDto.Tool> tools = context.tools().isEmpty() ? null :
            context.tools().stream().map(td -> new ResponsesDto.Tool(
                "function", td.name(), td.description(), td.parametersSchema()
            )).toList();

        ResponsesDto.TextFormat textFormat = null;
        if (context.outputType() != null) {
            Map<String, Object> schema = cz.krokviak.agents.output.JsonSchemaGenerator.generate(context.outputType());
            textFormat = new ResponsesDto.TextFormat(
                new ResponsesDto.FormatSpec("json_schema",
                    new ResponsesDto.JsonSchemaSpec(context.outputType().getSimpleName(), schema, true)));
        }

        return new ResponsesDto.Request(
            modelId, instructions, input, tools, textFormat,
            settings != null ? settings.temperature() : null,
            settings != null ? settings.topP() : null,
            settings != null ? settings.maxTokens() : null,
            stream ? Boolean.TRUE : null
        );
    }

    private ModelResponse parseResponse(ResponsesDto.Response response) {
        String id = response.id();
        int inputTokens = response.usage() != null ? response.usage().inputTokens() : 0;
        int outputTokens = response.usage() != null ? response.usage().outputTokens() : 0;

        List<ModelResponse.OutputItem> outputs = new ArrayList<>();
        if (response.output() != null) {
            for (ResponsesDto.OutputItem item : response.output()) {
                String type = item.type();
                if ("message".equals(type)) {
                    String content = "";
                    if (item.content() != null) {
                        for (ResponsesDto.ContentPart c : item.content()) {
                            if ("output_text".equals(c.type())) content = c.text();
                        }
                    }
                    outputs.add(new ModelResponse.OutputItem.Message(content));
                } else if ("function_call".equals(type)) {
                    Map<String, Object> args;
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> parsed = mapper.readValue(item.arguments(), Map.class);
                        args = parsed;
                    } catch (Exception e) { args = Map.of(); }
                    outputs.add(new ModelResponse.OutputItem.ToolCallRequest(item.callId(), item.name(), args));
                }
            }
        }
        return new ModelResponse(id, outputs, new Usage(inputTokens, outputTokens));
    }

    private static class OpenAIResponseStream implements ModelResponseStream {
        private final Iterator<SseParser.SseEvent> sseIterator;
        private final ObjectMapper mapper;

        // Accumulate tool call state
        private final Map<String, String> toolCallNames = new LinkedHashMap<>();
        private final Map<String, StringBuilder> toolCallArgs = new LinkedHashMap<>();
        private String currentCallId; // track current function_call being streamed
        private String responseId;

        OpenAIResponseStream(Iterator<SseParser.SseEvent> sseIterator, ObjectMapper mapper) {
            this.sseIterator = sseIterator;
            this.mapper = mapper;
        }

        @Override
        public Iterator<Event> iterator() {
            return new Iterator<>() {
                @Override public boolean hasNext() { return sseIterator.hasNext(); }

                @Override
                public Event next() {
                    while (sseIterator.hasNext()) {
                        SseParser.SseEvent sse = sseIterator.next();
                        if (sse.isDone()) return buildDone();

                        try {
                            ResponsesDto.StreamEvent event = mapper.readValue(
                                sse.data(), ResponsesDto.StreamEvent.class);
                            String type = event.type();

                            if ("response.created".equals(type) || "response.in_progress".equals(type)) {
                                continue; // skip metadata events
                            }

                            if ("response.output_text.delta".equals(type)) {
                                return new Event.TextDelta(event.delta() != null ? event.delta() : "");
                            }

                            // Function call: track call_id from output_item.added
                            if ("response.output_item.added".equals(type)) {
                                if (event.callId() != null) {
                                    currentCallId = event.callId();
                                    if (event.name() != null) {
                                        toolCallNames.put(currentCallId, event.name());
                                    }
                                }
                                continue;
                            }

                            if ("response.function_call_arguments.delta".equals(type)) {
                                String callId = event.callId() != null ? event.callId() : currentCallId;
                                if (callId == null) callId = "unknown-" + toolCallArgs.size();
                                currentCallId = callId;
                                if (event.name() != null) toolCallNames.putIfAbsent(callId, event.name());
                                toolCallArgs.computeIfAbsent(callId, _ -> new StringBuilder())
                                    .append(event.delta() != null ? event.delta() : "");
                                return new Event.ToolCallDelta(callId,
                                    toolCallNames.getOrDefault(callId, ""),
                                    event.delta() != null ? event.delta() : "");
                            }

                            if ("response.function_call_arguments.done".equals(type)) {
                                // Args complete — nothing extra to emit
                                continue;
                            }

                            if ("response.completed".equals(type) || "response.done".equals(type)) {
                                return buildDone();
                            }

                            // Skip other event types
                        } catch (Exception e) {
                            // Skip unparseable events
                        }
                    }
                    return buildDone();
                }
            };
        }

        private Event.Done buildDone() {
            List<ModelResponse.OutputItem> outputs = new ArrayList<>();
            for (var entry : toolCallNames.entrySet()) {
                String callId = entry.getKey();
                String name = entry.getValue();
                String argsJson = toolCallArgs.containsKey(callId)
                    ? toolCallArgs.get(callId).toString() : "{}";
                Map<String, Object> args;
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsed = mapper.readValue(argsJson, Map.class);
                    args = parsed;
                } catch (Exception e) { args = Map.of(); }
                outputs.add(new ModelResponse.OutputItem.ToolCallRequest(callId, name, args));
            }
            return new Event.Done(new ModelResponse(responseId, outputs, Usage.zero()));
        }

        @Override public void close() {}
    }
}
