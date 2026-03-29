package cz.krokviak.agents.tracing;

import java.util.ArrayList;
import java.util.List;

public record TraceConfig(boolean enabled, List<SpanExporter> exporters) {

    public void install() {
        Tracing.install(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean enabled = true;
        private final List<SpanExporter> exporters = new ArrayList<>();

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder addExporter(SpanExporter exporter) {
            this.exporters.add(exporter);
            return this;
        }

        public TraceConfig build() {
            return new TraceConfig(enabled, List.copyOf(exporters));
        }
    }
}
