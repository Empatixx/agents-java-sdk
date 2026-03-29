package cz.krokviak.agents.tracing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class Span implements AutoCloseable {
    private final String spanId;
    private final String parentSpanId;
    private final String traceId;
    private final String name;
    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private final long startTimeMs;
    private final boolean active;

    Span(String name, String parentSpanId, String traceId, boolean active) {
        this.spanId = UUID.randomUUID().toString().substring(0, 16);
        this.parentSpanId = parentSpanId;
        this.traceId = traceId;
        this.name = name;
        this.startTimeMs = System.currentTimeMillis();
        this.active = active;
    }

    public void setAttribute(String key, Object value) {
        if (active) {
            attributes.put(key, value);
        }
    }

    public String spanId() { return spanId; }
    public String name() { return name; }

    @Override
    public void close() {
        if (active) {
            long endTimeMs = System.currentTimeMillis();
            var data = new SpanData(spanId, parentSpanId, traceId, name,
                Map.copyOf(attributes), startTimeMs, endTimeMs);
            Tracing.endSpan(this, data);
        }
    }
}
