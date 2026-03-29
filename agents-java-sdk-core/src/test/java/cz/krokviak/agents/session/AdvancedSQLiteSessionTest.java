package cz.krokviak.agents.session;

import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.runner.RunItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdvancedSQLiteSessionTest {

    @Test
    void branchCopiesHistory(@TempDir Path tempDir) {
        AdvancedSQLiteSession session = new AdvancedSQLiteSession(tempDir.resolve("test.db"));
        session.save("source", List.of(
            new RunItem.MessageOutput("agent", "msg1"),
            new RunItem.MessageOutput("agent", "msg2")
        ));

        session.branch("source", "target");

        List<InputItem> targetHistory = session.getHistory("target");
        assertEquals(2, targetHistory.size());
    }

    @Test
    void branchPreservesSourceIntact(@TempDir Path tempDir) {
        AdvancedSQLiteSession session = new AdvancedSQLiteSession(tempDir.resolve("test.db"));
        session.save("source", List.of(new RunItem.MessageOutput("agent", "original")));

        session.branch("source", "fork");

        // Source still intact
        List<InputItem> sourceHistory = session.getHistory("source");
        assertEquals(1, sourceHistory.size());

        // Fork has copy
        List<InputItem> forkHistory = session.getHistory("fork");
        assertEquals(1, forkHistory.size());
    }

    @Test
    void getItemCount(@TempDir Path tempDir) {
        AdvancedSQLiteSession session = new AdvancedSQLiteSession(tempDir.resolve("test.db"));
        assertEquals(0, session.getItemCount("empty"));

        session.save("s1", List.of(
            new RunItem.MessageOutput("agent", "a"),
            new RunItem.MessageOutput("agent", "b"),
            new RunItem.MessageOutput("agent", "c")
        ));
        assertEquals(3, session.getItemCount("s1"));
    }

    @Test
    void deleteSession(@TempDir Path tempDir) {
        AdvancedSQLiteSession session = new AdvancedSQLiteSession(tempDir.resolve("test.db"));
        session.save("s1", List.of(new RunItem.MessageOutput("agent", "hello")));
        assertEquals(1, session.getItemCount("s1"));

        session.deleteSession("s1");
        assertEquals(0, session.getItemCount("s1"));
        assertTrue(session.getHistory("s1").isEmpty());
    }

    @Test
    void listSessions(@TempDir Path tempDir) {
        AdvancedSQLiteSession session = new AdvancedSQLiteSession(tempDir.resolve("test.db"));
        assertTrue(session.listSessions().isEmpty());

        session.save("alpha", List.of(new RunItem.MessageOutput("agent", "a")));
        session.save("beta", List.of(new RunItem.MessageOutput("agent", "b")));
        session.save("gamma", List.of(new RunItem.MessageOutput("agent", "c")));

        List<String> sessions = session.listSessions();
        assertEquals(3, sessions.size());
        assertTrue(sessions.contains("alpha"));
        assertTrue(sessions.contains("beta"));
        assertTrue(sessions.contains("gamma"));
    }

    @Test
    void listSessionsExcludesDeletedSessions(@TempDir Path tempDir) {
        AdvancedSQLiteSession session = new AdvancedSQLiteSession(tempDir.resolve("test.db"));

        session.save("keep", List.of(new RunItem.MessageOutput("agent", "keep this")));
        session.save("remove", List.of(new RunItem.MessageOutput("agent", "remove this")));
        session.deleteSession("remove");

        List<String> sessions = session.listSessions();
        assertEquals(1, sessions.size());
        assertEquals("keep", sessions.get(0));
    }
}
