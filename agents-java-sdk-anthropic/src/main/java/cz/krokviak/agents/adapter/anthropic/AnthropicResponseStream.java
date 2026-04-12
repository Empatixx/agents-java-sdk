package cz.krokviak.agents.adapter.anthropic;
import cz.krokviak.agents.model.Usage;
import cz.krokviak.agents.model.ModelResponseStream;
import cz.krokviak.agents.model.ModelResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.krokviak.agents.exception.ContextTooLongException;
import cz.krokviak.agents.http.SseParser;
import cz.krokviak.agents.adapter.anthropic.dto.AnthropicDto;

import java.util.*;

final class AnthropicResponseStream implements ModelResponseStream {
    private final Iterator<SseParser.SseEvent> sseIterator;
    private final ObjectMapper mapper;

    AnthropicResponseStream(Iterator<SseParser.SseEvent> sseIterator, ObjectMapper mapper) {
        this.sseIterator = sseIterator;
        this.mapper = mapper;
    }

    @Override
    public Iterator<Event> iterator() {
        return new EventIterator();
    }

    @Override
    public void close() {}

    private class EventIterator implements Iterator<Event> {
        private final Queue<Event> pending = new ArrayDeque<>();
        private boolean done = false;

        // Accumulated state
        private String messageId;
        private int inputTokens;
        private int outputTokens;
        private final StringBuilder accumulatedText = new StringBuilder();
        private final Map<Integer, String> toolCallIds = new HashMap<>();
        private final Map<Integer, String> toolCallNames = new HashMap<>();
        private final Map<Integer, StringBuilder> toolCallArgs = new HashMap<>();

        @Override
        public boolean hasNext() {
            if (!pending.isEmpty()) return true;
            if (done) return false;
            advance();
            return !pending.isEmpty();
        }

        @Override
        public Event next() {
            if (!hasNext()) throw new NoSuchElementException();
            return pending.poll();
        }

        private void advance() {
            while (pending.isEmpty() && sseIterator.hasNext()) {
                SseParser.SseEvent sse = sseIterator.next();
                String eventType = sse.event();
                String data = sse.data();

                if (eventType == null || data == null) continue;

                try {
                    switch (eventType) {
                        case "message_start" -> {
                            AnthropicDto.StreamMessageStart msg = mapper.readValue(data, AnthropicDto.StreamMessageStart.class);
                            if (msg.message() != null) {
                                messageId = msg.message().id();
                                if (msg.message().usage() != null) {
                                    inputTokens = msg.message().usage().inputTokens();
                                }
                            }
                        }
                        case "content_block_start" -> {
                            AnthropicDto.StreamContentBlockStart cbs = mapper.readValue(data, AnthropicDto.StreamContentBlockStart.class);
                            if (cbs.contentBlock() instanceof AnthropicDto.ContentBlock.ToolUseBlock tub) {
                                toolCallIds.put(cbs.index(), tub.id());
                                toolCallNames.put(cbs.index(), tub.name());
                                toolCallArgs.put(cbs.index(), new StringBuilder());
                            }
                        }
                        case "content_block_delta" -> {
                            AnthropicDto.StreamContentBlockDelta cbd = mapper.readValue(data, AnthropicDto.StreamContentBlockDelta.class);
                            if (cbd.delta() != null) {
                                if ("text_delta".equals(cbd.delta().type()) && cbd.delta().text() != null) {
                                    accumulatedText.append(cbd.delta().text());
                                    pending.add(new Event.TextDelta(cbd.delta().text()));
                                } else if ("thinking_delta".equals(cbd.delta().type())
                                        && cbd.delta().thinking() != null) {
                                    pending.add(new Event.ThinkingDelta(cbd.delta().thinking()));
                                } else if ("input_json_delta".equals(cbd.delta().type()) && cbd.delta().partialJson() != null) {
                                    int idx = cbd.index();
                                    StringBuilder sb = toolCallArgs.get(idx);
                                    if (sb != null) {
                                        sb.append(cbd.delta().partialJson());
                                    }
                                    pending.add(new Event.ToolCallDelta(
                                        toolCallIds.get(idx),
                                        toolCallNames.get(idx),
                                        cbd.delta().partialJson()
                                    ));
                                }
                            }
                        }
                        case "content_block_stop" -> {
                            // nothing special needed
                        }
                        case "message_delta" -> {
                            AnthropicDto.StreamMessageDelta md = mapper.readValue(data, AnthropicDto.StreamMessageDelta.class);
                            if (md.usage() != null) {
                                outputTokens = md.usage().outputTokens();
                            }
                        }
                        case "message_stop" -> {
                            done = true;
                            pending.add(new Event.Done(buildFullResponse()));
                        }
                        case "error" -> {
                            String lower = data.toLowerCase();
                            if (lower.contains("prompt is too long") || lower.contains("prompt_too_long")
                                || lower.contains("context length exceeded")) {
                                throw new ContextTooLongException("Prompt is too long: " + data);
                            }
                        }
                        default -> {} // ping, etc.
                    }
                } catch (Exception e) {
                    // skip unparseable events
                }
            }
            if (pending.isEmpty() && !sseIterator.hasNext() && !done) {
                done = true;
                pending.add(new Event.Done(buildFullResponse()));
            }
        }

        @SuppressWarnings("unchecked")
        private ModelResponse buildFullResponse() {
            List<ModelResponse.OutputItem> outputs = new ArrayList<>();

            if (!accumulatedText.isEmpty()) {
                outputs.add(new ModelResponse.OutputItem.Message(accumulatedText.toString()));
            }

            for (Map.Entry<Integer, String> entry : toolCallIds.entrySet()) {
                int idx = entry.getKey();
                String id = entry.getValue();
                String name = toolCallNames.get(idx);
                StringBuilder argsSb = toolCallArgs.get(idx);
                Map<String, Object> args;
                try {
                    args = mapper.readValue(argsSb.toString(), Map.class);
                } catch (Exception e) {
                    args = Map.of();
                }
                outputs.add(new ModelResponse.OutputItem.ToolCallRequest(id, name, args));
            }

            return new ModelResponse(messageId, outputs, new Usage(inputTokens, outputTokens));
        }
    }
}
