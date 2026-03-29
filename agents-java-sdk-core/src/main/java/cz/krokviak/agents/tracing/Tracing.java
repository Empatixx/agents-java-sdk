package cz.krokviak.agents.tracing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

public final class Tracing {
    private static volatile TraceConfig config;
    private static final ThreadLocal<Deque<Span>> spanStack = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<String> currentTraceId = new ThreadLocal<>();
    private static final List<SpanData> pendingSpans = new ArrayList<>();

    private Tracing() {}

    static void install(TraceConfig cfg) {
        config = cfg;
    }

    public static Span span(String name) {
        if (config == null || !config.enabled()) {
            return new Span(name, null, null, false);
        }

        String traceId = currentTraceId.get();
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
            currentTraceId.set(traceId);
        }

        Deque<Span> stack = spanStack.get();
        Span parent = stack.peek();
        String parentId = parent != null ? parent.spanId() : null;
        Span span = new Span(name, parentId, traceId, true);
        stack.push(span);
        return span;
    }

    static synchronized void endSpan(Span span, SpanData data) {
        pendingSpans.add(data);
        // Pop this span from the stack
        Deque<Span> stack = spanStack.get();
        if (!stack.isEmpty() && stack.peek() == span) {
            stack.pop();
        }
        // If stack is now empty, clear the trace ID so next top-level span gets a fresh one
        if (stack.isEmpty()) {
            currentTraceId.remove();
        }
    }

    public static synchronized void flush() {
        if (config == null || pendingSpans.isEmpty()) return;
        var toExport = List.copyOf(pendingSpans);
        pendingSpans.clear();
        for (SpanExporter exporter : config.exporters()) {
            exporter.export(toExport);
        }
    }

    public static void reset() {
        config = null;
        spanStack.remove();
        currentTraceId.remove();
        synchronized (Tracing.class) {
            pendingSpans.clear();
        }
    }
}
