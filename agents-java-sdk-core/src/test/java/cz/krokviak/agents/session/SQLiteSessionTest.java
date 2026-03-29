package cz.krokviak.agents.session;

import cz.krokviak.agents.runner.RunItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SQLiteSessionTest {

    @Test
    void saveAndRetrieveFromSqlite(@TempDir Path tempDir) {
        var dbPath = tempDir.resolve("test.db");
        var session = new SQLiteSession(dbPath);
        session.save("s1", List.of(new RunItem.MessageOutput("agent", "Hello from SQLite")));

        var history = session.getHistory("s1");
        assertEquals(1, history.size());
    }

    @Test
    void persistsAcrossInstances(@TempDir Path tempDir) {
        var dbPath = tempDir.resolve("persist.db");

        var session1 = new SQLiteSession(dbPath);
        session1.save("s1", List.of(new RunItem.MessageOutput("agent", "msg1")));

        var session2 = new SQLiteSession(dbPath);
        var history = session2.getHistory("s1");
        assertEquals(1, history.size());
    }
}
