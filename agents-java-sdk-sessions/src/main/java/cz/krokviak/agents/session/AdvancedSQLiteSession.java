package cz.krokviak.agents.session;

import cz.krokviak.agents.exception.SessionException;
import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.runner.RunItem;

import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Extends SQLiteSession with additional session management capabilities:
 * branching, counting, deleting, listing sessions, and session metadata.
 */
public class AdvancedSQLiteSession extends SQLiteSession {

    private final String jdbcUrl;

    public AdvancedSQLiteSession(Path dbPath) {
        super(dbPath);
        this.jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        initMetadataTable();
    }

    private void initMetadataTable() {
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS session_metadata (
                    session_id TEXT PRIMARY KEY,
                    title TEXT,
                    created_at TEXT NOT NULL,
                    last_activity_at TEXT NOT NULL,
                    message_count INTEGER DEFAULT 0,
                    working_directory TEXT
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_meta_last_activity ON session_metadata(last_activity_at DESC)");
        } catch (SQLException e) {
            throw new SessionException("Failed to initialize session metadata table", e);
        }
    }

    public void saveMetadata(SessionMetadata meta) {
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO session_metadata (session_id, title, created_at, last_activity_at, message_count, working_directory)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(session_id) DO UPDATE SET
                    title = COALESCE(excluded.title, session_metadata.title),
                    last_activity_at = excluded.last_activity_at,
                    message_count = excluded.message_count,
                    working_directory = COALESCE(excluded.working_directory, session_metadata.working_directory)
            """)) {
            stmt.setString(1, meta.sessionId());
            stmt.setString(2, meta.title());
            stmt.setString(3, meta.createdAt().toString());
            stmt.setString(4, meta.lastActivityAt().toString());
            stmt.setInt(5, meta.messageCount());
            stmt.setString(6, meta.workingDirectory());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new SessionException("Failed to save session metadata", e);
        }
    }

    public Optional<SessionMetadata> getMetadata(String sessionId) {
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM session_metadata WHERE session_id = ?")) {
            stmt.setString(1, sessionId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(rowToMetadata(rs));
            }
        } catch (SQLException e) {
            throw new SessionException("Failed to get session metadata", e);
        }
        return Optional.empty();
    }

    public List<SessionMetadata> listSessionsWithMetadata() {
        List<SessionMetadata> result = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT * FROM session_metadata ORDER BY last_activity_at DESC")) {
            while (rs.next()) {
                result.add(rowToMetadata(rs));
            }
        } catch (SQLException e) {
            throw new SessionException("Failed to list sessions with metadata", e);
        }
        return result;
    }

    private SessionMetadata rowToMetadata(ResultSet rs) throws SQLException {
        return new SessionMetadata(
            rs.getString("session_id"),
            rs.getString("title"),
            Instant.parse(rs.getString("created_at")),
            Instant.parse(rs.getString("last_activity_at")),
            rs.getInt("message_count"),
            rs.getString("working_directory")
        );
    }

    /**
     * Branches a session by copying all items from {@code fromSessionId} to {@code toSessionId}.
     */
    public void branch(String fromSessionId, String toSessionId) {
        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            // Read all items from source session
            List<String[]> rows = new ArrayList<>();
            try (PreparedStatement selectStmt = conn.prepareStatement(
                "SELECT item_type, item_json FROM session_items WHERE session_id = ? ORDER BY id")) {
                selectStmt.setString(1, fromSessionId);
                try (ResultSet rs = selectStmt.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new String[]{rs.getString("item_type"), rs.getString("item_json")});
                    }
                }
            }
            // Insert into target session
            try (PreparedStatement insertStmt = conn.prepareStatement(
                "INSERT INTO session_items (session_id, item_type, item_json) VALUES (?, ?, ?)")) {
                for (String[] row : rows) {
                    insertStmt.setString(1, toSessionId);
                    insertStmt.setString(2, row[0]);
                    insertStmt.setString(3, row[1]);
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
            }
        } catch (SQLException e) {
            throw new SessionException("Failed to branch session from " + fromSessionId + " to " + toSessionId, e);
        }
    }

    /**
     * Returns the number of items stored for a given session.
     */
    public int getItemCount(String sessionId) {
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COUNT(*) FROM session_items WHERE session_id = ?")) {
            stmt.setString(1, sessionId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new SessionException("Failed to get item count for session " + sessionId, e);
        }
        return 0;
    }

    /**
     * Deletes all items for the given session.
     */
    public void deleteSession(String sessionId) {
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(
                 "DELETE FROM session_items WHERE session_id = ?")) {
            stmt.setString(1, sessionId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new SessionException("Failed to delete session " + sessionId, e);
        }
    }

    /**
     * Returns a list of all distinct session IDs stored in the database.
     */
    public List<String> listSessions() {
        List<String> sessions = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT DISTINCT session_id FROM session_items ORDER BY session_id")) {
            while (rs.next()) {
                sessions.add(rs.getString("session_id"));
            }
        } catch (SQLException e) {
            throw new SessionException("Failed to list sessions", e);
        }
        return sessions;
    }
}
