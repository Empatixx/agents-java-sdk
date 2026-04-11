package cz.krokviak.agents.cli.context;

import cz.krokviak.agents.runner.InputItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TokenEstimatorTest {

    @Test
    void staticEstimateCharsDiv4() {
        var items = List.<InputItem>of(new InputItem.UserMessage("x".repeat(400)));
        assertEquals(100, TokenEstimator.estimate(items));
    }

    @Test
    void staticEstimateString() {
        assertEquals(25, TokenEstimator.estimate("x".repeat(100)));
    }

    @Test
    void staticEstimateNull() {
        assertEquals(0, TokenEstimator.estimate((String) null));
    }

    @Test
    void calibratedUsesRatio() {
        var estimator = new TokenEstimator();
        // API says 1000 chars = 300 tokens
        estimator.calibrate(300, 1000);

        // 2000 chars should estimate to ~600 tokens
        var items = List.<InputItem>of(new InputItem.UserMessage("x".repeat(2000)));
        int est = estimator.estimateCalibrated(items);
        assertEquals(600, est);
    }

    @Test
    void calibratedFallsBackWithoutCalibration() {
        var estimator = new TokenEstimator();
        var items = List.<InputItem>of(new InputItem.UserMessage("x".repeat(400)));
        // No calibration → chars/4
        assertEquals(100, estimator.estimateCalibrated(items));
    }

    @Test
    void calibratedStringEstimate() {
        var estimator = new TokenEstimator();
        estimator.calibrate(500, 2000);
        assertEquals(250, estimator.estimateCalibrated("x".repeat(1000)));
    }

    @Test
    void countCharsIncludesToolCalls() {
        var items = List.<InputItem>of(
            new InputItem.UserMessage("hello"),
            new InputItem.AssistantMessage("reply", List.of(
                new InputItem.ToolCall("tc-1", "bash", java.util.Map.of("command", "ls"))
            )),
            new InputItem.ToolResult("tc-1", "bash", "file1\nfile2")
        );
        int chars = TokenEstimator.countChars(items);
        assertTrue(chars > 20);
    }

    @Test
    void recalibrationUpdatesRatio() {
        var estimator = new TokenEstimator();
        estimator.calibrate(100, 400); // ratio 0.25
        estimator.calibrate(200, 400); // ratio 0.5

        assertEquals(500, estimator.estimateCalibrated("x".repeat(1000)));
    }
}
