package cz.krokviak.agents.runner;

import cz.krokviak.agents.agent.Agent;
import cz.krokviak.agents.guardrail.GuardrailResults;
import cz.krokviak.agents.model.Usage;
import java.util.ArrayList;
import java.util.List;

public record RunResult<T>(
    T finalOutput,
    List<RunItem> newItems,
    Agent<T> lastAgent,
    List<InputItem> input,
    Usage usage,
    List<Interruption> interruptions,
    GuardrailResults guardrailResults
) {
    public List<InputItem> toInputList() {
        var result = new ArrayList<>(input);
        for (RunItem item : newItems) {
            result.add(switch (item) {
                case RunItem.UserInput msg -> new InputItem.UserMessage(msg.content());
                case RunItem.MessageOutput msg -> new InputItem.AssistantMessage(msg.content());
                case RunItem.ToolCallItem call -> new InputItem.AssistantMessage("",
                    List.of(new InputItem.ToolCall(call.toolCallId(), call.toolName(), call.arguments())));
                case RunItem.ToolOutputItem out -> new InputItem.ToolResult(out.toolCallId(), out.toolName(),
                    out.output() instanceof cz.krokviak.agents.tool.ToolOutput.Text t ? t.content() : "[non-text]");
                case RunItem.HandoffItem h -> new InputItem.AssistantMessage(
                    "[Handoff: " + h.fromAgent() + " -> " + h.toAgent() + "]");
            });
        }
        return result;
    }
}
