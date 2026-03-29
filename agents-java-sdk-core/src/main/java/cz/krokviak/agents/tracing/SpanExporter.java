package cz.krokviak.agents.tracing;

import java.util.List;

@FunctionalInterface
public interface SpanExporter {
    void export(List<SpanData> spans);
}
