package cz.krokviak.agents.cli;

import java.nio.file.Path;

public record CliConfig(
    String apiKey,
    String model,
    String sessionId,
    Path workingDirectory,
    String baseUrl,
    int maxTurns,
    String permissionMode,
    boolean tui,
    Provider provider
) {

    public enum Provider { ANTHROPIC, OPENAI }

    public static CliConfig parse(String[] args) {
        String apiKey = null;
        String model = null;
        String sessionId = null;
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        String baseUrl = null;
        int maxTurns = CliDefaults.MAX_TURNS;
        String permissionMode = "default";
        boolean tui = false;
        Provider provider = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--api-key", "-k" -> apiKey = args[++i];
                case "--model", "-m" -> model = args[++i];
                case "--session-id" -> sessionId = args[++i];
                case "--cwd" -> workingDirectory = Path.of(args[++i]);
                case "--base-url" -> baseUrl = args[++i];
                case "--max-turns" -> maxTurns = Integer.parseInt(args[++i]);
                case "--permission-mode" -> permissionMode = args[++i];
                case "--tui" -> tui = true;
                case "--provider" -> provider = Provider.valueOf(args[++i].toUpperCase());
                default -> {
                    if (args[i].startsWith("-")) {
                        // unknown option — silently ignore
                    }
                }
            }
        }

        // Auto-detect provider from env vars if not explicitly set
        if (provider == null && apiKey == null) {
            String anthropicKey = System.getenv("ANTHROPIC_API_KEY");
            String openaiKey = System.getenv("OPENAI_API_KEY");
            if (anthropicKey != null && !anthropicKey.isBlank()) {
                provider = Provider.ANTHROPIC;
                apiKey = anthropicKey;
            } else if (openaiKey != null && !openaiKey.isBlank()) {
                provider = Provider.OPENAI;
                apiKey = openaiKey;
            }
        } else if (apiKey == null) {
            // Provider set explicitly, pick matching env var
            apiKey = switch (provider) {
                case ANTHROPIC -> System.getenv("ANTHROPIC_API_KEY");
                case OPENAI -> System.getenv("OPENAI_API_KEY");
            };
        }

        // Default provider if still null
        if (provider == null) {
            provider = Provider.ANTHROPIC;
        }

        // Default model per provider
        if (model == null) {
            model = switch (provider) {
                case ANTHROPIC -> CliDefaults.DEFAULT_ANTHROPIC_MODEL;
                case OPENAI -> CliDefaults.DEFAULT_OPENAI_MODEL;
            };
        }

        // Default base URL per provider
        if (baseUrl == null) {
            baseUrl = switch (provider) {
                case ANTHROPIC -> CliDefaults.DEFAULT_ANTHROPIC_BASE_URL;
                case OPENAI -> CliDefaults.DEFAULT_OPENAI_BASE_URL;
            };
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(
                "Set ANTHROPIC_API_KEY or OPENAI_API_KEY environment variable, or use --api-key flag.");
        }

        return new CliConfig(apiKey, model, sessionId, workingDirectory, baseUrl, maxTurns, permissionMode, tui, provider);
    }
}
