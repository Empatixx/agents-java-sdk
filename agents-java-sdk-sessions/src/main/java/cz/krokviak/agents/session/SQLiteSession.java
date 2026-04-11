package cz.krokviak.agents.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.krokviak.agents.exception.SessionException;
import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.runner.RunItem;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SQLiteSession implements Session {
    private final String jdbcUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SQLiteSession(Path dbPath) {
        this.jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        initTable();
    }

    private void initTable() {
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS session_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id TEXT NOT NULL,
                    item_type TEXT NOT NULL,
                    item_json TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_session_id ON session_items(session_id)");
        } catch (SQLException e) {
            throw new SessionException("Failed to initialize SQLite session", e);
        }
    }

    @Override
    public List<InputItem> getHistory(String sessionId) {
        List<InputItem> items = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT item_type, item_json FROM session_items WHERE session_id = ? ORDER BY id")) {
            stmt.setString(1, sessionId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString("item_type");
                    String json = rs.getString("item_json");
                    items.add(deserializeToInput(type, json));
                }
            }
        } catch (SQLException e) {
            throw new SessionException("Failed to get history", e);
        }
        return items;
    }

    @Override
    public void save(String sessionId, List<RunItem> newItems) {
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO session_items (session_id, item_type, item_json) VALUES (?, ?, ?)")) {
            for (RunItem item : newItems) {
                stmt.setString(1, sessionId);
                stmt.setString(2, item.getClass().getSimpleName());
                stmt.setString(3, objectMapper.writeValueAsString(item));
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException | JsonProcessingException e) {
            throw new SessionException("Failed to save session items", e);
        }
    }

    @SuppressWarnings("unchecked")
    private InputItem deserializeToInput(String type, String json) {
        try {
            var map = objectMapper.readValue(json, java.util.Map.class);
            return switch (type) {
                case "UserInput" -> new InputItem.UserMessage((String) map.get("content"));
                case "MessageOutput" -> {
                    String content = (String) map.get("content");
                    yield new InputItem.AssistantMessage(content != null ? content : "");
                }
                case "ToolCallItem" -> {
                    String toolCallId = (String) map.get("toolCallId");
                    String toolName = (String) map.get("toolName");
                    var arguments = map.get("arguments");
                    java.util.Map<String, Object> args = arguments instanceof java.util.Map
                        ? (java.util.Map<String, Object>) arguments : java.util.Map.of();
                    yield new InputItem.AssistantMessage("",
                        java.util.List.of(new InputItem.ToolCall(toolCallId, toolName, args)));
                }
                case "ToolOutputItem" -> {
                    String toolCallId = (String) map.get("toolCallId");
                    String toolName = (String) map.get("toolName");
                    var outputObj = map.get("output");
                    String output = "";
                    if (outputObj instanceof java.util.Map outputMap) {
                        var text = outputMap.get("text");
                        if (text != null) output = text.toString();
                    } else if (outputObj instanceof String s) {
                        output = s;
                    }
                    yield new InputItem.ToolResult(toolCallId, toolName, output);
                }
                case "HandoffItem" -> new InputItem.SystemMessage(
                    "[Handoff: " + map.get("fromAgent") + " → " + map.get("toAgent") + "]");
                case "CompactionMarker" -> {
                    String summary = (String) map.get("summary");
                    var compactedAt = map.get("compactedAt");
                    int messagesCompacted = map.get("messagesCompacted") instanceof Number n ? n.intValue() : 0;
                    java.time.Instant instant = compactedAt instanceof String s
                        ? java.time.Instant.parse(s) : java.time.Instant.now();
                    yield new InputItem.CompactionMarker(summary != null ? summary : "", instant, messagesCompacted);
                }
                default -> new InputItem.SystemMessage("[" + type + "]");
            };
        } catch (JsonProcessingException e) {
            throw new SessionException("Failed to deserialize session item", e);
        }
    }
}
