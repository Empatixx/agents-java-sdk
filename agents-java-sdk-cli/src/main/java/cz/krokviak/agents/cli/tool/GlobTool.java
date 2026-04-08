package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GlobTool implements ExecutableTool {

    private final Path workingDirectory;
    private final ToolDefinition toolDefinition;

    public GlobTool(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
        this.toolDefinition = new ToolDefinition(
            "glob",
            "Find files matching a glob pattern.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "pattern", Map.of("type", "string", "description", "Glob pattern to match (e.g. **/*.java)"),
                    "path", Map.of("type", "string", "description", "Directory to search in (default: working directory)")
                ),
                "required", List.of("pattern")
            )
        );
    }

    @Override
    public String name() { return "glob"; }

    @Override
    public String description() { return toolDefinition.description(); }

    @Override
    public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) throws Exception {
        String pattern = args.get("pattern", String.class);
        String pathStr = args.get("path", String.class);

        Path searchRoot = pathStr != null ? workingDirectory.resolve(pathStr).normalize() : workingDirectory;
        if (!Files.isDirectory(searchRoot)) {
            return ToolOutput.text("Error: Not a directory: " + searchRoot);
        }

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        List<String> matches = new ArrayList<>();
        int limit = 1000;

        Files.walkFileTree(searchRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (matches.size() >= limit) return FileVisitResult.TERMINATE;
                Path relative = searchRoot.relativize(file);
                if (matcher.matches(relative)) {
                    matches.add(file.toString());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        Collections.sort(matches);
        if (matches.isEmpty()) {
            return ToolOutput.text("No files matched pattern: " + pattern);
        }
        String result = String.join("\n", matches);
        if (matches.size() >= limit) {
            result += "\n(truncated at " + limit + " results)";
        }
        return ToolOutput.text(result);
    }
}
