package cz.krokviak.agents.cli.test;

import cz.krokviak.agents.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Deterministic model for testing. Enqueue responses, they are returned in order.
 */
public class FakeModel implements Model {

    private final Queue<ModelResponse> responses = new ConcurrentLinkedQueue<>();
    private final List<LlmContext> callLog = new ArrayList<>();

    public void enqueue(String text) {
        enqueue(text, 100, 50);
    }

    public void enqueue(String text, int inputTokens, int outputTokens) {
        responses.add(new ModelResponse("fake-" + responses.size(),
            List.of(new ModelResponse.OutputItem.Message(text)),
            new Usage(inputTokens, outputTokens)));
    }

    public void enqueueToolCall(String id, String name, java.util.Map<String, Object> args) {
        responses.add(new ModelResponse("fake-" + responses.size(),
            List.of(new ModelResponse.OutputItem.ToolCallRequest(id, name, args)),
            new Usage(100, 50)));
    }

    public List<LlmContext> callLog() { return callLog; }

    @Override
    public ModelResponse call(LlmContext context, ModelSettings settings) {
        callLog.add(context);
        ModelResponse resp = responses.poll();
        if (resp == null) {
            return new ModelResponse("fake-empty", List.of(new ModelResponse.OutputItem.Message("(no more responses)")),
                new Usage(10, 10));
        }
        return resp;
    }

    @Override
    public ModelResponseStream callStreamed(LlmContext context, ModelSettings settings) {
        ModelResponse resp = call(context, settings);
        return new FakeModelResponseStream(resp);
    }

    private record FakeModelResponseStream(ModelResponse response) implements ModelResponseStream {
        @Override
        public java.util.Iterator<Event> iterator() {
            List<Event> events = new ArrayList<>();
            for (var item : response.output()) {
                switch (item) {
                    case ModelResponse.OutputItem.Message msg ->
                        events.add(new Event.TextDelta(msg.content()));
                    case ModelResponse.OutputItem.ToolCallRequest tc ->
                        events.add(new Event.ToolCallDelta(tc.id(), tc.name(),
                            new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().toString()));
                }
            }
            events.add(new Event.Done(response));
            return events.iterator();
        }

        @Override
        public void close() {}
    }
}
