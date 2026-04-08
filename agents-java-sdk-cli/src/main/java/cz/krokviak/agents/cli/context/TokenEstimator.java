package cz.krokviak.agents.cli.context;

import cz.krokviak.agents.runner.InputItem;
import java.util.List;

public final class TokenEstimator {
    private TokenEstimator() {}

    public static int estimate(List<InputItem> items) {
        int chars = 0;
        for (InputItem item : items) {
            chars += switch (item) {
                case InputItem.UserMessage m -> m.content() != null ? m.content().length() : 0;
                case InputItem.AssistantMessage m -> {
                    int c = m.content() != null ? m.content().length() : 0;
                    for (var tc : m.toolCalls()) {
                        c += tc.name().length() + tc.arguments().toString().length();
                    }
                    yield c;
                }
                case InputItem.ToolResult m -> m.output() != null ? m.output().length() : 0;
                case InputItem.SystemMessage m -> m.content() != null ? m.content().length() : 0;
            };
        }
        return chars / 4; // rough estimate
    }

    public static int estimate(String text) {
        return text != null ? text.length() / 4 : 0;
    }
}
