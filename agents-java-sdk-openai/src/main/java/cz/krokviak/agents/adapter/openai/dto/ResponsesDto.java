package cz.krokviak.agents.adapter.openai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public final class ResponsesDto {
    private ResponsesDto() {}

    // ---- REQUEST ----

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Request(
        String model,
        String instructions,
        List<InputMessage> input,
        List<Tool> tools,
        TextFormat text,
        Double temperature,
        @JsonProperty("top_p") Double topP,
        @JsonProperty("max_output_tokens") Integer maxOutputTokens,
        Boolean stream
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InputMessage(
        String role,
        String type,
        String content,
        @JsonProperty("call_id") String callId,
        String output
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Tool(
        String type,
        String name,
        String description,
        Map<String, Object> parameters
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TextFormat(
        FormatSpec format
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FormatSpec(
        String type,
        @JsonProperty("json_schema") JsonSchemaSpec jsonSchema
    ) {}

    public record JsonSchemaSpec(
        String name,
        Map<String, Object> schema,
        boolean strict
    ) {}

    // ---- RESPONSE ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(
        String id,
        ResponseUsage usage,
        List<OutputItem> output
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResponseUsage(
        @JsonProperty("input_tokens") int inputTokens,
        @JsonProperty("output_tokens") int outputTokens
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutputItem(
        String type,
        List<ContentPart> content,
        @JsonProperty("call_id") String callId,
        String name,
        String arguments
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentPart(
        String type,
        String text
    ) {}

    // ---- STREAMING ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StreamEvent(
        String type,
        String delta,
        @JsonProperty("call_id") String callId,
        String name
    ) {}
}
