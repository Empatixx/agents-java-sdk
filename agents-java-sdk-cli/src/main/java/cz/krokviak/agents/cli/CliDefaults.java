package cz.krokviak.agents.cli;

/**
 * Centralized defaults for the CLI. All magic numbers in one place.
 */
public final class CliDefaults {
    private CliDefaults() {}

    // --- Agent execution ---
    public static final int MAX_TURNS = 50;
    public static final int TOKEN_BUDGET = 200_000;
    public static final int MODEL_MAX_TOKENS = 16_384;

    // --- Compaction thresholds (tokens) ---
    public static final int COMPACTION_LAYER1_THRESHOLD = 40_000;
    public static final int COMPACTION_LAYER2_THRESHOLD = 60_000;
    public static final int COMPACTION_LAYER3_THRESHOLD = 80_000;
    public static final int COMPACTION_TARGET_TOKENS = 40_000;
    public static final int COMPACTION_SUMMARY_MAX_TOKENS = 2_048;

    // --- Tool output limits ---
    public static final int BASH_MAX_OUTPUT_CHARS = 2_000;
    public static final int BASH_TIMEOUT_MS = 120_000;
    public static final int READ_DEFAULT_LINES = 200;
    public static final int READ_MAX_OUTPUT_CHARS = 10_000;
    public static final long READ_MAX_FILE_SIZE = 256 * 1024;
    public static final int GREP_RESULT_LIMIT = 500;
    public static final int TOOL_EXECUTION_TIMEOUT_SECONDS = 120;

    // --- Micro compactor ---
    public static final int MICRO_COMPACTOR_KEEP_RECENT = 10;
    public static final int SNIP_COMPACTOR_THRESHOLD = 60_000;

    // --- Default models ---
    public static final String DEFAULT_ANTHROPIC_MODEL = "claude-sonnet-4-20250514";
    public static final String DEFAULT_OPENAI_MODEL = "gpt-5.4-2026-03-05";
    public static final String DEFAULT_ANTHROPIC_BASE_URL = "https://api.anthropic.com";
    public static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1";
}
