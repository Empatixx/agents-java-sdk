package cz.krokviak.agents.tracing;

import cz.krokviak.agents.http.AgentHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exports spans to the OpenAI traces API endpoint.
 * Format mirrors the OpenAI Agents Python SDK trace format.
 */
public class OpenAISpanExporter implements SpanExporter {
    private static final Logger log = LoggerFactory.getLogger(OpenAISpanExporter.class);
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String TRACES_PATH = "/traces";

    private final AgentHttpClient httpClient;

    public OpenAISpanExporter(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL);
    }

    public OpenAISpanExporter(String apiKey, String baseUrl) {
        this.httpClient = new AgentHttpClient(baseUrl, apiKey);
    }

    /** Package-private constructor for testing with a pre-built client. */
    OpenAISpanExporter(AgentHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public void export(List<SpanData> spans) {
        if (spans.isEmpty()) return;
        try {
            Map<String, Object> payload = buildPayload(spans);
            httpClient.post(TRACES_PATH, payload, Map.class);
        } catch (Exception e) {
            log.warn("Failed to export {} span(s) to OpenAI traces API: {}", spans.size(), e.getMessage());
        }
    }

    /** Builds the JSON payload matching OpenAI's trace ingest format. */
    Map<String, Object> buildPayload(List<SpanData> spans) {
        List<Map<String, Object>> spanObjects = new ArrayList<>();
        for (SpanData span : spans) {
            spanObjects.add(toSpanObject(span));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("object", "trace");
        payload.put("spans", spanObjects);
        return payload;
    }

    private Map<String, Object> toSpanObject(SpanData span) {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("object", "trace.span");
        obj.put("id", span.spanId());
        if (span.parentSpanId() != null) {
            obj.put("parent_id", span.parentSpanId());
        }
        obj.put("trace_id", span.traceId());
        obj.put("name", span.name());
        obj.put("type", span.type().name().toLowerCase());
        obj.put("started_at", span.startTimeMs());
        obj.put("ended_at", span.endTimeMs());
        obj.put("duration_ms", span.durationMs());
        if (!span.attributes().isEmpty()) {
            obj.put("attributes", span.attributes());
        }
        if (span.typedData() != null) {
            obj.put("span_data", toTypedDataObject(span.typedData()));
        }
        return obj;
    }

    private Map<String, Object> toTypedDataObject(Object typedData) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (typedData instanceof AgentSpanData asd) {
            data.put("type", "agent");
            data.put("agent_name", asd.agentName());
            if (asd.handoffs() != null && !asd.handoffs().isEmpty()) {
                data.put("handoffs", asd.handoffs());
            }
            if (asd.tools() != null && !asd.tools().isEmpty()) {
                data.put("tools", asd.tools());
            }
            if (asd.outputType() != null) {
                data.put("output_type", asd.outputType());
            }
        } else if (typedData instanceof GenerationSpanData gsd) {
            data.put("type", "generation");
            data.put("model", gsd.model());
            data.put("input_tokens", gsd.inputTokens());
            data.put("output_tokens", gsd.outputTokens());
            if (gsd.responseId() != null) {
                data.put("response_id", gsd.responseId());
            }
        } else if (typedData instanceof ToolSpanData tsd) {
            data.put("type", "function");
            data.put("tool_name", tsd.toolName());
            data.put("tool_type", tsd.toolType());
            if (tsd.arguments() != null && !tsd.arguments().isEmpty()) {
                data.put("arguments", tsd.arguments());
            }
        } else {
            data.put("type", "custom");
            data.put("value", typedData.toString());
        }
        return data;
    }
}
