package cz.krokviak.agents.guardrail;

import cz.krokviak.agents.runner.InputItem;
import java.util.List;

public record GuardrailInputData(List<InputItem> items) {
    public String text() {
        StringBuilder sb = new StringBuilder();
        for (InputItem item : items) {
            if (item instanceof InputItem.UserMessage msg) {
                sb.append(msg.content());
            }
        }
        return sb.toString();
    }
}
