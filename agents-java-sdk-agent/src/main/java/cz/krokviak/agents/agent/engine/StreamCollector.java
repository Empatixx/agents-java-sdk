package cz.krokviak.agents.agent.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.krokviak.agents.model.ModelResponse;
import cz.krokviak.agents.runner.InputItem;

import java.util.*;

public class StreamCollector {
    private static final Logger log = LoggerFactory.getLogger(StreamCollector.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StringBuilder textAccumulator = new StringBuilder();
    private final Map<String, String> toolCallNames = new LinkedHashMap<>();
    private final Map<String, StringBuilder> toolCallArgs = new LinkedHashMap<>();
    private ModelResponse response;

    public void onTextDelta(String delta) {
        textAccumulator.append(delta);
    }

    public void onToolCallDelta(String id, String name, String argsDelta) {
        toolCallNames.putIfAbsent(id, name);
        toolCallArgs.computeIfAbsent(id, _ -> new StringBuilder()).append(argsDelta);
    }

    public void onDone(ModelResponse response) {
        this.response = response;
    }

    public String text() {
        return textAccumulator.isEmpty() ? null : textAccumulator.toString();
    }

    public List<InputItem.ToolCall> toolCalls() {
        List<InputItem.ToolCall> result = new ArrayList<>();
        for (var entry : toolCallNames.entrySet()) {
            String id = entry.getKey();
            String name = entry.getValue();
            Map<String, Object> arguments = Map.of();
            StringBuilder argsJson = toolCallArgs.get(id);
            if (argsJson != null && !argsJson.isEmpty()) {
                try {
                    arguments = objectMapper.readValue(argsJson.toString(),
                        new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    log.warn(
                        "Failed to parse tool arguments for " + name + ": " + e.getMessage());
                }
            }
            result.add(new InputItem.ToolCall(id, name, arguments));
        }
        return result;
    }

    public boolean hasToolCalls() {
        return !toolCallNames.isEmpty();
    }

    public ModelResponse response() {
        return response;
    }
}
