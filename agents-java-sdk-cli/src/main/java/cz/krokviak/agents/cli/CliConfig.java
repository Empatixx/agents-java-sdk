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
        int maxTurns = 50;
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
                        System.err.println("Unknown option: " + args[i]);
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
                case ANTHROPIC -> "claude-sonnet-4-20250514";
                case OPENAI -> "gpt-5.4-2026-03-05";
            };
        }

        // Default base URL per provider
        if (baseUrl == null) {
            baseUrl = switch (provider) {
                case ANTHROPIC -> "https://api.anthropic.com";
                case OPENAI -> "https://api.openai.com/v1";
            };
        }

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Error: Set ANTHROPIC_API_KEY or OPENAI_API_KEY environment variable, or use --api-key flag.");
            System.exit(1);
        }

        return new CliConfig(apiKey, model, sessionId, workingDirectory, baseUrl, maxTurns, permissionMode, tui, provider);
    }
}
