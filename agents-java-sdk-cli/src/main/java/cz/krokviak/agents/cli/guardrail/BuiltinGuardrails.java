package cz.krokviak.agents.cli.guardrail;

import java.util.Map;
import java.util.Set;

public final class BuiltinGuardrails {
    private BuiltinGuardrails() {}

    public static boolean checkInputSafety(String userInput) {
        if (userInput == null) return true;
        String lower = userInput.toLowerCase();
        return !lower.contains("ignore previous instructions") &&
               !lower.contains("disregard your instructions") &&
               !lower.contains("you are now");
    }

    public static boolean checkToolArgSafety(String toolName, Map<String, Object> args) {
        if ("bash".equals(toolName)) {
            Object cmd = args.get("command");
            if (cmd instanceof String command) return !containsDangerous(command);
        }
        if ("write_file".equals(toolName) || "edit_file".equals(toolName)) {
            Object path = args.get("file_path");
            if (path instanceof String filePath) return !isSensitivePath(filePath);
        }
        return true;
    }

    private static final Set<String> DANGEROUS = Set.of(
        "rm -rf /", "rm -rf ~", "mkfs.", "dd if=/dev/zero",
        ":(){:|:&};:", "chmod -R 777 /", "wget|sh", "curl|bash");

    private static boolean containsDangerous(String command) {
        String lower = command.toLowerCase().trim();
        return DANGEROUS.stream().anyMatch(lower::contains);
    }

    private static final Set<String> SENSITIVE = Set.of(
        ".env", ".env.local", "credentials.json", "secrets.yaml",
        "id_rsa", "id_ed25519", ".ssh/config", ".aws/credentials");

    private static boolean isSensitivePath(String path) {
        String lower = path.toLowerCase();
        return SENSITIVE.stream().anyMatch(s -> lower.endsWith(s) || lower.contains("/" + s));
    }
}
