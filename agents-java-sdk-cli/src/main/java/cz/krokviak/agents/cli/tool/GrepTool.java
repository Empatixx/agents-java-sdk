package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class GrepTool implements ExecutableTool {

    private final Path workingDirectory;
    private final ToolDefinition toolDefinition;

    public GrepTool(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
        this.toolDefinition = new ToolDefinition(
            "grep",
            "Search file contents using a regex pattern.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "pattern", Map.of("type", "string", "description", "Regex pattern to search for"),
                    "path", Map.of("type", "string", "description", "Directory or file to search in (default: working directory)"),
                    "include", Map.of("type", "string", "description", "Glob pattern to filter files (e.g. *.java)")
                ),
                "required", List.of("pattern")
            )
        );
    }

    @Override
    public String name() { return "grep"; }

    @Override
    public String description() { return toolDefinition.description(); }

    @Override
    public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) throws Exception {
        String patternStr = args.get("pattern", String.class);
        String pathStr = args.get("path", String.class);
        String include = args.get("include", String.class);

        Pattern regex;
        try {
            regex = Pattern.compile(patternStr);
        } catch (Exception e) {
            return ToolOutput.text("Error: Invalid regex: " + e.getMessage());
        }

        Path searchRoot = pathStr != null ? workingDirectory.resolve(pathStr).normalize() : workingDirectory;
        PathMatcher includeFilter = include != null
            ? FileSystems.getDefault().getPathMatcher("glob:" + include)
            : null;

        List<String> matches = new ArrayList<>();
        int limit = cz.krokviak.agents.cli.CliDefaults.GREP_RESULT_LIMIT;

        if (Files.isRegularFile(searchRoot)) {
            searchFile(searchRoot, regex, matches, limit);
        } else {
            Files.walkFileTree(searchRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (matches.size() >= limit) return FileVisitResult.TERMINATE;
                    if (includeFilter != null && !includeFilter.matches(file.getFileName())) {
                        return FileVisitResult.CONTINUE;
                    }
                    // Skip binary files (heuristic: skip if not readable as text)
                    try {
                        String contentType = Files.probeContentType(file);
                        if (contentType != null && !contentType.startsWith("text") && !contentType.contains("json") && !contentType.contains("xml")) {
                            return FileVisitResult.CONTINUE;
                        }
                    } catch (IOException ignored) {}

                    searchFile(file, regex, matches, limit);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        if (matches.isEmpty()) {
            return ToolOutput.text("No matches found for pattern: " + patternStr);
        }
        String result = String.join("\n", matches);
        if (matches.size() >= limit) {
            result += "\n(truncated at " + limit + " matches)";
        }
        return ToolOutput.text(result);
    }

    private static void searchFile(Path file, Pattern regex, List<String> matches, int limit) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size() && matches.size() < limit; i++) {
                if (regex.matcher(lines.get(i)).find()) {
                    matches.add(file + ":" + (i + 1) + ":" + lines.get(i));
                }
            }
        } catch (Exception ignored) {
            // Skip files that can't be read as text
        }
    }
}
