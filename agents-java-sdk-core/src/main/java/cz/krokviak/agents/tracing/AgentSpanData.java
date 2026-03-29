package cz.krokviak.agents.tracing;

import java.util.List;

public record AgentSpanData(
    String agentName,
    List<String> handoffs,
    List<String> tools,
    String outputType
) {
    public AgentSpanData(String agentName) {
        this(agentName, List.of(), List.of(), null);
    }
}
