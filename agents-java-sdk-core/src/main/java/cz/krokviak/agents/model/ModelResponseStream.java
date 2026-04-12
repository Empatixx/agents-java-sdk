package cz.krokviak.agents.model;

import java.util.Iterator;

public interface ModelResponseStream extends Iterable<ModelResponseStream.Event>, AutoCloseable {

    sealed interface Event permits Event.TextDelta, Event.ToolCallDelta, Event.ThinkingDelta, Event.Done {
        record TextDelta(String delta) implements Event {}
        record ToolCallDelta(String toolCallId, String name, String argumentsDelta) implements Event {}
        record ThinkingDelta(String delta) implements Event {}
        record Done(ModelResponse fullResponse) implements Event {}
    }

    @Override
    Iterator<Event> iterator();

    @Override
    void close();
}
