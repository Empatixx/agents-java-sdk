package cz.krokviak.agents.session;

import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.runner.RunItem;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InMemorySessionTest {

    @Test
    void emptyHistoryByDefault() {
        var session = new InMemorySession();
        assertTrue(session.getHistory("session-1").isEmpty());
    }

    @Test
    void saveAndRetrieveHistory() {
        var session = new InMemorySession();
        var items = List.<RunItem>of(
            new RunItem.MessageOutput("agent", "Hello"),
            new RunItem.MessageOutput("agent", "World")
        );
        session.save("session-1", items);

        var history = session.getHistory("session-1");
        assertEquals(2, history.size());
    }

    @Test
    void historyAccumulatesAcrossSaves() {
        var session = new InMemorySession();
        session.save("s1", List.of(new RunItem.MessageOutput("a", "first")));
        session.save("s1", List.of(new RunItem.MessageOutput("a", "second")));

        var history = session.getHistory("s1");
        assertEquals(2, history.size());
    }

    @Test
    void separateSessionsAreIsolated() {
        var session = new InMemorySession();
        session.save("s1", List.of(new RunItem.MessageOutput("a", "s1-msg")));
        session.save("s2", List.of(new RunItem.MessageOutput("a", "s2-msg")));

        assertEquals(1, session.getHistory("s1").size());
        assertEquals(1, session.getHistory("s2").size());
    }
}
