package cz.krokviak.agents.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CliDefaultsTest {

    @Test
    void compactionThresholdsAreOrdered() {
        assertTrue(CliDefaults.COMPACTION_LAYER1_THRESHOLD < CliDefaults.COMPACTION_LAYER2_THRESHOLD);
        assertTrue(CliDefaults.COMPACTION_LAYER2_THRESHOLD < CliDefaults.COMPACTION_LAYER3_THRESHOLD);
        assertTrue(CliDefaults.COMPACTION_LAYER3_THRESHOLD < CliDefaults.TOKEN_BUDGET);
    }

    @Test
    void targetTokensBelowMaxThreshold() {
        assertTrue(CliDefaults.COMPACTION_TARGET_TOKENS <= CliDefaults.COMPACTION_LAYER3_THRESHOLD);
    }

    @Test
    void toolLimitsArePositive() {
        assertTrue(CliDefaults.BASH_MAX_OUTPUT_CHARS > 0);
        assertTrue(CliDefaults.READ_DEFAULT_LINES > 0);
        assertTrue(CliDefaults.READ_MAX_OUTPUT_CHARS > 0);
        assertTrue(CliDefaults.READ_MAX_FILE_SIZE > 0);
        assertTrue(CliDefaults.GREP_RESULT_LIMIT > 0);
        assertTrue(CliDefaults.TOOL_EXECUTION_TIMEOUT_SECONDS > 0);
    }

    @Test
    void defaultModelsAreNotBlank() {
        assertFalse(CliDefaults.DEFAULT_ANTHROPIC_MODEL.isBlank());
        assertFalse(CliDefaults.DEFAULT_OPENAI_MODEL.isBlank());
        assertFalse(CliDefaults.DEFAULT_ANTHROPIC_BASE_URL.isBlank());
        assertFalse(CliDefaults.DEFAULT_OPENAI_BASE_URL.isBlank());
    }
}
