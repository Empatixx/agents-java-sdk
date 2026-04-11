package cz.krokviak.agents.cli.context;

import cz.krokviak.agents.runner.InputItem;
import java.util.List;

public final class TokenEstimator {

    private volatile int lastApiTokens;
    private volatile int lastCharCount;

    public TokenEstimator() {}

    /**
     * Calibrate the estimator using actual API response data.
     */
    public void calibrate(int apiTokens, int charCount) {
        if (apiTokens > 0 && charCount > 0) {
            this.lastApiTokens = apiTokens;
            this.lastCharCount = charCount;
        }
    }

    /**
     * Estimate tokens using calibrated ratio if available, otherwise chars/4.
     */
    public int estimateCalibrated(List<InputItem> items) {
        int chars = countChars(items);
        if (lastApiTokens > 0 && lastCharCount > 0) {
            return (int) ((long) chars * lastApiTokens / lastCharCount);
        }
        return chars / 4;
    }

    /**
     * Estimate tokens for a string using calibrated ratio if available.
     */
    public int estimateCalibrated(String text) {
        if (text == null) return 0;
        int chars = text.length();
        if (lastApiTokens > 0 && lastCharCount > 0) {
            return (int) ((long) chars * lastApiTokens / lastCharCount);
        }
        return chars / 4;
    }

    // --- Static methods for backward compatibility ---

    public static int estimate(List<InputItem> items) {
        return countChars(items) / 4;
    }

    public static int estimate(String text) {
        return text != null ? text.length() / 4 : 0;
    }

    public static int countChars(List<InputItem> items) {
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
                case InputItem.CompactionMarker m -> m.summary() != null ? m.summary().length() : 0;
            };
        }
        return chars;
    }
}
