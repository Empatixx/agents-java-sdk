package cz.krokviak.agents.cli.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.ExecutableTool;
import cz.krokviak.agents.tool.ToolArgs;
import cz.krokviak.agents.tool.ToolDefinition;
import cz.krokviak.agents.tool.ToolOutput;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class NotebookEditTool implements ExecutableTool {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Path workingDirectory;
    private final ToolDefinition toolDefinition;

    public NotebookEditTool(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
        this.toolDefinition = new ToolDefinition("notebook_edit",
            "Edit a Jupyter notebook by replacing, inserting, appending, or deleting cells.",
            Map.of("type", "object", "properties", Map.of(
                "file_path", Map.of("type", "string", "description", "Notebook path"),
                "action", Map.of("type", "string", "description", "replace_cell, insert_cell, append_cell, or delete_cell"),
                "cell_index", Map.of("type", "integer", "description", "Cell index for replace, insert, or delete"),
                "cell_type", Map.of("type", "string", "description", "code or markdown (default code)"),
                "source", Map.of("type", "string", "description", "Cell source text for replace, insert, or append")
            ), "required", List.of("file_path", "action")));
    }

    @Override public String name() { return "notebook_edit"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) throws Exception {
        String filePath = args.get("file_path", String.class);
        String action = args.get("action", String.class);
        String source = args.get("source", String.class);
        String cellType = args.getOrDefault("cell_type", String.class, "code");
        Integer cellIndex = getIntOrNull(args.raw().get("cell_index"));

        if (filePath == null || filePath.isBlank()) return ToolOutput.text("Error: file_path required");
        if (action == null || action.isBlank()) return ToolOutput.text("Error: action required");

        Path resolved = workingDirectory.resolve(filePath).normalize();
        if (!Files.exists(resolved)) return ToolOutput.text("Error: notebook not found: " + resolved);

        ObjectNode root = (ObjectNode) OBJECT_MAPPER.readTree(Files.readString(resolved));
        ArrayNode cells = ensureCells(root);

        switch (action) {
            case "replace_cell" -> {
                if (cellIndex == null) return ToolOutput.text("Error: cell_index required for replace_cell");
                if (source == null) return ToolOutput.text("Error: source required for replace_cell");
                ObjectNode existing = requireCell(cells, cellIndex);
                overwriteCell(existing, cellType, source);
            }
            case "insert_cell" -> {
                if (cellIndex == null) return ToolOutput.text("Error: cell_index required for insert_cell");
                if (source == null) return ToolOutput.text("Error: source required for insert_cell");
                if (cellIndex < 0 || cellIndex > cells.size()) return ToolOutput.text("Error: cell_index out of range: " + cellIndex);
                cells.insert(cellIndex, newCell(cellType, source));
            }
            case "append_cell" -> {
                if (source == null) return ToolOutput.text("Error: source required for append_cell");
                cells.add(newCell(cellType, source));
            }
            case "delete_cell" -> {
                if (cellIndex == null) return ToolOutput.text("Error: cell_index required for delete_cell");
                requireCell(cells, cellIndex);
                cells.remove(cellIndex);
            }
            default -> {
                return ToolOutput.text("Error: unsupported action: " + action);
            }
        }

        Files.writeString(resolved, OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root) + System.lineSeparator());
        return ToolOutput.text("Notebook updated: " + resolved + " (" + action + ")");
    }

    private static ArrayNode ensureCells(ObjectNode root) {
        JsonNode existing = root.get("cells");
        if (existing instanceof ArrayNode cells) return cells;
        ArrayNode cells = OBJECT_MAPPER.createArrayNode();
        root.set("cells", cells);
        return cells;
    }

    private static ObjectNode requireCell(ArrayNode cells, int index) {
        if (index < 0 || index >= cells.size()) {
            throw new IllegalArgumentException("Cell index out of range: " + index);
        }
        return (ObjectNode) cells.get(index);
    }

    private static void overwriteCell(ObjectNode cell, String cellType, String source) {
        cell.put("cell_type", normalizeCellType(cellType));
        cell.set("source", sourceLines(source));
        cell.putObject("metadata");
        if ("code".equals(normalizeCellType(cellType))) {
            cell.putNull("execution_count");
            cell.set("outputs", OBJECT_MAPPER.createArrayNode());
        } else {
            cell.remove("execution_count");
            cell.remove("outputs");
        }
    }

    private static ObjectNode newCell(String cellType, String source) {
        ObjectNode cell = OBJECT_MAPPER.createObjectNode();
        overwriteCell(cell, cellType, source);
        return cell;
    }

    private static ArrayNode sourceLines(String source) {
        ArrayNode lines = OBJECT_MAPPER.createArrayNode();
        String normalized = source.replace("\r\n", "\n");
        String[] split = normalized.split("\n", -1);
        for (int i = 0; i < split.length; i++) {
            String suffix = i == split.length - 1 ? "" : "\n";
            lines.add(split[i] + suffix);
        }
        return lines;
    }

    private static String normalizeCellType(String cellType) {
        return "markdown".equalsIgnoreCase(cellType) ? "markdown" : "code";
    }

    private static Integer getIntOrNull(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(value.toString());
    }
}
