package cz.krokviak.agents.cli.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.krokviak.agents.agent.hook.Hook;
import cz.krokviak.agents.agent.hook.ToolUseEvent;
import cz.krokviak.agents.api.hook.HookPhase;
import cz.krokviak.agents.api.hook.HookResult;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A shell-command hook loaded from a plugin's {@code hooks.json}.
 *
 * <p>Matches on a {@code ToolName(arg_pattern)} expression and, when a
 * tool invocation matches, runs the configured bash command. The command
 * can return JSON with {@code "continue": false, "stopReason": "..."} to
 * block the tool; any other output (or exit code 0) allows it to proceed.
 *
 * <p>Extracted from {@code PluginLoader} so the shell-execution +
 * pattern-matching logic has its own home (and its own tests).
 *
 * @param phase           lifecycle phase the hook attaches to
 * @param matcher         pattern selecting which tools trigger the hook, {@code null} = all
 * @param command         bash command to run; supports {@code ${KROK_PLUGIN_ROOT}},
 *                        {@code $TOOL_NAME}, {@code $TOOL_CALL_ID} substitution
 * @param pluginDir       plugin root — used as working dir + substitution value
 * @param timeoutSeconds  hard deadline; past it the process is force-killed and the hook blocks
 */
public record CommandHook(
    HookPhase phase,
    String matcher,
    String command,
    Path pluginDir,
    int timeoutSeconds
) implements Hook {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public HookResult execute(ToolUseEvent event) {
        if (matcher != null && !matcher.isBlank()
                && !matchesPattern(matcher, event.toolName(), event.args())) {
            return new HookResult.Proceed();
        }
        try {
            String expanded = command
                .replace("${KROK_PLUGIN_ROOT}", pluginDir.toAbsolutePath().toString())
                .replace("$TOOL_NAME", event.toolName())
                .replace("$TOOL_CALL_ID", event.toolCallId() != null ? event.toolCallId() : "");

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", expanded);
            pb.directory(pluginDir.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new HookResult.Block("Hook timed out: " + command);
            }
            String output = new String(process.getInputStream().readAllBytes()).trim();
            if (process.exitValue() != 0) {
                return new HookResult.Block("Hook failed (exit " + process.exitValue() + "): " + output);
            }
            return parseJsonResponse(output);
        } catch (Exception e) {
            return new HookResult.Block("Hook error: " + e.getMessage());
        }
    }

    private static HookResult parseJsonResponse(String output) {
        if (!output.startsWith("{")) return new HookResult.Proceed();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = MAPPER.readValue(output, Map.class);
            Boolean cont = (Boolean) response.getOrDefault("continue", true);
            if (Boolean.FALSE.equals(cont)) {
                String reason = (String) response.getOrDefault("stopReason", "Blocked by hook");
                return new HookResult.Block(reason);
            }
        } catch (Exception ignored) {
            // Non-JSON output is fine; treat as proceed.
        }
        return new HookResult.Proceed();
    }

    /**
     * Matcher syntax: {@code ToolName(arg_glob)}. {@code *} matches any tool
     * or any arg. Arg matching is run against the first string argument
     * (typically {@code "command"} for Bash).
     */
    static boolean matchesPattern(String pattern, String toolName, Map<String, Object> args) {
        int parenIdx = pattern.indexOf('(');
        if (parenIdx < 0) return toolName.equalsIgnoreCase(pattern);
        String patternTool = pattern.substring(0, parenIdx).trim();
        if (!toolName.equalsIgnoreCase(patternTool) && !"*".equals(patternTool)) return false;
        String argPattern = pattern.substring(parenIdx + 1, pattern.length() - 1).trim();
        if ("*".equals(argPattern)) return true;
        String firstArg = args.values().stream()
            .filter(v -> v instanceof String)
            .map(Object::toString)
            .findFirst()
            .orElse("");
        return firstArg.matches(argPattern.replace("*", ".*"));
    }
}
