package cz.krokviak.agents.tracing;

import java.util.Map;

public record SpanData(
    String spanId,
    String parentSpanId,
    String traceId,
    String name,
    Map<String, Object> attributes,
    long startTimeMs,
    long endTimeMs
) {
    public long durationMs() {
        return endTimeMs - startTimeMs;
    }
}
