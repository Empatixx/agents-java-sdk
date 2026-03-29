package cz.krokviak.agents.http;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class SseParser {

    public record SseEvent(String event, String data) {
        public boolean isDone() {
            return "[DONE]".equals(data);
        }
    }

    private SseParser() {}

    public static List<SseEvent> parse(InputStream inputStream) {
        List<SseEvent> events = new ArrayList<>();
        try (var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String eventType = null;
            StringBuilder data = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (!data.isEmpty()) {
                        events.add(new SseEvent(eventType, data.toString()));
                        eventType = null;
                        data = new StringBuilder();
                    }
                    continue;
                }
                if (line.startsWith(":")) continue; // comment
                if (line.startsWith("event:")) {
                    eventType = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    if (!data.isEmpty()) data.append("\n");
                    data.append(line.substring(5).trim());
                }
            }
            if (!data.isEmpty()) {
                events.add(new SseEvent(eventType, data.toString()));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse SSE stream", e);
        }
        return events;
    }

    public static Iterator<SseEvent> stream(InputStream inputStream) {
        var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        return new Iterator<>() {
            private SseEvent next = null;
            private boolean done = false;

            @Override
            public boolean hasNext() {
                if (done) return false;
                if (next != null) return true;
                next = readNext();
                return next != null;
            }

            @Override
            public SseEvent next() {
                if (!hasNext()) throw new java.util.NoSuchElementException();
                var event = next;
                next = null;
                return event;
            }

            private SseEvent readNext() {
                try {
                    String eventType = null;
                    StringBuilder data = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty()) {
                            if (!data.isEmpty()) {
                                return new SseEvent(eventType, data.toString());
                            }
                            continue;
                        }
                        if (line.startsWith(":")) continue;
                        if (line.startsWith("event:")) {
                            eventType = line.substring(6).trim();
                        } else if (line.startsWith("data:")) {
                            if (!data.isEmpty()) data.append("\n");
                            data.append(line.substring(5).trim());
                        }
                    }
                    done = true;
                    if (!data.isEmpty()) {
                        return new SseEvent(eventType, data.toString());
                    }
                    return null;
                } catch (Exception e) {
                    done = true;
                    return null;
                }
            }
        };
    }
}
