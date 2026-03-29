package cz.krokviak.agents.tracing;

import java.util.Map;

public record SpanData(
    String spanId,
    String parentSpanId,
    String traceId,
    String name,
    Map<String, Object> attributes,
    long startTimeMs,
    long endTimeMs,
    SpanType type,
    Object typedData
) {
    /** Backward-compatible constructor defaulting to CUSTOM with no typed data. */
    public SpanData(
        String spanId,
        String parentSpanId,
        String traceId,
        String name,
        Map<String, Object> attributes,
        long startTimeMs,
        long endTimeMs
    ) {
        this(spanId, parentSpanId, traceId, name, attributes, startTimeMs, endTimeMs,
             SpanType.CUSTOM, null);
    }

    public long durationMs() {
        return endTimeMs - startTimeMs;
    }
}
