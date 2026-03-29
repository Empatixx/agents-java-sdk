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

    private InputItem deserializeToInput(String type, String json) {
        try {
            return switch (type) {
                case "MessageOutput" -> {
                    var msg = objectMapper.readTree(json);
                    yield new InputItem.AssistantMessage(msg.get("content").asText());
                }
                default -> new InputItem.AssistantMessage("[" + type + "]");
            };
        } catch (JsonProcessingException e) {
            throw new SessionException("Failed to deserialize session item", e);
        }
    }
}
