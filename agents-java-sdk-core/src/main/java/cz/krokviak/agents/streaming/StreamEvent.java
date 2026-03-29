package cz.krokviak.agents.streaming;

import cz.krokviak.agents.agent.Agent;
import cz.krokviak.agents.runner.RunItem;
import cz.krokviak.agents.runner.RunResult;
import cz.krokviak.agents.tool.ToolOutput;
import java.util.Map;

public sealed interface StreamEvent<T>
    permits StreamEvent.TextDeltaEvent,
            StreamEvent.RunItemEvent,
            StreamEvent.ToolCallEvent,
            StreamEvent.ToolResultEvent,
            StreamEvent.AgentUpdatedEvent,
            StreamEvent.CompletedEvent {

    record TextDeltaEvent<T>(String delta) implements StreamEvent<T> {}
    record RunItemEvent<T>(RunItem item) implements StreamEvent<T> {}
    record ToolCallEvent<T>(String toolName, Map<String, Object> args) implements StreamEvent<T> {}
    record ToolResultEvent<T>(String toolName, ToolOutput result) implements StreamEvent<T> {}
    record AgentUpdatedEvent<T>(Agent<T> agent) implements StreamEvent<T> {}
    record CompletedEvent<T>(RunResult<T> result) implements StreamEvent<T> {}
}
