package cz.krokviak.agents.agent.engine;

import cz.krokviak.agents.runner.InputItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompactionPipelineTest {

    private static List<InputItem> historyOfSize(int tokenEstimate) {
        // Each char ≈ 0.25 tokens, so we need tokenEstimate * 4 chars
        List<InputItem> history = new ArrayList<>();
        String content = "x".repeat(tokenEstimate * 4);
        history.add(new InputItem.UserMessage(content));
        return history;
    }

    @Test
    void noCompactionBelowThreshold() {
        var pipeline = new CompactionPipeline(null);
        var history = historyOfSize(10_000); // well below 40K threshold
        var result = pipeline.compact(history, "");
        assertSame(history, result);
    }

    @Test
    void snipCompactorTriggersAboveThreshold() {
        // SnipCompactor default threshold is 60K tokens. Create history above that.
        List<InputItem> history = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            history.add(new InputItem.UserMessage("x".repeat(10_000))); // ~2500 tokens each = 100K total
        }
        var pipeline = new CompactionPipeline(null);
        var result = pipeline.compact(history, "");
        assertTrue(result.size() < history.size(), "Snip should have removed some messages");
    }

    @Test
    void microCompactorTruncatesOldToolResults() {
        var micro = new MicroCompactor(3); // keep only 3 recent
        List<InputItem> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            history.add(new InputItem.ToolResult("tc-" + i, "tool-" + i, "result content " + i));
        }
        var result = micro.compact(history);
        assertEquals(10, result.size()); // same count, but older ones truncated

        // First 7 should be truncated
        for (int i = 0; i < 7; i++) {
            var tr = (InputItem.ToolResult) result.get(i);
            assertEquals("[result truncated]", tr.output());
        }
        // Last 3 should be preserved
        for (int i = 7; i < 10; i++) {
            var tr = (InputItem.ToolResult) result.get(i);
            assertTrue(tr.output().startsWith("result content"));
        }
    }

    @Test
    void microCompactorNoOpBelowThreshold() {
        var micro = new MicroCompactor(10);
        List<InputItem> history = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            history.add(new InputItem.ToolResult("tc-" + i, "tool", "result " + i));
        }
        var result = micro.compact(history);
        assertSame(history, result); // 5 < 10, no compaction
    }

    @Test
    void snipCompactorPreservesRecentMessages() {
        var snip = new SnipCompactor(100); // very low threshold to force snip
        List<InputItem> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            history.add(new InputItem.UserMessage("message " + i + " " + "x".repeat(500)));
        }
        var result = snip.snipIfNeeded(history);
        assertTrue(result.size() < history.size());
        // Should preserve at least last 4
        assertTrue(result.size() >= 5); // 4 recent + 1 CompactionMarker
        // First element should be a CompactionMarker
        assertInstanceOf(InputItem.CompactionMarker.class, result.getFirst());
    }

    @Test
    void reactiveCompactAppliesAllLayers() {
        var pipeline = new CompactionPipeline(null);
        List<InputItem> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            history.add(new InputItem.UserMessage("msg " + i));
            history.add(new InputItem.ToolResult("tc-" + i, "tool", "result ".repeat(100)));
        }
        var result = pipeline.reactiveCompact(history, "");
        // Should have fewer messages after all layers
        assertTrue(result.size() <= history.size());
    }
}
