package cz.krokviak.agents.tracing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TracingTest {

    @AfterEach
    void cleanup() {
        Tracing.reset();
    }

    @Test
    void spanCapturesNameAndAttributes() {
        var captured = new ArrayList<SpanData>();
        TraceConfig.builder()
            .enabled(true)
            .addExporter(spans -> captured.addAll(spans))
            .build()
            .install();

        try (Span span = Tracing.span("test-op")) {
            span.setAttribute("key", "value");
        }
        Tracing.flush();

        assertEquals(1, captured.size());
        assertEquals("test-op", captured.getFirst().name());
        assertEquals("value", captured.getFirst().attributes().get("key"));
    }

    @Test
    void nestedSpansTrackParent() {
        var captured = new ArrayList<SpanData>();
        TraceConfig.builder()
            .enabled(true)
            .addExporter(spans -> captured.addAll(spans))
            .build()
            .install();

        try (Span outer = Tracing.span("outer")) {
            try (Span inner = Tracing.span("inner")) {
                inner.setAttribute("level", "inner");
            }
        }
        Tracing.flush();

        assertEquals(2, captured.size());
        var inner = captured.stream().filter(s -> s.name().equals("inner")).findFirst().orElseThrow();
        var outer = captured.stream().filter(s -> s.name().equals("outer")).findFirst().orElseThrow();
        assertEquals(outer.spanId(), inner.parentSpanId());
    }

    @Test
    void disabledTracingIsNoOp() {
        var captured = new ArrayList<SpanData>();
        TraceConfig.builder()
            .enabled(false)
            .addExporter(spans -> captured.addAll(spans))
            .build()
            .install();

        try (Span span = Tracing.span("test")) {
            span.setAttribute("k", "v");
        }
        Tracing.flush();

        assertTrue(captured.isEmpty());
    }

    @Test
    void spanRecordsDuration() {
        var captured = new ArrayList<SpanData>();
        TraceConfig.builder()
            .enabled(true)
            .addExporter(spans -> captured.addAll(spans))
            .build()
            .install();

        try (Span span = Tracing.span("timed")) {
            // some work
        }
        Tracing.flush();

        assertTrue(captured.getFirst().durationMs() >= 0);
    }
}
