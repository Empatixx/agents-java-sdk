package cz.krokviak.agents.model.dto;

import com.fasterxml.jackson.annotation.*;

import java.util.List;
import java.util.Map;

public final class AnthropicDto {

    private AnthropicDto() {}

    // ── Request types ──

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Request(
        @JsonProperty("model") String model,
        @JsonProperty("max_tokens") int maxTokens,
        @JsonProperty("system") String system,
        @JsonProperty("messages") List<Message> messages,
        @JsonProperty("stream") Boolean stream,
        @JsonProperty("tools") List<Tool> tools,
        @JsonProperty("temperature") Double temperature,
        @JsonProperty("top_p") Double topP
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(
        @JsonProperty("role") String role,
        @JsonProperty("content") List<ContentBlock> content
    ) {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = ContentBlock.TextBlock.class, name = "text"),
        @JsonSubTypes.Type(value = ContentBlock.ToolUseBlock.class, name = "tool_use"),
        @JsonSubTypes.Type(value = ContentBlock.ToolResultBlock.class, name = "tool_result"),
        @JsonSubTypes.Type(value = ContentBlock.ImageBlock.class, name = "image")
    })
    public sealed interface ContentBlock permits ContentBlock.TextBlock, ContentBlock.ToolUseBlock, ContentBlock.ToolResultBlock, ContentBlock.ImageBlock {

        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        record TextBlock(
            @JsonProperty("text") String text
        ) implements ContentBlock {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        record ToolUseBlock(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("input") Map<String, Object> input
        ) implements ContentBlock {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        record ToolResultBlock(
            @JsonProperty("tool_use_id") String toolUseId,
            @JsonProperty("content") String content
        ) implements ContentBlock {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        record ImageBlock(
            @JsonProperty("source") ImageSource source
        ) implements ContentBlock {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        record ImageSource(
            @JsonProperty("type") String type,
            @JsonProperty("media_type") String mediaType,
            @JsonProperty("data") String data
        ) {
            public static ImageSource base64(String mediaType, String data) {
                return new ImageSource("base64", mediaType, data);
            }
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Tool(
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("input_schema") InputSchema inputSchema
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InputSchema(
        @JsonProperty("type") String type,
        @JsonProperty("properties") Map<String, Object> properties,
        @JsonProperty("required") List<String> required
    ) {}

    // ── Response types ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(
        @JsonProperty("id") String id,
        @JsonProperty("role") String role,
        @JsonProperty("content") List<ContentBlock> content,
        @JsonProperty("usage") Usage usage,
        @JsonProperty("stop_reason") String stopReason,
        @JsonProperty("model") String model,
        @JsonProperty("type") String type
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
        @JsonProperty("input_tokens") int inputTokens,
        @JsonProperty("output_tokens") int outputTokens
    ) {}

    // ── Stream event types ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StreamMessageStart(
        @JsonProperty("type") String type,
        @JsonProperty("message") Response message
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StreamContentBlockStart(
        @JsonProperty("type") String type,
        @JsonProperty("index") int index,
        @JsonProperty("content_block") ContentBlock contentBlock
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StreamContentBlockDelta(
        @JsonProperty("type") String type,
        @JsonProperty("index") int index,
        @JsonProperty("delta") Delta delta
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Delta(
            @JsonProperty("type") String type,
            @JsonProperty("text") String text,
            @JsonProperty("partial_json") String partialJson
        ) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StreamMessageDelta(
        @JsonProperty("type") String type,
        @JsonProperty("delta") MessageDelta delta,
        @JsonProperty("usage") Usage usage
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record MessageDelta(
            @JsonProperty("stop_reason") String stopReason
        ) {}
    }
}
