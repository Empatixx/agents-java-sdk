package cz.krokviak.agents.model;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ResponsesModel;
import com.openai.models.responses.*;
import com.openai.core.http.StreamResponse;
import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.tool.ToolDefinition;

import java.util.*;

/**
 * OpenAI model using the official Java SDK with Responses API.
 */
public class OpenAIOfficalModel implements Model {

    private final OpenAIClient client;
    private final String modelId;

    public OpenAIOfficalModel(String apiKey, String baseUrl, String modelId) {
        this.modelId = modelId;
        var builder = OpenAIOkHttpClient.builder().apiKey(apiKey);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        this.client = builder.build();
    }

    @Override
    public ModelResponse call(LlmContext context, ModelSettings settings) {
        var params = buildParams(context, settings);
        Response response = client.responses().create(params);
        return convertResponse(response);
    }

    @Override
    public ModelResponseStream callStreamed(LlmContext context, ModelSettings settings) {
        var params = buildParams(context, settings);
        StreamResponse<ResponseStreamEvent> stream = client.responses().createStreaming(params);
        return new OfficialResponseStream(stream);
    }

    private ResponseCreateParams buildParams(LlmContext context, ModelSettings settings) {
        var builder = ResponseCreateParams.builder()
            .model(ResponsesModel.ofString(modelId));

        if (context.systemPrompt() != null && !context.systemPrompt().isEmpty()) {
            builder.instructions(context.systemPrompt());
        }

        // Build input messages
        List<ResponseInputItem> input = new ArrayList<>();
        for (InputItem item : context.messages()) {
            switch (item) {
                case InputItem.UserMessage msg -> input.add(ResponseInputItem.ofEasyInputMessage(
                    EasyInputMessage.builder()
                        .role(EasyInputMessage.Role.USER)
                        .content(EasyInputMessage.Content.ofTextInput(msg.content()))
                        .build()));
                case InputItem.AssistantMessage msg -> {
                    if (msg.content() != null && !msg.content().isEmpty()) {
                        input.add(ResponseInputItem.ofEasyInputMessage(
                            EasyInputMessage.builder()
                                .role(EasyInputMessage.Role.ASSISTANT)
                                .content(EasyInputMessage.Content.ofTextInput(msg.content()))
                                .build()));
                    }
                    // Add function calls as separate items
                    for (var tc : msg.toolCalls()) {
                        input.add(ResponseInputItem.ofFunctionCall(
                            ResponseFunctionToolCall.builder()
                                .callId(tc.id())
                                .name(tc.name())
                                .arguments(serializeArgs(tc.arguments()))
                                .build()));
                    }
                }
                case InputItem.ToolResult result -> input.add(ResponseInputItem.ofFunctionCallOutput(
                    ResponseInputItem.FunctionCallOutput.builder()
                        .callId(result.toolCallId())
                        .output(result.output())
                        .build()));
                case InputItem.SystemMessage msg -> input.add(ResponseInputItem.ofEasyInputMessage(
                    EasyInputMessage.builder()
                        .role(EasyInputMessage.Role.SYSTEM)
                        .content(EasyInputMessage.Content.ofTextInput(msg.content()))
                        .build()));
                case InputItem.CompactionMarker marker -> input.add(ResponseInputItem.ofEasyInputMessage(
                    EasyInputMessage.builder()
                        .role(EasyInputMessage.Role.SYSTEM)
                        .content(EasyInputMessage.Content.ofTextInput("[Conversation Summary]\n" + marker.summary()))
                        .build()));
            }
        }
        if (!input.isEmpty()) {
            builder.inputOfResponse(input);
        }

        // Tools
        if (!context.tools().isEmpty()) {
            List<Tool> tools = context.tools().stream()
                .map(td -> Tool.ofFunction(FunctionTool.builder()
                    .name(td.name())
                    .description(td.description())
                    .strict(false)
                    .parameters(FunctionTool.Parameters.builder()
                        .putAllAdditionalProperties(td.parametersSchema().entrySet().stream()
                            .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                e -> com.openai.core.JsonValue.from(e.getValue()))))
                        .build())
                    .build()))
                .toList();
            builder.tools(tools);
        }

        if (settings != null && settings.temperature() != null) {
            builder.temperature(settings.temperature());
        }
        if (settings != null && settings.maxTokens() != null) {
            builder.maxOutputTokens(settings.maxTokens().longValue());
        }

        return builder.build();
    }

    private ModelResponse convertResponse(Response response) {
        List<ModelResponse.OutputItem> outputs = new ArrayList<>();
        for (var item : response.output()) {
            item.message().ifPresent(msg -> {
                for (var content : msg.content()) {
                    content.outputText().ifPresent(t -> {
                        outputs.add(new ModelResponse.OutputItem.Message(t.text()));
                    });
                }
            });
            item.functionCall().ifPresent(fc -> {
                Map<String, Object> args = parseArgs(fc.arguments());
                outputs.add(new ModelResponse.OutputItem.ToolCallRequest(fc.callId(), fc.name(), args));
            });
        }
        int inTok = response.usage().map(u -> (int) u.inputTokens()).orElse(0);
        int outTok = response.usage().map(u -> (int) u.outputTokens()).orElse(0);
        return new ModelResponse(response.id(), outputs, new Usage(inTok, outTok));
    }

    private Map<String, Object> parseArgs(String json) {
        try {
            @SuppressWarnings("unchecked")
            var parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
            return parsed;
        } catch (Exception e) { return Map.of(); }
    }

    private String serializeArgs(Map<String, Object> args) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(args);
        } catch (Exception e) { return "{}"; }
    }

    // ---- Streaming ----

    private static class OfficialResponseStream implements ModelResponseStream {
        private final StreamResponse<ResponseStreamEvent> stream;

        // Accumulate tool calls
        private final Map<String, String> toolNames = new LinkedHashMap<>();
        private final Map<String, StringBuilder> toolArgs = new LinkedHashMap<>();
        private String currentCallId;

        OfficialResponseStream(StreamResponse<ResponseStreamEvent> stream) {
            this.stream = stream;
        }

        @Override
        public Iterator<Event> iterator() {
            var it = stream.stream().iterator();
            return new Iterator<>() {
                @Override public boolean hasNext() { return it.hasNext(); }

                @Override
                public Event next() {
                    while (it.hasNext()) {
                        ResponseStreamEvent event = it.next();

                        // Text delta
                        var textDelta = event.outputTextDelta();
                        if (textDelta.isPresent()) {
                            return new Event.TextDelta(textDelta.get().delta());
                        }

                        // Function call added — get callId + name
                        var itemAdded = event.outputItemAdded();
                        if (itemAdded.isPresent()) {
                            var fc = itemAdded.get().item().functionCall();
                            if (fc.isPresent()) {
                                currentCallId = fc.get().callId();
                                toolNames.put(currentCallId, fc.get().name());
                                toolArgs.put(currentCallId, new StringBuilder());
                            }
                            continue;
                        }

                        // Function call args delta
                        var fcDelta = event.functionCallArgumentsDelta();
                        if (fcDelta.isPresent()) {
                            String id = currentCallId; // itemId not same as callId
                            if (id != null) {
                                toolArgs.computeIfAbsent(id, _ -> new StringBuilder())
                                    .append(fcDelta.get().delta());
                                return new Event.ToolCallDelta(id,
                                    toolNames.getOrDefault(id, ""),
                                    fcDelta.get().delta());
                            }
                            continue;
                        }

                        // Completed — build final response
                        var completed = event.completed();
                        if (completed.isPresent()) {
                            Response resp = completed.get().response();
                            List<ModelResponse.OutputItem> outputs = new ArrayList<>();
                            for (var out : resp.output()) {
                                out.message().ifPresent(msg -> {
                                    for (var c : msg.content()) {
                                        c.outputText().ifPresent(t ->
                                            outputs.add(new ModelResponse.OutputItem.Message(t.text())));
                                    }
                                });
                                out.functionCall().ifPresent(fc -> {
                                    Map<String, Object> args;
                                    try {
                                        @SuppressWarnings("unchecked")
                                        var p = new com.fasterxml.jackson.databind.ObjectMapper()
                                            .readValue(fc.arguments(), Map.class);
                                        args = p;
                                    } catch (Exception e) { args = Map.of(); }
                                    outputs.add(new ModelResponse.OutputItem.ToolCallRequest(
                                        fc.callId(), fc.name(), args));
                                });
                            }
                            int inTok = resp.usage().map(u -> (int) u.inputTokens()).orElse(0);
                            int outTok = resp.usage().map(u -> (int) u.outputTokens()).orElse(0);
                            return new Event.Done(new ModelResponse(resp.id(), outputs, new Usage(inTok, outTok)));
                        }
                    }
                    // Stream ended without completed event
                    return new Event.Done(new ModelResponse(null, List.of(), Usage.zero()));
                }
            };
        }

        @Override
        public void close() { stream.close(); }
    }
}
