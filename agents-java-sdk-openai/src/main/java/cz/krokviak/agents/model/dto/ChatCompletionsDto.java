package cz.krokviak.agents.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public final class ChatCompletionsDto {
    private ChatCompletionsDto() {}

    // ---- REQUEST ----

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Request(
        String model,
        List<Message> messages,
        List<Tool> tools,
        Double temperature,
        @JsonProperty("top_p") Double topP,
        @JsonProperty("max_tokens") Integer maxTokens,
        Boolean stream,
        @JsonProperty("stream_options") StreamOptions streamOptions
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StreamOptions(
        @JsonProperty("include_usage") Boolean includeUsage
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(
        String role,
        String content,
        @JsonProperty("tool_call_id") String toolCallId,
        @JsonProperty("tool_calls") List<ToolCallOut> toolCalls
    ) {}

    public record ToolCallOut(
        String id,
        String type,
        Function function
    ) {}

    public record Function(
        String name,
        String arguments
    ) {}

    public record Tool(
        String type,
        Function function
    ) {
        public record Function(
            String name,
            String description,
            Map<String, Object> parameters
        ) {}
    }

    // ---- RESPONSE ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(
        String id,
        ResponseUsage usage,
        List<Choice> choices
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResponseUsage(
        @JsonProperty("prompt_tokens") int promptTokens,
        @JsonProperty("completion_tokens") int completionTokens
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
        ResponseMessage message
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResponseMessage(
        String content,
        @JsonProperty("tool_calls") List<ToolCallIn> toolCalls
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ToolCallIn(
        String id,
        Function function
    ) {}

    // ---- STREAMING ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StreamChunk(
        String id,
        List<StreamChoice> choices,
        ResponseUsage usage
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StreamChoice(
        StreamDelta delta,
        @JsonProperty("finish_reason") String finishReason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StreamDelta(
        String content,
        @JsonProperty("tool_calls") List<StreamToolCall> toolCalls
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StreamToolCall(
        int index,
        String id,
        Function function
    ) {}
}
