package cz.krokviak.agents.cli;

import java.nio.file.Path;

public record CliConfig(
    String apiKey,
    String model,
    String sessionId,
    Path workingDirectory,
    String baseUrl,
    int maxTurns,
    String permissionMode
) {

    public static CliConfig parse(String[] args) {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        String model = "claude-sonnet-4-20250514";
        String sessionId = null;
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        String baseUrl = "https://api.anthropic.com";
        int maxTurns = 50;
        String permissionMode = "default";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--api-key", "-k" -> apiKey = args[++i];
                case "--model", "-m" -> model = args[++i];
                case "--session-id" -> sessionId = args[++i];
                case "--cwd" -> workingDirectory = Path.of(args[++i]);
                case "--base-url" -> baseUrl = args[++i];
                case "--max-turns" -> maxTurns = Integer.parseInt(args[++i]);
                case "--permission-mode" -> permissionMode = args[++i];
                default -> {
                    if (args[i].startsWith("-")) {
                        System.err.println("Unknown option: " + args[i]);
                    }
                }
            }
        }

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Error: ANTHROPIC_API_KEY environment variable or --api-key flag is required.");
            System.exit(1);
        }

        return new CliConfig(apiKey, model, sessionId, workingDirectory, baseUrl, maxTurns, permissionMode);
    }
}
