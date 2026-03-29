package cz.krokviak.agents.http;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SseParserTest {

    @Test
    void parsesSimpleEvents() {
        String sse = """
            data: {"type": "text", "value": "Hello"}

            data: {"type": "text", "value": "World"}

            """;

        var events = SseParser.parse(new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)));
        assertEquals(2, events.size());
        assertTrue(events.get(0).data().contains("Hello"));
        assertTrue(events.get(1).data().contains("World"));
    }

    @Test
    void parsesEventWithType() {
        String sse = """
            event: message
            data: {"content": "test"}

            """;

        var events = SseParser.parse(new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, events.size());
        assertEquals("message", events.getFirst().event());
    }

    @Test
    void parsesMultiLineData() {
        String sse = """
            data: line1
            data: line2

            """;

        var events = SseParser.parse(new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, events.size());
        assertEquals("line1\nline2", events.getFirst().data());
    }

    @Test
    void ignoresComments() {
        String sse = """
            : this is a comment
            data: actual data

            """;

        var events = SseParser.parse(new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, events.size());
    }

    @Test
    void handlesDoneEvent() {
        String sse = """
            data: {"text": "hi"}

            data: [DONE]

            """;

        var events = SseParser.parse(new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)));
        assertEquals(2, events.size());
        assertTrue(events.get(1).isDone());
    }
}
