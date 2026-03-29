package cz.krokviak.agents.runner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

public final class RunState {
    private final List<InputItem> messages;
    private final String lastAgentName;
    private final Map<String, Boolean> toolApprovals;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public RunState(List<InputItem> messages, String lastAgentName) {
        this.messages = new ArrayList<>(messages);
        this.lastAgentName = lastAgentName;
        this.toolApprovals = new HashMap<>();
    }

    public List<InputItem> messages() { return Collections.unmodifiableList(messages); }
    public String lastAgentName() { return lastAgentName; }

    public void approveTool(String toolCallId) {
        toolApprovals.put(toolCallId, true);
    }

    public void rejectTool(String toolCallId) {
        toolApprovals.put(toolCallId, false);
    }

    public boolean isApproved(String toolCallId) {
        return toolApprovals.getOrDefault(toolCallId, false);
    }

    public Map<String, Boolean> toolApprovals() {
        return Collections.unmodifiableMap(toolApprovals);
    }

    public String toJson() {
        try {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("lastAgentName", lastAgentName);
            state.put("toolApprovals", toolApprovals);
            return MAPPER.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize RunState", e);
        }
    }
}
