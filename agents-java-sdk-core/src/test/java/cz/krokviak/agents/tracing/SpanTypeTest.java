package cz.krokviak.agents.tracing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpanTypeTest {

    @AfterEach
    void cleanup() {
        Tracing.reset();
    }

    private void enableTracing(List<SpanData> captured) {
        TraceConfig.builder()
            .enabled(true)
            .addExporter(captured::addAll)
            .build()
            .install();
    }

    // ---- Factory method tests ----

    @Test
    void agentSpanHasCorrectType() {
        var captured = new ArrayList<SpanData>();
        enableTracing(captured);

        try (Span span = Tracing.agentSpan("MyAgent")) {
            // no-op
        }
        Tracing.flush();

        assertEquals(1, captured.size());
        SpanData data = captured.getFirst();
        assertEquals(SpanType.AGENT, data.type());
        assertTrue(data.name().contains("MyAgent"));
    }

    @Test
    void agentSpanHasAgentSpanData() {
        var captured = new ArrayList<SpanData>();
        enableTracing(captured);

        try (Span span = Tracing.agentSpan("MyAgent")) {
            // no-op
        }
        Tracing.flush();

        SpanData data = captured.getFirst();
        assertInstanceOf(AgentSpanData.class, data.typedData());
        AgentSpanData asd = (AgentSpanData) data.typedData();
        assertEquals("MyAgent", asd.agentName());
    }

    @Test
    void generationSpanHasCorrectType() {
        var captured = new ArrayList<SpanData>();
        enableTracing(captured);

        try (Span span = Tracing.generationSpan("gpt-4o")) {
            // no-op
        }
        Tracing.flush();

        SpanData data = captured.getFirst();
        assertEquals(SpanType.GENERATION, data.type());
        assertInstanceOf(GenerationSpanData.class, data.typedData());
        GenerationSpanData gsd = (GenerationSpanData) data.typedData();
        assertEquals("gpt-4o", gsd.model());
    }

    @Test
    void functionSpanHasCorrectType() {
        var captured = new ArrayList<SpanData>();
        enableTracing(captured);

        try (Span span = Tracing.functionSpan("my_tool")) {
            // no-op
        }
        Tracing.flush();

        SpanData data = captured.getFirst();
        assertEquals(SpanType.FUNCTION, data.type());
        assertInstanceOf(ToolSpanData.class, data.typedData());
        ToolSpanData tsd = (ToolSpanData) data.typedData();
        assertEquals("my_tool", tsd.toolName());
        assertEquals("function", tsd.toolType());
    }

    @Test
    void guardrailSpanHasCorrectType() {
        var captured = new ArrayList<SpanData>();
        enableTracing(captured);

        try (Span span = Tracing.guardrailSpan("length-check")) {
            // no-op
        }
        Tracing.flush();

        SpanData data = captured.getFirst();
        assertEquals(SpanType.GUARDRAIL, data.type());
        assertTrue(data.name().contains("length-check"));
    }

    @Test
    void handoffSpanHasCorrectType() {
        var captured = new ArrayList<SpanData>();
        enableTracing(captured);

        try (Span span = Tracing.handoffSpan("AgentA", "AgentB")) {
            // no-op
        }
        Tracing.flush();

        SpanData data = captured.getFirst();
        assertEquals(SpanType.HANDOFF, data.type());
        assertTrue(data.name().contains("AgentA"));
        assertTrue(data.name().contains("AgentB"));
    }

    @Test
    void plainSpanDefaultsToCustomType() {
        var captured = new ArrayList<SpanData>();
        enableTracing(captured);

        try (Span span = Tracing.span("my-span")) {
            // no-op
        }
        Tracing.flush();

        SpanData data = captured.getFirst();
        assertEquals(SpanType.CUSTOM, data.type());
        assertNull(data.typedData());
    }

    @Test
    void setTypeAndTypedDataPreservedInSpanData() {
        var captured = new ArrayList<SpanData>();
        enableTracing(captured);

        try (Span span = Tracing.span("custom-span")) {
            span.setType(SpanType.FUNCTION);
            span.setTypedData(new ToolSpanData("calc", "builtin", Map.of("x", 1)));
        }
        Tracing.flush();

        SpanData data = captured.getFirst();
        assertEquals(SpanType.FUNCTION, data.type());
        assertInstanceOf(ToolSpanData.class, data.typedData());
        ToolSpanData tsd = (ToolSpanData) data.typedData();
        assertEquals("calc", tsd.toolName());
        assertEquals("builtin", tsd.toolType());
        assertEquals(1, tsd.arguments().get("x"));
    }

    @Test
    void backwardCompatSpanDataConstructorDefaultsToCustom() {
        SpanData data = new SpanData("id1", null, "trace1", "test", Map.of(), 100L, 200L);
        assertEquals(SpanType.CUSTOM, data.type());
        assertNull(data.typedData());
        assertEquals(100L, data.durationMs());
    }

}
