package cz.krokviak.agents.session;

import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.runner.RunItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EncryptedSessionTest {

    @Test
    void encryptThenDecryptRoundtrip() {
        InMemorySession backing = new InMemorySession();
        EncryptedSession session = EncryptedSession.withGeneratedKey(backing);

        session.save("s1", List.of(new RunItem.MessageOutput("agent", "Hello secret world")));

        List<InputItem> history = session.getHistory("s1");
        assertEquals(1, history.size());
        assertTrue(history.get(0) instanceof InputItem.AssistantMessage msg
            && msg.content().contains("Hello secret world"));
    }

    @Test
    void encryptedDataDiffersFromOriginal() {
        InMemorySession backing = new InMemorySession();
        EncryptedSession session = EncryptedSession.withGeneratedKey(backing);
        String originalContent = "sensitive data";

        session.save("s1", List.of(new RunItem.MessageOutput("agent", originalContent)));

        // Inspect what the backing store actually contains — it should NOT be the original text
        List<InputItem> rawHistory = backing.getHistory("s1");
        assertEquals(1, rawHistory.size());
        String storedContent = ((InputItem.AssistantMessage) rawHistory.get(0)).content();
        assertNotEquals(originalContent, storedContent,
            "Backing store should contain encrypted (not plaintext) data");
    }

    @Test
    void withGeneratedKeyFactory() {
        InMemorySession backing = new InMemorySession();
        EncryptedSession session = EncryptedSession.withGeneratedKey(backing);

        assertNotNull(session);
        assertNotNull(session.getKey());
        assertEquals("AES", session.getKey().getAlgorithm());
    }

    @Test
    void keyExposedAsBase64() {
        InMemorySession backing = new InMemorySession();
        EncryptedSession session = EncryptedSession.withGeneratedKey(backing);

        String base64Key = session.getKeyAsBase64();
        assertNotNull(base64Key);
        assertFalse(base64Key.isBlank());
    }

    @Test
    void twoSessionsWithSameKeyProduceSameDecryptedOutput() {
        InMemorySession backing = new InMemorySession();
        EncryptedSession session1 = EncryptedSession.withGeneratedKey(backing);
        // Share the key with a second wrapper over the same backing store
        EncryptedSession session2 = new EncryptedSession(backing, session1.getKey());

        session1.save("s1", List.of(new RunItem.MessageOutput("agent", "shared secret")));

        List<InputItem> history = session2.getHistory("s1");
        assertEquals(1, history.size());
        assertTrue(history.get(0) instanceof InputItem.AssistantMessage msg
            && msg.content().contains("shared secret"));
    }

    @Test
    void base64KeyConstructorWorks() {
        InMemorySession backing = new InMemorySession();
        EncryptedSession original = EncryptedSession.withGeneratedKey(backing);
        String base64Key = original.getKeyAsBase64();

        original.save("s1", List.of(new RunItem.MessageOutput("agent", "base64 key test")));

        // Reconstruct using base64 key
        EncryptedSession restored = new EncryptedSession(backing, base64Key);
        List<InputItem> history = restored.getHistory("s1");
        assertEquals(1, history.size());
        assertTrue(history.get(0) instanceof InputItem.AssistantMessage msg
            && msg.content().contains("base64 key test"));
    }
}
