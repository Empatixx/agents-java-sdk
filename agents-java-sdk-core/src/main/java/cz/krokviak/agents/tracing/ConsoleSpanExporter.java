package cz.krokviak.agents.tracing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

public class ConsoleSpanExporter implements SpanExporter {
    private static final Logger log = LoggerFactory.getLogger(ConsoleSpanExporter.class);

    @Override
    public void export(List<SpanData> spans) {
        for (SpanData span : spans) {
            log.info("[TRACE] {} ({}ms) {}", span.name(), span.durationMs(), span.attributes());
        }
    }
}
