package cz.krokviak.agents.agent;

/**
 * Defaults used by the engine layer. Frontends may override via config,
 * but these are the values that make the agent run reasonably.
 */
public final class AgentDefaults {
    private AgentDefaults() {}

    public static final int MAX_TURNS = 50;
    public static final int TOKEN_BUDGET = 200_000;
    public static final int MODEL_MAX_TOKENS = 16_384;

    public static final int COMPACTION_LAYER1_THRESHOLD = 40_000;
    public static final int COMPACTION_LAYER2_THRESHOLD = 60_000;
    public static final int COMPACTION_LAYER3_THRESHOLD = 80_000;
    public static final int COMPACTION_TARGET_TOKENS = 40_000;
    public static final int COMPACTION_SUMMARY_MAX_TOKENS = 2_048;

    public static final int MICRO_COMPACTOR_KEEP_RECENT = 10;
    public static final int SNIP_COMPACTOR_THRESHOLD = 60_000;
    public static final int TOOL_EXECUTION_TIMEOUT_SECONDS = 120;

    /** Soft ceiling on in-memory history size; beyond this we trim oldest items to cap at this value. */
    public static final int HISTORY_HARD_CAP = 5_000;
    /** Threshold at which we emit a one-time warning about unusually large history. */
    public static final int HISTORY_WARN_THRESHOLD = 2_500;
}
