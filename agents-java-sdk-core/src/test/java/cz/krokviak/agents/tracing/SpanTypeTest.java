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

    // ---- OpenAI exporter JSON tests ----

    @Test
    void openAIExporterBuildsCorrectJsonForAgentSpan() {
        OpenAISpanExporter exporter = new OpenAISpanExporter("test-key");

        SpanData span = new SpanData("span1", null, "trace1", "agent:TestAgent",
            Map.of(), 1000L, 2000L, SpanType.AGENT, new AgentSpanData("TestAgent", List.of("AgentB"), List.of("tool1"), "text"));

        Map<String, Object> payload = exporter.buildPayload(List.of(span));

        assertEquals("trace", payload.get("object"));
        List<?> spans = (List<?>) payload.get("spans");
        assertEquals(1, spans.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> spanObj = (Map<String, Object>) spans.getFirst();
        assertEquals("trace.span", spanObj.get("object"));
        assertEquals("span1", spanObj.get("id"));
        assertEquals("trace1", spanObj.get("trace_id"));
        assertEquals("agent", spanObj.get("type"));
        assertEquals(1000L, spanObj.get("started_at"));
        assertEquals(2000L, spanObj.get("ended_at"));
        assertEquals(1000L, spanObj.get("duration_ms"));

        @SuppressWarnings("unchecked")
        Map<String, Object> spanData = (Map<String, Object>) spanObj.get("span_data");
        assertNotNull(spanData);
        assertEquals("agent", spanData.get("type"));
        assertEquals("TestAgent", spanData.get("agent_name"));
        assertEquals(List.of("AgentB"), spanData.get("handoffs"));
        assertEquals(List.of("tool1"), spanData.get("tools"));
        assertEquals("text", spanData.get("output_type"));
    }

    @Test
    void openAIExporterBuildsCorrectJsonForGenerationSpan() {
        OpenAISpanExporter exporter = new OpenAISpanExporter("test-key");

        SpanData span = new SpanData("span2", "parent1", "trace1", "generation",
            Map.of("agent", "MyAgent"), 500L, 1500L,
            SpanType.GENERATION, new GenerationSpanData("gpt-4o", 100, 200, "resp-abc"));

        Map<String, Object> payload = exporter.buildPayload(List.of(span));
        @SuppressWarnings("unchecked")
        Map<String, Object> spanObj = (Map<String, Object>) ((List<?>) payload.get("spans")).getFirst();

        assertEquals("parent1", spanObj.get("parent_id"));
        assertEquals("generation", spanObj.get("type"));
        assertEquals(Map.of("agent", "MyAgent"), spanObj.get("attributes"));

        @SuppressWarnings("unchecked")
        Map<String, Object> spanData = (Map<String, Object>) spanObj.get("span_data");
        assertEquals("generation", spanData.get("type"));
        assertEquals("gpt-4o", spanData.get("model"));
        assertEquals(100, spanData.get("input_tokens"));
        assertEquals(200, spanData.get("output_tokens"));
        assertEquals("resp-abc", spanData.get("response_id"));
    }

    @Test
    void openAIExporterBuildsCorrectJsonForFunctionSpan() {
        OpenAISpanExporter exporter = new OpenAISpanExporter("test-key");

        SpanData span = new SpanData("span3", null, "trace1", "function:my_tool",
            Map.of(), 0L, 50L,
            SpanType.FUNCTION, new ToolSpanData("my_tool", "function", Map.of("arg1", "val1")));

        Map<String, Object> payload = exporter.buildPayload(List.of(span));
        @SuppressWarnings("unchecked")
        Map<String, Object> spanObj = (Map<String, Object>) ((List<?>) payload.get("spans")).getFirst();

        assertNull(spanObj.get("parent_id"), "No parent_id should be included when null");

        @SuppressWarnings("unchecked")
        Map<String, Object> spanData = (Map<String, Object>) spanObj.get("span_data");
        assertEquals("function", spanData.get("type"));
        assertEquals("my_tool", spanData.get("tool_name"));
        assertEquals("function", spanData.get("tool_type"));
        assertEquals(Map.of("arg1", "val1"), spanData.get("arguments"));
    }

    @Test
    void openAIExporterEmptySpanListProducesEmptyPayload() {
        // just verify buildPayload handles empty list gracefully
        OpenAISpanExporter exporter = new OpenAISpanExporter("test-key");
        Map<String, Object> payload = exporter.buildPayload(List.of());
        assertEquals("trace", payload.get("object"));
        List<?> spans = (List<?>) payload.get("spans");
        assertTrue(spans.isEmpty());
    }
}
